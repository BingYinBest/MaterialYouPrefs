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
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chaquopy-backed [AvbToolRunner]. Real implementation for M3.
 *
 * Wire-up:
 *   1. Constructor receives a [Context] to bootstrap the Python runtime.
 *   2. First call to any method triggers [Python.init] on an Executor thread
 *      (Chaquopy forbids calling `Python.init` on the main thread).
 *   3. `run(request)` serializes [AvbExecutionRequest] to JSON, invokes
 *      `python_main.run(json)` on the executor, parses the JSON result back
 *      into [AvbExecutionResult].
 *
 * Error mapping:
 *   - `PyException` (Python-side unhandled exception) -> PYTHON_EXCEPTION
 *   - Any other Throwable -> UNKNOWN
 *   - Result JSON with kind=Failure -> mapped by the enum string
 *
 * M3.1 scope: only `version` is dispatched on the Python side. All other
 * commands return an UNKNOWN_PARAM failure so the UI has a clean path
 * (e.g. "runtime not yet wired"). M3.2 vendors the real avbtool.py.
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
    private val python: com.chaquo.python.Python by lazy {
        com.chaquo.python.Python.getInstance().also { Python.useInstance(it) }
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun run(request: AvbExecutionRequest): AvbExecutionResult =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                executor.execute {
                    try {
                        ensureInitialized()
                        val argsJson = json.encodeToString(
                            JsonObject(
                                mapOf(
                                    "commandName" to com.bingyin.materialyouprefs.data.repository
                                        .CommandRepository.ID_PREFIX.let { request.commandId.removePrefix(it) },
                                    "args" to com.bingyin.materialyouprefs.data.repository.JsonArrayWrapper.wrap(request.args),
                                ),
                            ),
                        )
                        cont.resume(runBlocking { parseResult(argsJson) }, null)
                    } catch (e: PyException) {
                        cont.resume(
                            AvbExecutionResult.Failure(
                                errorCode = ErrorCode.PYTHON_EXCEPTION,
                                message = "Python error: ${e.value}",
                                cause = e,
                            ),
                            null,
                        )
                    } catch (t: Throwable) {
                        cont.resume(
                            AvbExecutionResult.Failure(
                                errorCode = ErrorCode.UNKNOWN,
                                message = "${t::class.java.simpleName}: ${t.message}",
                                cause = t,
                            ),
                            null,
                        )
                    }
                }
            }
        }

    override suspend fun fetchHelp(commandName: String): List<CommandParam> =
        withContext(Dispatchers.IO) {
            // M3.2+ will shell out to `avbtool.py <name> --help` and parse.
            // M3.1 returns empty list so callers fall back to seed data.
            emptyList()
        }

    override fun aospHead(): String = AOSP_HEAD

    override fun isFecLoaded(): Boolean = FEC_LOADED

    override suspend fun stageInput(uri: Uri): String {
        // M3.5: real SAF bridging arrives later. For now, only handle plain
        // content/file URIs by copying into app-private cache.
        withContext(Dispatchers.IO) {
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

    private fun ensureInitialized() {
        if (pyModule == null) {
            python.getPlatform().useAsCurrent()
            pyModule = python.importModule("python_main")
        }
    }

    /**
     * Execute `python_main.run(argsJson)` on the current thread and decode
     * the JSON result back into an [AvbExecutionResult].
     */
    private fun parseResult(argsJson: String): AvbExecutionResult {
        val module = pyModule ?: throw IllegalStateException("Python not initialized")
        val raw: String = module.call("run", argsJson).asString()
        val root = json.parseToJsonElement(raw).jsonObject
        val kind = root["kind"]?.jsonPrimitive?.content ?: "Failure"
        val durationMs = root["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L

        return when (kind) {
            "Success" -> AvbExecutionResult.Success(
                exitCode = root["exitCode"]?.jsonPrimitive?.intOrNull ?: -1,
                stdout = root["stdout"]?.jsonPrimitive?.content ?: "",
                stderr = root["stderr"]?.jsonPrimitive?.content ?: "",
                outputUri = null,
                durationMs = durationMs,
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

    private fun runBlocking(
        block: suspend () -> AvbExecutionResult,
    ): AvbExecutionResult {
        // Not used directly; kept for readability. Real work is inside the
        // outer suspendCancellableCoroutine block, so this helper is not
        // invoked in M3.1. (TODO: remove if dead code.)
        TODO("unreachable")
    }

    companion object {
        private const val TAG = "AvbToolRunnerImpl"

        const val AOSP_HEAD = "386fb90492db3bd6bc484a579bcde5b43a2a0292"

        /**
         * Whether the FEC native library (libavbfec.so) is bundled.
         * Kept as a constant false until M3.5 lands.
         */
        const val FEC_LOADED: Boolean = false
    }

    // ---------------- helpers ----------------------------------------

    /**
     * Tiny helper for encoding a List<String> into a JSON array via
     * kotlinx-serialization without leaking an import.
     */
    private object JsonArrayWrapper {
        private val j = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun wrap(args: List<String>): JsonElement =
            j.encodeToJsonElement(
                com.bingyin.materialyouprefs.data.repository.JsonStringListSerializer,
                args,
            )
    }
}

// Minimal serializer for List<String> since we don't want to introduce
// additional top-level dependencies.
val JsonStringListSerializer: kotlinx.serialization.KSerializer<List<String>> =
    kotlinx.serialization.builtins.listSerializer(kotlinx.serialization.builtins.serializer())
