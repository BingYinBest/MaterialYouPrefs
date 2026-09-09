package com.bingyin.materialyouprefs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bingyin.materialyouprefs.data.model.AvbExecutionResult
import com.bingyin.materialyouprefs.data.model.CommandDefinition
import com.bingyin.materialyouprefs.data.model.CommandParam
import com.bingyin.materialyouprefs.data.model.ParamType
import com.bingyin.materialyouprefs.ui.iconKeyToIcon
import com.bingyin.materialyouprefs.ui.viewmodel.DetailState
import com.bingyin.materialyouprefs.ui.viewmodel.DetailViewModel

/**
 * Detail screen (M4.1).
 *
 * Two sections:
 *   - Scrollable param form at the top: one row per [CommandParam],
 *     rendered per [ParamType].
 *   - A live "output" card appended below the form when the last run
 *     finishes.
 *
 * M4.2 will add SAF picker plumbing (inputUris / outputUri) and M3.5.2b
 * fd-bridge; M4.1 always runs with empty SAF inputs and lets the runner
 * use its copy-through-tempfile path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    itemId: String,
    onBack: () -> Unit,
) {
    val viewModel = remember(itemId) {
        DetailViewModel.factory(itemId).create(DetailViewModel::class.java)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.command?.title ?: "命令详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.command == null && state.error != null -> ErrorView(
                message = state.error ?: "未知错误",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            else -> CommandExecutionContent(
                state = state,
                viewModel = viewModel,
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun CommandExecutionContent(
    state: DetailState,
    viewModel: DetailViewModel,
    contentPadding: PaddingValues,
) {
    val command = state.command ?: return
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        // Status strip.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = state.status,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            state.lastResult?.let { r ->
                val exit = when (r) {
                    is AvbExecutionResult.Success -> r.exitCode
                    is AvbExecutionResult.Failure -> -1
                }
                Text(
                    text = "exit=$exit",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (exit == 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Scrollable param form + output.
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { CommandHeader(command) }
            if (state.params.isEmpty()) {
                item(key = "empty-params") {
                    Text(
                        text = "该命令无需参数。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                state.params.forEach { param ->
                    item(key = "param-${param.name}") {
                        ParamRow(
                            param = param,
                            value = state.inputValues[param.name] ?: param.default.orEmpty(),
                            enabled = !state.isRunning,
                            onChange = { v -> viewModel.setValue(param.name, v) },
                        )
                    }
                }
            }
            item(key = "actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { viewModel.execute() },
                        enabled = !state.isRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("执行")
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearResult() },
                        enabled = !state.isRunning,
                    ) {
                        Text("清空输出")
                    }
                }
            }
            state.lastResult?.let { result ->
                item(key = "output") { OutputSection(result) }
            }
        }
    }
}

@Composable
private fun CommandHeader(command: CommandDefinition) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconKeyToIcon(command.iconKey),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = command.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = command.summary.ifEmpty { command.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/**
 * Render one parameter according to its [ParamType].
 *
 *   - BOOLEAN  → Switch
 *   - CHOICE   → ExposedDropdownMenu when choices are non-empty
 *   - INT/PATH/FILE/STRING → OutlinedTextField (single line, plain text;
 *     argparse does strict validation)
 */
@Composable
private fun ParamRow(
    param: CommandParam,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = param.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (param.required) {
                    Text(
                        text = "必填",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = param.type.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (param.description.isNotBlank()) {
                Text(
                    text = param.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (param.default?.isNotBlank() == true) {
                Text(
                    text = "默认：${param.default}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (param.type) {
                ParamType.BOOLEAN -> {
                    val isOn = value.equals("true", ignoreCase = true)
                        || value == "1" || value.equals("yes", ignoreCase = true)
                    Switch(
                        checked = isOn,
                        onCheckedChange = { checked -> onChange(checked.toString()) },
                        enabled = enabled,
                    )
                }
                ParamType.CHOICE -> {
                    if (param.choices.isNotEmpty()) {
                        ChoiceRow(
                            choices = param.choices,
                            value = value,
                            enabled = enabled,
                            onChange = onChange,
                        )
                    } else {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { if (enabled) onChange(it) },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("选择值") },
                            singleLine = true,
                        )
                    }
                }
                else -> OutlinedTextField(
                    value = value,
                    onValueChange = { if (enabled) onChange(it) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(param.name) },
                    singleLine = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(
    choices: List<String>,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var current by remember(value) { mutableStateOf(value.ifBlank { choices.firstOrNull().orEmpty() }) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            choices.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c) },
                    leadingIcon = {
                        if (c == current) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        current = c
                        onChange(c)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun OutputSection(result: AvbExecutionResult) {
    val stdout: String
    val stderr: String
    val exitCode: Int
    val isError: Boolean
    when (result) {
        is AvbExecutionResult.Success -> {
            stdout = result.stdout
            stderr = result.stderr
            exitCode = result.exitCode
            isError = false
        }
        is AvbExecutionResult.Failure -> {
            stdout = ""
            stderr = "[${result.errorCode}] ${result.message}"
            exitCode = -1
            isError = true
        }
    }
    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isError) "错误" else "输出",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "exit=$exitCode",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (exitCode == 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = formatOutput(stdout, stderr).take(64 * 1024),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(8.dp)
                        .height(240.dp),
                )
            }
        }
    }
}

private fun formatOutput(stdout: String, stderr: String): String {
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

@Composable
private fun ErrorView(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
