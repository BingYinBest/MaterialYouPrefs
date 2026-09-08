package com.bingyin.materialyouprefs.data.model

import android.net.Uri

/**
 * User-facing request to execute an avbtool subcommand.
 *
 * @property commandId stable id matching [CommandDefinition.id]
 * @property args      ordered positional arguments
 * @property params    named parameters keyed by argparse option name
 * @property inputUris SAF URIs of input files (may be empty for e.g. `add_hashtree_footer` with only stdout output)
 * @property outputUri optional SAF Uri for output; if null and command writes files, runner picks temp + returns via callback
 * @property timeoutMs default 60_000 ms
 */
data class AvbExecutionRequest(
    val commandId: String,
    val args: List<String> = emptyList(),
    val params: Map<String, String> = emptyMap(),
    val inputUris: List<Uri> = emptyList(),
    val outputUri: Uri? = null,
    val timeoutMs: Long = 60_000L,
)

/**
 * Result of an [AvbExecutionRequest].
 */
sealed interface AvbExecutionResult {
    data class Success(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val outputUri: Uri?,
        val durationMs: Long,
    ) : AvbExecutionResult

    data class Failure(
        val errorCode: ErrorCode,
        val message: String,
        val cause: Throwable? = null,
    ) : AvbExecutionResult
}

/**
 * Categorized error codes surfaced to the UI. See REVIEW_CRITERIA.
 */
enum class ErrorCode {
    MISSING_REQUIRED_ARG,
    UNKNOWN_PARAM,
    PYTHON_EXCEPTION,
    FEC_LIBRARY_MISSING,
    PROCESS_TIMEOUT,
    FILE_IO_ERROR,
    PERMISSION_DENIED,
    UNKNOWN,
}
