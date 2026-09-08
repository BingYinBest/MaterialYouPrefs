package com.bingyin.materialyouprefs.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bingyin.materialyouprefs.data.model.CommandDefinition
import com.bingyin.materialyouprefs.ui.iconKeyToIcon
import com.bingyin.materialyouprefs.ui.viewmodel.HomeViewModel

/**
 * Home tab. M2.6+ layout — 4 cards stacked vertically:
 *
 *   1. Version card     — avbtool version + AOSP HEAD + FEC status + refresh
 *   2. Terminal entry   — big card that navigates to the Terminal route
 *   3. Recommended cmds — 5 curated commands (M4+: replaces with execution_history)
 *   4. Runtime status   — Python / Chaquopy / FEC / seed count
 */
@Composable
fun HomeScreen(
    onItemClicked: (String) -> Unit,
    onTerminalClicked: () -> Unit,
    contentPadding: PaddingValues,
) {
    val viewModel = remember {
        HomeViewModel.factory().create(HomeViewModel::class.java)
    }
    val version by viewModel.version.collectAsStateWithLifecycle()
    val recommended by viewModel.recommended.collectAsStateWithLifecycle()
    val runtime by viewModel.runtimeStatus.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { VersionCard(version, refreshing, viewModel::refresh) }
        item { TerminalEntryCard(onTerminalClicked) }
        item { RecommendedCard(recommended, onItemClicked) }
        item { RuntimeStatusCard(runtime) }
    }
}

// ---------- Card 1: Version -------------------------------------------

@Composable
private fun VersionCard(
    version: HomeViewModel.Snapshot,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "avbtool ${version.avbToolVersion}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "AOSP ${version.shortAospHead()} · Chaquopy 15.0.1 · Python 3.12",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !refreshing,
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "刷新",
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (version.fecLoaded) "FEC ✅"
                            else "FEC ⚠️ 未加载",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (version.fecLoaded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                )
                Text(
                    text = if (version.lastRefreshAtMs == 0L) "从未刷新"
                            else "上次刷新 ${formatRelative(version.lastRefreshAtMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------- Card 2: Terminal entry ------------------------------------

@Composable
private fun TerminalEntryCard(onClick: () -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Avbtool 终端控制台",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "输入任意 avbtool 命令并等待结束",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ---------- Card 3: Recommended commands ------------------------------

@Composable
private fun RecommendedCard(
    items: List<CommandDefinition>,
    onItemClicked: (String) -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = "常用命令",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (items.isEmpty()) {
                Text(
                    text = "暂无历史执行，去 Feature tab 探索",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                items.forEachIndexed { idx, cmd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClicked(cmd.id) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = iconKeyToIcon(cmd.iconKey),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cmd.title,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = cmd.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = cmd.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (idx < items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---------- Card 4: Runtime status ------------------------------------

@Composable
private fun RuntimeStatusCard(status: HomeViewModel.RuntimeStatus) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "运行时状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
            StatusRow("Python 运行时", status.pythonRuntime)
            Spacer(modifier = Modifier.height(4.dp))
            StatusRow("Python 版本", status.pythonVersion)
            Spacer(modifier = Modifier.height(4.dp))
            StatusRow(
                "FEC 库",
                if (status.fecLoaded) "已加载" else "未加载（M3 上线后启用）",
            )
            Spacer(modifier = Modifier.height(4.dp))
            StatusRow(
                "已注册命令",
                if (status.seedRowCount < 0) "seed 未完成" else "${status.seedRowCount} 条",
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ---------- Helpers ---------------------------------------------------

private fun formatRelative(timestampMs: Long): String {
    val delta = System.currentTimeMillis() - timestampMs
    return when {
        delta < 0L -> "刚刚"
        delta < 1000 -> "刚刚"
        delta < 60_000 -> "${delta / 1000} 秒前"
        delta < 3_600_000 -> "${delta / 60_000} 分钟前"
        delta < 86_400_000 -> "${delta / 3_600_000} 小时前"
        else -> "${delta / 86_400_000} 天前"
    }
}
