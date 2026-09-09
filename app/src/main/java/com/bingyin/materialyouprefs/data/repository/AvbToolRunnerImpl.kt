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
                parseResult(raw)
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
        pyModule = py.getModule("python_main")
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

    companion object {
        private const val TAG = "AvbToolRunnerImpl"

        const val AOSP_HEAD = "386fb90492db3bd6bc484a579bcde5b43a2a0292"

        /**
         * Whether the FEC native library (libavbfec.so) is bundled.
         * Kept false until M3.5 lands.
         */
        const val FEC_LOADED: Boolean = false
    }
}
