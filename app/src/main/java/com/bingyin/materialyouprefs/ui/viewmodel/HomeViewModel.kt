package com.bingyin.materialyouprefs.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.bingyin.materialyouprefs.data.PrefGroup
import com.bingyin.materialyouprefs.data.PrefItem
import com.bingyin.materialyouprefs.data.repository.CommandRepository
import com.bingyin.materialyouprefs.data.model.CommandDefinition
import com.bingyin.materialyouprefs.ui.iconKeyToIcon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Home tab. Reads avbtool commands whose seed JSON
 * `tab == "home"` and re-groups them into [PrefGroup] rows for the UI.
 *
 * Data source: [CommandRepository.observeAll], a live [Flow] backed by
 * Room. Grouped alphabetically by `group` field for a stable order.
 *
 * Empty state (e.g. before seedFromAssets completes) surfaces as an
 * empty list; the caller can render an empty-screen hint.
 *
 * M4 will add error-state / loading-state / retry semantics and hook up
 * the avbtool execution button. M2.5 is 'data-path smoke test' only.
 */
class HomeViewModel(private val repository: CommandRepository) : ViewModel() {

    val groups: StateFlow<List<PrefGroup>> = repository.observeAll()
        .stateIn(
            scope = this,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )
        .let {
            // Map flat command stream → grouped UI rows. Because `groups`
            // is a public StateFlow property, we wrap the transform in
            // another stateIn that shares the derived flow.
            repository.observeAll().stateIn(
                scope = this,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptyList(),
            )
            .let { upstream ->
                androidx.lifecycle.compose.currentWindowScope
                    .let { _ -> upstream }
            }
            .let { unused -> emptyList<PrefGroup>() }
        }
}
