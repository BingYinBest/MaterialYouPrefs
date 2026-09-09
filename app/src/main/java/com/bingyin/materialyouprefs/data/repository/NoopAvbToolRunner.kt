package com.bingyin.materialyouprefs.data.repository

import android.net.Uri
import com.bingyin.materialyouprefs.data.model.AvbExecutionRequest
import com.bingyin.materialyouprefs.data.model.AvbExecutionResult
import com.bingyin.materialyouprefs.data.model.CommandParam
import com.bingyin.materialyouprefs.data.model.ErrorCode

/**
 * M2.5 placeholder [AvbToolRunner] used before Chaquopy + avbtool.py land
 * in M3. Every method returns a safe default so the UI can render, the
 * seed JSON can be parsed, and the Room layer can be exercised end-to-end
 * without a real Python runtime.
 *
 * `fetchHelp` returns an empty list so [CommandRepository.getByIdOrFetch]
 * keeps the paramsJson from `commands_seed.json` instead of trying to
 * re-derive it from a real `--help` invocation.
 *
 * Real implementation: see docs/tech/PYTHON_RUNTIME.md (Chaquopy +
 * python_main.py).
 */
class NoopAvbToolRunner : AvbToolRunner {

    override suspend fun run(request: AvbExecutionRequest): AvbExecutionResult =
        AvbExecutionResult.Failure(
            errorCode = ErrorCode.UNKNOWN,
            message = "avbtool runtime not implemented until M3 (Chaquopy).",
        )

    override suspend fun fetchHelp(commandName: String): List<CommandParam> =
        emptyList()

    override fun aospHead(): String = ""

    override fun isFecLoaded(): Boolean = false

    override suspend fun stageInput(uri: Uri): String =
        throw UnsupportedOperationException("SAF staging arrives in M3 (SAFBridge.kt).")

    override suspend fun promoteToOutput(localPath: String, outputUri: Uri): Boolean = false

    override fun cleanupTemp() { /* nothing to clean at M2.5 */ }
}
