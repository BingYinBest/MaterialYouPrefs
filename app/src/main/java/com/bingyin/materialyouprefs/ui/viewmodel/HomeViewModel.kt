package com.bingyin.materialyouprefs.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.bingyin.materialyouprefs.data.PrefGroup
import com.bingyin.materialyouprefs.data.PrefItem
import com.bingyin.materialyouprefs.data.repository.CommandRepository
import com.bingyin.materialyouprefs.data.model.CommandDefinition
import com.bingyin.materialyouprefs.ui.iconKeyToIcon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Home tab. Reads commands from Room via
 * [CommandRepository.observeAll] and groups them alphabetically by
 * their `group` field for stable UI ordering.
 *
 * M2.5 notes:
 *  - `CommandEntity` doesn't persist the seed JSON `tab` field, so we
 *    can't yet filter by `tab == "home"` in Room. This VM returns ALL
 *    commands grouped; M2.5 scope is 'data path smoke test' only.
 *  - Room schema migration to add a `tab` column is deferred to M3
 *    alongside other schema work.
 *  - Icon is resolved via [iconKeyToIcon]; falls back to a neutral
 *    icon when the key isn't in the map.
 *  - Empty state (before seed completes) is exposed as `emptyList()`
 *    so the caller can render a loading hint.
 */
class HomeViewModel(repository: CommandRepository) : ViewModel() {

    val groups: StateFlow<List<PrefGroup>> = repository.observeAll()
        .map { commands -> groupByTab(commands) }
        .stateIn(
            scope = this,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    private fun groupByTab(commands: List<CommandDefinition>): List<PrefGroup> {
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

    private companion object {
        /** Chinese group titles (matches PrefData style). */
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
    }
}
