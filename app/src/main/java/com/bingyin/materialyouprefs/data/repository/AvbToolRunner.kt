package com.bingyin.materialyouprefs.data.repository

import android.net.Uri
import com.bingyin.materialyouprefs.data.model.AvbExecutionRequest
import com.bingyin.materialyouprefs.data.model.AvbExecutionResult
import com.bingyin.materialyouprefs.data.model.CommandParam

/**
 * Executes avbtool subcommands inside the embedded Python runtime.
 *
 * Implementation will live in M3 (Chaquopy + patched avbtool.py). This
 * interface is the contract UI and Repository code depends on.
 */
interface AvbToolRunner {

    /**
     * Execute an avbtool subcommand.
     *
     * Must be called from a background coroutine; implementation is expected
     * to be cancel-aware (checks `CoroutineContext` cancellation).
     */
    suspend fun run(request: AvbExecutionRequest): AvbExecutionResult

    /**
     * Fetch the argparse help output for a given subcommand and return the
     * parsed parameter list.
     *
     * Used by [CommandRepository] to keep [CommandDefinition.paramsJson]
     * up-to-date from the real avbtool binary rather than a stale seed.
     *
     * @param commandName subcommand name, e.g. `gen_key_pair`
     * @return parsed parameters, empty list on failure (caller decides fallback)
     */
    suspend fun fetchHelp(commandName: String): List<CommandParam>

    /**
     * Return the AOSP commit SHA this Python runtime was built from.
     * Empty string if unknown.
     */
    fun aospHead(): String

    /**
     * Whether the FEC native library (libavbfec.so) is loaded. Cacheable.
     */
    fun isFecLoaded(): Boolean

    /**
     * Copy a URI input to a temporary file the Python sandbox can read,
     * returning the local path. Caller is responsible for cleanup.
     */
    suspend fun stageInput(uri: Uri): String

    /**
     * Move a produced temp file to a SAF output Uri. Returns true on success.
     */
    suspend fun promoteToOutput(localPath: String, outputUri: Uri): Boolean

    /**
     * Clean up temporary files from a prior run. Called at session start.
     */
    fun cleanupTemp()
}
