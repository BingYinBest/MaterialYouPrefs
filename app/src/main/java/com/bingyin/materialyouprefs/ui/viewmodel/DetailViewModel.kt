package com.bingyin.materialyouprefs.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bingyin.materialyouprefs.AppState
import com.bingyin.materialyouprefs.data.db.AvbDatabase
import com.bingyin.materialyouprefs.data.db.ExecutionEntity
import com.bingyin.materialyouprefs.data.model.AvbExecutionRequest
import com.bingyin.materialyouprefs.data.model.AvbExecutionResult
import com.bingyin.materialyouprefs.data.model.CommandDefinition
import com.bingyin.materialyouprefs.data.model.CommandParam
import com.bingyin.materialyouprefs.data.model.ErrorCode
import com.bingyin.materialyouprefs.data.model.ParamType
import com.bingyin.materialyouprefs.data.repository.CommandRepository
import com.bingyin.materialyouprefs.data.repository.AvbToolRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * UI state for a single [DetailScreen] instance.
 *
 * One ViewModel per `commandId`; created lazily by the screen when the
 * user navigates to `detail/{itemId}`.
 */
data class DetailState(
    val command: CommandDefinition? = null,
    val params: List<CommandParam> = emptyList(),
    val inputValues: Map<String, String> = emptyMap(),
    val isRunning: Boolean = false,
    val lastResult: AvbExecutionResult? = null,
    val status: String = "加载中…",
    val error: String? = null,
)

/**
 * ViewModel for the Detail screen (M4.1).
 *
 * Responsibilities:
 *   1. Load the [CommandDefinition] for the target [commandId] via
 *      [CommandRepository.getByIdOrFetch] (which also refreshes `paramsJson`
 *      from `avbtool <cmd> --help` when the cache is stale).
 *   2. Surface the parsed [CommandParam] list to the UI so it can render
 *      a dynamic form.
 *   3. On `execute()`, expand user input into an ordered `args` list in the
 *      `--flag=value` shape argparse expects, build an [AvbExecutionRequest],
 *      call [AvbToolRunner.run], and record the outcome to
 *      `execution_history`.
 *
 * M4.2 will add SAF picker plumbing (inputUris / outputUri) and M3.5.2b
 * fd-bridge; this milestone leaves those inputs empty and the runner uses
 * its copy-through-tempfile path.
 */
class DetailViewModel(
    private val repository: CommandRepository,
    private val runner: AvbToolRunner,
    private val db: AvbDatabase,
    private val commandId: String,
) : ViewModel() {

    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    init {
        loadCommand()
    }

    private fun loadCommand() {
        vmScope.launch {
            val cmd = runCatching { repository.getByIdOrFetch(commandId) }
                .getOrElse { e ->
                    _state.value = _state.value.copy(
                        status = "加载失败",
                        error = e.message ?: e::class.java.simpleName,
                    )
                    return@launch
                }
            if (cmd == null) {
                _state.value = _state.value.copy(
                    status = "命令未找到",
                    error = "No CommandDefinition with id=$commandId",
                )
                return@launch
            }
            val params = runCatching {
                json.decodeFromString<List<CommandParam>>(cmd.paramsJson)
            }.getOrDefault(emptyList())

            // Pre-fill with each param's default (if any).
            val defaults = params
                .mapNotNull { p -> p.default?.takeIf { it.isNotEmpty() }?.let { p.name to it } }
                .toMap()

            _state.value = DetailState(
                command = cmd,
                params = params,
                inputValues = defaults,
                status = if (params.isEmpty()) "命令无参数，可直接执行" else "准备好执行",
            )
        }
    }

    /**
     * Set a single parameter's value in the current form state. Used by
     * text fields and switches in [DetailScreen].
     */
    fun setValue(paramName: String, value: String) {
        val current = _state.value
        if (current.isRunning) return
        _state.value = current.copy(
            inputValues = current.inputValues + (paramName to value),
        )
    }

    /**
     * Validate + execute the currently form-filled parameters.
     *
     * Validation: every param with `required=true` must have a non-blank
     * value in [DetailState.inputValues] (default also counts). Missing
     * required params short-circuit with a friendly status message; we do
     * not fail the run on non-required-but-blank params — those are simply
     * omitted from the CLI argv.
     *
     * @param inputUris  SAF URIs for input files (M4.2 will populate; M4.1
     *                   always passes the empty list).
     * @param outputUri  Optional SAF Uri for output (M4.2).
     */
    fun execute(
        inputUris: List<Uri> = emptyList(),
        outputUri: Uri? = null,
    ) {
        val current = _state.value
        if (current.isRunning) return
        val cmd = current.command
        if (cmd == null) {
            _state.value = current.copy(status = "命令未加载")
            return
        }
        val params = current.params
        val values = current.inputValues

        // Required-param check.
        for (p in params) {
            if (!p.required) continue
            val v = values[p.name]?.takeIf { it.isNotBlank() } ?: p.default?.takeIf { it.isNotBlank() }
            if (v == null) {
                _state.value = current.copy(
                    status = "缺少必填参数：${p.name}",
                    error = "缺少必填参数：${p.name}",
                )
                return
            }
        }

        // Expand params into argv tokens in `--flag=value` shape.
        //
        // argparse accepts `--opt=value` and `--opt value` interchangeably,
        // so this keeps argv short and quote-safe. Boolean flags are
        // emitted as bare `--flag` when the user's value is truthy and
        // dropped entirely otherwise (argparse's `action='store_true'`
        // would reject `--flag=False`).
        val args = buildList {
            for (p in params) {
                val raw = values[p.name]?.takeIf { it.isNotBlank() } ?: p.default?.takeIf { it.isNotBlank() }
                if (raw == null) continue
                when (p.type) {
                    ParamType.BOOLEAN -> {
                        when (raw.lowercase()) {
                            "true", "1", "yes", "on" -> add(p.name)
                            else -> Unit // flag off → omit
                        }
                    }
                    else -> add("${p.name}=$raw")
                }
            }
        }

        val request = AvbExecutionRequest(
            commandId = cmd.id,
            args = args,
            params = values,
            inputUris = inputUris,
            outputUri = outputUri,
        )

        _state.value = current.copy(
            isRunning = true,
            status = "执行中：${cmd.name} …",
            error = null,
            lastResult = null,
        )

        val startedAt = System.currentTimeMillis()
        vmScope.launch {
            val result: AvbExecutionResult = try {
                runner.run(request)
            } catch (t: Throwable) {
                AvbExecutionResult.Failure(
                    errorCode = ErrorCode.UNKNOWN,
                    message = t.message ?: t::class.java.simpleName,
                    cause = t,
                )
            }
            val duration = System.currentTimeMillis() - startedAt

            // Persist to execution_history so the Home card can show real
            // recent runs (M4.3) and so the user doesn't lose the trace
            // after navigating away.
            runCatching {
                db.executionDao().insert(
                    ExecutionEntity(
                        commandId = cmd.id,
                        argsJson = json.encodeToString(request.args),
                        paramsJson = json.encodeToString(request.params),
                        stdout = when (result) {
                            is AvbExecutionResult.Success -> result.stdout
                            is AvbExecutionResult.Failure -> ""
                        },
                        stderr = when (result) {
                            is AvbExecutionResult.Success -> result.stderr
                            is AvbExecutionResult.Failure -> result.message
                        },
                        exitCode = when (result) {
                            is AvbExecutionResult.Success -> result.exitCode
                            is AvbExecutionResult.Failure -> -1
                        },
                        startedAtMs = startedAt,
                        durationMs = duration,
                        inputFiles = inputUris.joinToString(","),
                        outputFiles = outputUri?.toString() ?: "",
                    ),
                )
            }

            when (result) {
                is AvbExecutionResult.Success -> {
                    _state.value = _state.value.copy(
                        isRunning = false,
                        lastResult = result,
                        status = "完成 (exit=${result.exitCode}, ${duration}ms)",
                    )
                }
                is AvbExecutionResult.Failure -> {
                    _state.value = _state.value.copy(
                        isRunning = false,
                        lastResult = result,
                        status = "失败：${result.errorCode}",
                        error = result.message,
                    )
                }
            }
        }
    }

    /**
     * Clear the last result / status so the user can retry a fresh run
     * without the stale output bleeding through.
     */
    fun clearResult() {
        if (_state.value.isRunning) return
        _state.value = _state.value.copy(lastResult = null, error = null)
    }

    override fun onCleared() {
        super.onCleared()
        vmScope.cancel()
    }

    companion object {
        /**
         * Factory bound to a specific [commandId]. Screen creates it via
         * `remember(commandId) { DetailViewModel.factory(commandId) }`.
         */
        fun factory(commandId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = AppState
                    val repo = app.commandRepository
                        ?: throw IllegalStateException("AppState.commandRepository not built yet")
                    val runner = app.avbToolRunner
                        ?: throw IllegalStateException("AppState.avbToolRunner not built yet")
                    val db = app.database
                        ?: throw IllegalStateException("AppState.database not built yet")
                    return DetailViewModel(repo, runner, db, commandId) as T
                }
            }
    }
}
