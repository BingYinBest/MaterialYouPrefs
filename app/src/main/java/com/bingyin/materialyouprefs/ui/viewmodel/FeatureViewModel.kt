package com.bingyin.materialyouprefs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bingyin.materialyouprefs.data.PrefGroup
import com.bingyin.materialyouprefs.data.PrefItem
import com.bingyin.materialyouprefs.data.model.CommandDefinition
import com.bingyin.materialyouprefs.data.repository.CommandRepository
import com.bingyin.materialyouprefs.ui.iconKeyToIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Feature tab.
 *
 * Reads all commands where `tab == "feature"` from Room and groups them
 * by the `group` field, alphabetically.
 */
class FeatureViewModel(
    private val repository: CommandRepository,
) : ViewModel() {

    private val vmScope = CoroutineScope(SupervisorJob())

    val groups: StateFlow<List<PrefGroup>> = repository.observeByTab("feature")
        .map { commands -> groupByTitle(commands) }
        .stateIn(
            scope = vmScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    override fun onCleared() {
        super.onCleared()
        vmScope.cancel()
    }

    private fun groupByTitle(commands: List<CommandDefinition>): List<PrefGroup> {
        if (commands.isEmpty()) return emptyList()
        val byGroup = commands.groupBy { it.group.ifEmpty { "GENERAL" } }
        return byGroup.entries
            .sortedBy { it.key.lowercase() }
            .map { (groupName, entries) ->
                PrefGroup(
                    title = GROUP_TITLES[groupName] ?: groupName,
                    items = entries
                        .sortedBy { it.title }
                        .map { cmd ->
                            PrefItem(
                                id = cmd.id,
                                title = cmd.title,
                                subtitle = cmd.summary,
                                icon = iconKeyToIcon(cmd.iconKey),
                            )
                        },
                )
            }
    }

    companion object {
        val GROUP_TITLES = mapOf(
            "KEY" to "密钥",
            "VBMETA" to "VBMETA 镜像",
            "HASHTREE" to "哈希树与页脚",
            "VERIFY" to "验证",
            "META" to "元数据",
            "ALGO" to "算法",
            "CONFIG" to "配置",
            "ABOUT" to "关于",
            "GENERAL" to "其他",
        )

        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = com.bingyin.materialyouprefs.AppState.commandRepository
                    ?: throw IllegalStateException("AppState.commandRepository not built yet")
                return FeatureViewModel(repo) as T
            }
        }
    }
}
