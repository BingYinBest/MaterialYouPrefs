package com.bingyin.materialyouprefs.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bingyin.materialyouprefs.data.model.AvbExecutionRequest
import com.bingyin.materialyouprefs.data.model.AvbExecutionResult
import com.bingyin.materialyouprefs.data.model.CommandParam
import com.bingyin.materialyouprefs.data.model.ErrorCode
import com.bingyin.materialyouprefs.data.parser.AvbHelpParser
import com.chaquo.python.PyException
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chaquopy-backed [AvbToolRunner]. Real implementation for M3.
 *
 * Wire-up:
 *   1. Constructor receives a [Context] to bootstrap the Python runtime.
 *   2. First call to any method triggers Python startup on an Executor thread
 *      (Chaquopy forbids starting Python on the main thread).
 *   3. `run(request)` serializes [AvbExecutionRequest] to JSON, invokes
 *      `python_main.run(json)` on the executor, parses the JSON result back
 *      into [AvbExecutionResult].
 *
 * Error mapping:
 *   - `PyException` -> PYTHON_EXCEPTION
 *   - Any other Throwable -> UNKNOWN
 *   - Result JSON with kind=Failure -> mapped by the enum string
 *
 * Milestone scope:
 *   - M3.1: only `version` is dispatched on the Python side.
 *   - M3.2: vendored the real avbtool.py; `run()` handles all subcommands.
 *   - M3.4: `fetchHelp()` is now real -- it shells out to
 *     `python_main.run` with the `__help__` virtual command and pipes the
 *     argparse help output through [AvbHelpParser].
 *   - M3.5.1: FEC now ships via pure-Python RS encoder (`avb_fec.py`);
 *     `FEC_LOADED` is set to `true` so the UI can surface the flag.
 *   - M3.5.2a: `ensureInitialized()` calls `python_main.init_runtime(cacheDir)`
 *     right after importing the Python module, so `tempfile.NamedTemporaryFile()`
 *     inside `avbtool.py`'s `sign()` writes under the app's private cache
 *     instead of the system `/tmp`.
 *   - M3.5.2c: mmap-backed large-file I/O is on the Python side (`avb_io.py`);
 *     nothing to wire here — `avb_fec.encode_fec` picks it up automatically.
 *   - M3.5.2b (SAF fd bridge): deferred to M4.2c. M4.2b uses the simpler
 *     stage+promote path: python reports generated files, we copy the
 *     freshest one into the user's output Uri after execution.
 *
 * Chaquopy 15 API notes:
 *   - There is no `PyModule` class. Use `PyObject` from `py.getModule(name)`.
 *   - There is no `Python.useInstance` / `py.importModule`.
 *   - `PyObject.toString()` returns the Python `str()` of the underlying value.
 *   - `PyException` has no `.value` field — use `.message` (which includes trace).
 */
@Singleton
class AvbToolRunnerImpl @Inject constructor(
    context: Context,
) : AvbToolRunner {

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor {
        r -> Thread(r, "avbtool-python").apply { isDaemon = true }
    }
    @Volatile private var pyModule: PyObject? = null
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun run(request: AvbExecutionRequest): AvbExecutionResult =
        withContext(Dispatchers.IO) {
            try {
                ensureInitialized()
                val argsJson = encodeArgsJson(request)
                val raw = callPythonRun(argsJson)
                var result = parseResult(raw)
                // M4.2b: when the caller picked an output Uri and Python
                // reported generated files in its tmpdir, promote the
                // freshest one into the SAF Uri. Done here (suspend-safe)
                // so the UI only sees the final URI-resolved result.
                if (result is AvbExecutionResult.Success &&
                    result.exitCode == 0 &&
                    request.outputUri != null
                ) {
                    val generated = parseGeneratedFiles(raw)
                    if (generated.isNotEmpty()) {
                        val candidate = generated.last()
                        runCatching {
                            if (promoteToOutput(candidate, request.outputUri!!)) {
                                result = result.copy(outputUri = request.outputUri)
                            }
                        }.onFailure { t ->
                            Log.w(TAG, "promoteToOutput after run failed", t)
                        }
                    }
                }
                result
            } catch (e: PyException) {
                AvbExecutionResult.Failure(
                    errorCode = ErrorCode.PYTHON_EXCEPTION,
                    message = "Python error: ${e.message}",
                    cause = e,
                )
            } catch (t: Throwable) {
                AvbExecutionResult.Failure(
                    errorCode = ErrorCode.UNKNOWN,
                    message = "${t::class.java.simpleName}: ${t.message}",
                    cause = t,
                )
            }
        }

    /**
     * Fetch argparse help text via the `__help__` virtual command, then run
     * it through [AvbHelpParser]. Returns an empty list on any failure so
     * callers (e.g. [CommandRepository.getByIdOrFetch]) can fall back to
     * whatever they already have cached.
     */
    override suspend fun fetchHelp(commandName: String): List<CommandParam> =
        withContext(Dispatchers.IO) {
            if (commandName.isEmpty()) return@withContext emptyList()
            try {
                ensureInitialized()
                val argsJson = buildJsonObject {
                    put("commandName", JsonPrimitive("__help__"))
                    put("args", buildJsonArray { add(JsonPrimitive(commandName)) })
                }.toString()
                val raw = callPythonRun(argsJson)
                val root = json.parseToJsonElement(raw).jsonObject
                val kind = root["kind"]?.jsonPrimitive?.content ?: "Failure"
                if (kind != "Success") {
                    Log.w(TAG, "fetchHelp '$commandName' returned Failure: " +
                        (root["message"]?.jsonPrimitive?.content ?: "(no msg)"))
                    return@withContext emptyList()
                }
                val helpText = root["stdout"]?.jsonPrimitive?.content ?: ""
                val params = runCatching { AvbHelpParser.parse(helpText) }
                    .getOrDefault(emptyList())
                Log.v(TAG, "fetchHelp '$commandName': ${params.size} params")
                params
            } catch (e: PyException) {
                Log.w(TAG, "fetchHelp '$commandName' PyException: ${e.message}")
                emptyList()
            } catch (t: Throwable) {
                Log.w(TAG, "fetchHelp '$commandName' failed", t)
                emptyList()
            }
        }

    override fun aospHead(): String = AOSP_HEAD

    override fun isFecLoaded(): Boolean = FEC_LOADED

    override suspend fun stageInput(uri: Uri): String = withContext(Dispatchers.IO) {
        val ext = uri.lastPathSegment?.substringAfterLast('.', "bin") ?: "bin"
        val target = File(appContext.cacheDir, "avbtool-in-${System.nanoTime()}.$ext")
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw java.io.IOException("Cannot open input URI")
            target.absolutePath
        } catch (t: Throwable) {
            target.delete()
            throw java.io.IOException("stageInput failed: ${t.message}", t)
        }
    }

    override suspend fun promoteToOutput(localPath: String, outputUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val src = File(localPath)
            if (!src.exists()) return@withContext false
            try {
                appContext.contentResolver.openOutputStream(outputUri, "wt")?.use { output ->
                    src.inputStream().use { input -> input.copyTo(output) }
                } ?: return@withContext false
                true
            } catch (t: Throwable) {
                Log.w(TAG, "promoteToOutput failed", t)
                false
            }
        }

    override fun cleanupTemp() {
        executor.execute {
            val dir = File(appContext.cacheDir, "avbtool-tmp")
            if (dir.exists()) dir.deleteRecursively()
        }
    }

    // ---------- internal helpers ----------

    /**
     * Must be called on the executor thread. Python.start() (implicitly via
     * `Python.getInstance()`) cannot be called from the main thread.
     */
    private fun ensureInitialized() {
        if (pyModule != null) return
        val py = Python.getInstance()
        val mod = py.getModule("python_main")
        pyModule = mod
        // M3.5.2a: relocate tempfile's TMPDIR to the app's cache dir before
        // any avbtool command runs. `avbtool.py` calls `tempfile.NamedTemporaryFile()`
        // once inside `sign()`; on Android the system `/tmp` is not always
        // writable (target SDK 24+ apps with scoped storage). Pointing TMPDIR
        // at cacheDir/avbtool-tmp/ makes the tempfile land on private storage
        // where we already have write permission.
        runCatching {
            val cachePath = appContext.cacheDir.absolutePath
            mod.call("init_runtime", cachePath)
        }.onFailure { t ->
            Log.w(TAG, "init_runtime() failed (non-fatal): ${t.message}")
        }
    }

    private fun encodeArgsJson(request: AvbExecutionRequest): String {
        val commandName = request.commandId
            .removePrefix(CommandRepository.ID_PREFIX)
        val argsJson = buildJsonArray {
            request.args.forEach { add(JsonPrimitive(it)) }
        }
        val root = buildJsonObject {
            put("commandName", JsonPrimitive(commandName))
            put("args", argsJson)
        }
        return root.toString()
    }

    private fun callPythonRun(argsJson: String): String {
        val module = pyModule ?: throw IllegalStateException("Python not initialized")
        val result = module.call("run", argsJson)
        return result.toString()
    }

    private fun parseResult(raw: String): AvbExecutionResult {
        val root = json.parseToJsonElement(raw).jsonObject
        val kind = root["kind"]?.jsonPrimitive?.content ?: "Failure"
        return when (kind) {
            "Success" -> AvbExecutionResult.Success(
                exitCode = root["exitCode"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
                stdout = root["stdout"]?.jsonPrimitive?.content ?: "",
                stderr = root["stderr"]?.jsonPrimitive?.content ?: "",
                outputUri = null,
                durationMs = root["durationMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
            "Failure" -> {
                val codeStr = root["errorCode"]?.jsonPrimitive?.content ?: "UNKNOWN"
                val code = try {
                    ErrorCode.valueOf(codeStr)
                } catch (e: IllegalArgumentException) {
                    ErrorCode.UNKNOWN
                }
                AvbExecutionResult.Failure(
                    errorCode = code,
                    message = root["message"]?.jsonPrimitive?.content ?: "(no message)",
                )
            }
            else -> AvbExecutionResult.Failure(
                errorCode = ErrorCode.UNKNOWN,
                message = "Unexpected result kind: $kind",
            )
        }
    }

    /**
     * M4.2b: read the ordered `generatedFiles` list Python returned
     * (files that landed inside the run's tmpdir). Empty if the command
     * didn't write anything.
     */
    private fun parseGeneratedFiles(raw: String): List<String> {
        val root = json.parseToJsonElement(raw).jsonObject
        return root["generatedFiles"]?.jsonArray?.mapNotNull {
            it.jsonPrimitive.content
        } ?: emptyList()
    }

    companion object {
        private const val TAG = "AvbToolRunnerImpl"

        const val AOSP_HEAD = "386fb90492db3bd6bc484a579bcde5b43a2a0292"

        /**
         * Whether FEC support is available. Since M3.5.1 it ships as a
         * pure-Python RS(255, 253) encoder in `avb_fec.py` (no native
         * `.so` needed). Set true so the UI can surface the flag.
         */
        const val FEC_LOADED: Boolean = true
    }
}
