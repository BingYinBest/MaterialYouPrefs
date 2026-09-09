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
    /**
     * SAF Uri selected per parameter name. Populated by
     * [DetailViewModel.setInputUri] when the user picks a file for a
     * FILE/PATH param via the SAF picker. [execute] will stage each Uri
     * to a local path (via [AvbToolRunner.stageInput]) before invoking
     * the Python runner.
     */
    val inputUris: Map<String, Uri> = emptyMap(),
    /**
     * SAF Uri selected by the user as the destination for the freshest
     * output file produced by this command (M4.2b). If the runner sees
     * non-empty `generatedFiles` from Python, it promotes the newest one
     * into this Uri via [AvbToolRunner.promoteToOutput] and echoes it
     * back on [AvbExecutionResult.Success.outputUri].
     */
    val outputUri: Uri? = null,
    val isRunning: Boolean = false,
    val lastResult: AvbExecutionResult? = null,
    val status: String = "加载中…",
    val error: String? = null,
)

/**
 * ViewModel for the Detail screen.
 *
 * Responsibilities:
 *   1. Load the [CommandDefinition] for the target [commandId] via
 *      [CommandRepository.getByIdOrFetch] (which also refreshes `paramsJson`
 *      from `avbtool <cmd> --help` when the cache is stale).
 *   2. Surface the parsed [CommandParam] list to the UI so it can render
 *      a dynamic form.
 *   3. On `execute()`, stage any SAF [Uri] inputs to local temp files
 *      (M4.2), expand user input into an ordered `args` list in the
 *      `--flag=value` shape argparse expects, build an
 *      [AvbExecutionRequest], call [AvbToolRunner.run], and record the
 *      outcome to `execution_history`.
 *
 * Output path (M4.2b): if the user picks an output SAF Uri via
 * [setOutputUri] (or the UI picker), the request carries that Uri and the
 * runner promotes the freshest file Python produced into it after
 * execution. The fd-bridge (M3.5.2b) — letting avbtool write directly to
 * a SAF output Uri via a `/saf/fd/<id>` virtual path — is deferred to
 * M4.2c.
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
     * Set a SAF Uri for a specific parameter. The Uri is stored alongside
     * [DetailState.inputValues] but is NOT what gets expanded to argv —
     * [execute] stages each such Uri to a local temp file via
     * [AvbToolRunner.stageInput] and substitutes the resulting path into
     * the corresponding parameter slot in [AvbExecutionRequest.args].
     *
     * @param paramName param name (e.g. "key_out_path"); must exist in
     *                  [CommandParam.name]
     * @param uri       Uri from `ActivityResultContracts.OpenDocument()`
     */
    fun setInputUri(paramName: String, uri: Uri) {
        val current = _state.value
        if (current.isRunning) return
        _state.value = current.copy(
            inputUris = current.inputUris + (paramName to uri),
            // Keep the Uri string visible in the inputValues map so the
            // UI can echo "已选择: …" — execute() will override with the
            // staged local path before running.
            inputValues = current.inputValues + (paramName to uri.toString()),
        )
    }

    /**
     * Set the SAF Uri for the freshest output file. If set and the runner
     * sees non-empty `generatedFiles`, the newest one is promoted into
     * this Uri via [AvbToolRunner.promoteToOutput] before returning.
     */
    fun setOutputUri(uri: Uri) {
        val current = _state.value
        if (current.isRunning) return
        _state.value = current.copy(outputUri = uri)
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
     * SAF handling (M4.2 / M4.2b):
     *   - If any parameter appears in [DetailState.inputUris], call
     *     [AvbToolRunner.stageInput] on that Uri (background thread) to
     *     copy the file into `cacheDir/avbtool-in-*.<ext>` and substitute
     *     the resulting local path into the args slot.
     *   - If [outputUri] (arg or state) is set, the runner promotes the
     *     freshest file Python reported in `generatedFiles` into that Uri
     *     after execution completes (see `AvbToolRunnerImpl.run`).
     *
     * @param outputUri Optional override for the state-tracked output Uri.
     *                   Explicit arg wins over [DetailState.outputUri].
     */
    fun execute(
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
        val safUris = current.inputUris
        // Explicit arg wins over the remembered state (UI picker path).
        val chosenOutputUri = outputUri ?: current.outputUri

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

        val startedAt = System.currentTimeMillis()
        _state.value = current.copy(
            isRunning = true,
            status = "准备输入文件…",
            error = null,
            lastResult = null,
        )

        vmScope.launch {
            // Hold onto the request outside the try so the persist step
            // below can record argsJson/paramsJson even on failure.
            var request: AvbExecutionRequest? = null

            val result: AvbExecutionResult = try {
                // Stage each SAF Uri to a local temp file, then override
                // the inputValues slot for that param so argv carries the
                // real path (not the Uri string).
                val stagedPaths = mutableMapOf<String, String>()
                for ((name, uri) in safUris) {
                    val localPath = runner.stageInput(uri)
                    stagedPaths[name] = localPath
                }
                val resolvedValues = values + stagedPaths

                // Expand params into argv tokens in `--flag=value` shape.
                //
                // argparse accepts `--opt=value` and `--opt value`
                // interchangeably, so this keeps argv short and
                // quote-safe. Boolean flags are emitted as bare `--flag`
                // when the user's value is truthy and dropped entirely
                // otherwise (argparse's `action='store_true'` would
                // reject `--flag=False`).
                val args = buildList {
                    for (p in params) {
                        val raw = resolvedValues[p.name]
                            ?.takeIf { it.isNotBlank() }
                            ?: p.default?.takeIf { it.isNotBlank() }
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

                val req = AvbExecutionRequest(
                    commandId = cmd.id,
                    args = args,
                    params = resolvedValues,
                    inputUris = safUris.values.toList(),
                    outputUri = chosenOutputUri,
                )
                request = req

                _state.value = _state.value.copy(
                    status = "执行中：${cmd.name} …",
                )

                runner.run(req)
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
            // after navigating away. If staging threw before we could
            // build the request, `request` is still null and we skip the
            // history write — the user still sees the error status.
            val persistedRequest = request
            if (persistedRequest != null) {
                runCatching {
                    db.executionDao().insert(
                        ExecutionEntity(
                            commandId = cmd.id,
                            argsJson = json.encodeToString(persistedRequest.args),
                            paramsJson = json.encodeToString(persistedRequest.params),
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
                            inputFiles = safUris.values.joinToString(",") { it.toString() },
                            outputFiles = chosenOutputUri?.toString() ?: "",
                        ),
                    )
                }
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
