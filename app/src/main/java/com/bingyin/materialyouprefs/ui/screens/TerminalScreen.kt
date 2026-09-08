package com.bingyin.materialyouprefs.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bingyin.materialyouprefs.ui.viewmodel.TerminalViewModel

/**
 * Standalone Terminal screen. Accessed via the `terminal` route.
 *
 * Layout:
 *   - TopAppBar with back arrow + clear button
 *   - Status line (running / exit code / duration)
 *   - Output box (monospace, scrollable)
 *   - Input row: OutlinedTextField + Run button
 */
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
) {
    val viewModel = remember {
        TerminalViewModel.factory().create(TerminalViewModel::class.java)
    }
    val input by viewModel.input.collectAsStateWithLifecycle()
    val output by viewModel.output.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val exitCode by viewModel.exitCode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Avbtool 终端") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.clearOutput() },
                        enabled = !running,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "清空输出",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(16.dp)
                            .height(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    text = if (exitCode == -1) status else "exit=${exitCode} · $status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Output box
            ElevatedCard(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        text = if (output.isEmpty()) "(执行结果将显示在这里)" else output,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Input row
            OutlinedTextField(
                value = input,
                onValueChange = { viewModel.setInput(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("avbtool gen_key_pair --key_key out/key --pub_key out/pub") },
                singleLine = true,
                enabled = !running,
            )

            Button(
                onClick = { viewModel.runCommand() },
                enabled = !running && input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (running) "执行中…" else "执行")
            }
        }
    }
}
