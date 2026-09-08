package com.bingyin.materialyouprefs.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bingyin.materialyouprefs.data.model.AvbExecutionRequest
import com.bingyin.materialyouprefs.data.model.AvbExecutionResult
import com.bingyin.materialyouprefs.data.model.CommandParam
import com.bingyin.materialyouprefs.data.model.ErrorCode
import com.chaquo.python.PyException
import com.chaquo.python.PyModule
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
 *   2. First call to any method triggers Python.init on an Executor thread
 *      (Chaquopy forbids calling Python.init on the main thread).
 *   3. `run(request)` serializes [AvbExecutionRequest] to JSON, invokes
 *      `python_main.run(json)` on the executor, parses the JSON result back
 *      into [AvbExecutionResult].
 *
 * Error mapping:
 *   - `PyException` -> PYTHON_EXCEPTION
 *   - Any other Throwable -> UNKNOWN
 *   - Result JSON with kind=Failure -> mapped by the enum string
 *
 * M3.1 scope: only `version` is dispatched on the Python side. All other
 * commands return an UNKNOWN_PARAM failure so the UI has a clean path.
 * M3.2 vendors the real avbtool.py.
 */
@Singleton
class AvbToolRunnerImpl @Inject constructor(
    context: Context,
) : AvbToolRunner {

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor {
        r -> Thread(r, "avbtool-python").apply { isDaemon = true }
    }
    private var pyModule: PyModule? = null
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
                    message = "Python error: ${e.value}",
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

    override suspend fun fetchHelp(commandName: String): List<CommandParam> {
        // M3.2+ will shell out to `avbtool.py <name> --help` and parse.
        // M3.1 returns empty list so callers fall back to seed data.
        return emptyList()
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

    private fun ensureInitialized() {
        if (pyModule != null) return
        val py = Python.getInstance()
        Python.useInstance(py)
        py.getPlatform().useAsCurrent()
        pyModule = py.importModule("python_main")
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
        return result.asString()
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
