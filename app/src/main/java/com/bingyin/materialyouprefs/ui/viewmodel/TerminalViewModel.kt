package com.bingyin.materialyouprefs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bingyin.materialyouprefs.data.model.AvbExecutionRequest
import com.bingyin.materialyouprefs.data.model.AvbExecutionResult
import com.bingyin.materialyouprefs.data.repository.AvbToolRunner
import com.bingyin.materialyouprefs.data.repository.CommandRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the standalone Terminal screen.
 *
 * UX (M2.6+, per user requirements):
 *   - Single input box + "Run" button + output box (not an interactive shell)
 *   - "Wait for end" semantics: the entire command runs to completion before
 *     the output is emitted. No streaming.
 *   - Output files are staged to app-private directory (SAF picker arrives in M4).
 *
 * Input format is one full CLI command like:
 *   `avbtool gen_key_pair --key_key out/key --pub_key out/pub`
 *
 * The ViewModel parses this into a commandId + params map. Parsing is
 * intentionally naive (split on whitespace, first token = subcommand, rest
 * passed to the runner as raw args). Real parsing arrives with argparse
 * support in M4.
 */
class TerminalViewModel(
    private val repository: CommandRepository,
    private val runner: AvbToolRunner,
) : ViewModel() {

    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The current input text in the terminal input field. */
    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    /** The last command's combined output (stdout + stderr). */
    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    /** Whether a command is currently executing. */
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Last exit code; -1 when no command has been run yet. */
    private val _exitCode = MutableStateFlow(-1)
    val exitCode: StateFlow<Int> = _exitCode.asStateFlow()

    /** Short human-readable status message displayed above the output. */
    private val _status = MutableStateFlow("输入任意 avbtool 命令，例如：")
    val status: StateFlow<String> = _status.asStateFlow()

    fun setInput(text: String) {
        _input.value = text
    }

    fun clearOutput() {
        _output.value = ""
        _exitCode.value = -1
        _status.value = "输入任意 avbtool 命令，例如："
    }

    /**
     * Parse and execute the command in [input].
     *
     * Parsing:
     *   - Trims leading/trailing whitespace.
     *   - If it starts with `avbtool ` (case-insensitive), strips that prefix.
     *   - Splits on whitespace; the first token becomes the subcommand name.
     *   - Remaining tokens are passed as raw args to [AvbToolRunner.run].
     */
    fun runCommand() {
        if (_running.value) return
        val raw = _input.value.trim()
        if (raw.isEmpty()) {
            _status.value = "输入为空"
            return
        }
        val withoutPrefix = raw.removePrefix("avbtool ").removePrefix("avbtool")
        val tokens = withoutPrefix.trim().split(Regex("\\s+"))
        if (tokens.isEmpty() || tokens[0].isEmpty()) {
            _status.value = "缺少子命令名称"
            return
        }
        val subcommand = tokens[0]
        val restArgs = tokens.drop(1)

        _running.value = true
        _status.value = "执行中: $subcommand …"
        _output.value = ""
        _exitCode.value = -1

        vmScope.launch {
            val request = AvbExecutionRequest(
                commandId = "avbtool.$subcommand",
                args = restArgs,
            )
            val startedAt = System.currentTimeMillis()
            val result: AvbExecutionResult = try {
                runner.run(request)
            } catch (t: Throwable) {
                AvbExecutionResult.Failure(
                    errorCode = com.bingyin.materialyouprefs.data.model.ErrorCode.UNKNOWN,
                    message = t.message ?: t::class.java.simpleName,
                    cause = t,
                )
            }
            val durationMs = System.currentTimeMillis() - startedAt

            when (result) {
                is AvbExecutionResult.Success -> {
                    _exitCode.value = result.exitCode
                    _output.value = buildOutput(result.stdout, result.stderr)
                    _status.value = "完成 (exit=${result.exitCode}, ${durationMs}ms)"
                }
                is AvbExecutionResult.Failure -> {
                    _exitCode.value = -1
                    _output.value = buildOutput("", "[${result.errorCode}] ${result.message}")
                    _status.value = "失败: ${result.errorCode}"
                }
            }
            _running.value = false
        }
    }

    /**
     * Convenience: pre-fill the input field with the given command name
     * (used when navigating from the Home recommended list).
     */
    fun prefill(commandName: String) {
        if (commandName.isEmpty()) return
        _input.value = "avbtool $commandName "
    }

    private fun buildOutput(stdout: String, stderr: String): String {
        val sb = StringBuilder()
        if (stdout.isNotEmpty()) {
            sb.append(stdout)
            if (!stdout.endsWith("\n")) sb.append("\n")
        }
        if (stderr.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("[stderr]\n").append(stderr)
            if (!stderr.endsWith("\n")) sb.append("\n")
        }
        if (sb.isEmpty()) sb.append("(无输出)")
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        vmScope.cancel()
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = com.bingyin.materialyouprefs.AppState
                val repo = app.commandRepository
                    ?: throw IllegalStateException("AppState.commandRepository not built yet")
                val runner = app.avbToolRunner
                    ?: throw IllegalStateException("AppState.avbToolRunner not built yet")
                return TerminalViewModel(repo, runner) as T
            }
        }
    }
}
