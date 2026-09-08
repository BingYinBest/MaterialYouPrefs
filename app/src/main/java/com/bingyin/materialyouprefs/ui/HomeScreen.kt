package com.bingyin.materialyouprefs.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bingyin.materialyouprefs.AppState
import com.bingyin.materialyouprefs.ui.components.PreferenceGroupSection
import com.bingyin.materialyouprefs.ui.viewmodel.HomeViewModel

/**
 * Home tab. M2.5 rewire: reads commands from Room via
 * [HomeViewModel] instead of the static [PrefData.homeGroups].
 *
 * The view model is bound to the `androidx.lifecycle.ViewModel` contract
 * but constructed in-place because we have no ViewModelStoreOwner
 * (Compose-only activity without the `ViewModelProvider` wiring). The
 * VM holds no state itself, so re-creation on config change is cheap.
 */
@Composable
fun HomeScreen(
    onItemClicked: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val viewModel = remember { HomeViewModel(AppState.homeRepository) }
    val groups by viewModel.groups.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (groups.isEmpty()) {
            item {
                Text(
                    text = "加载中…（seed 尚未完成）",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(groups) { group ->
            PreferenceGroupSection(
                group = group,
                onItemClicked = onItemClicked,
            )
        }
    }
}
