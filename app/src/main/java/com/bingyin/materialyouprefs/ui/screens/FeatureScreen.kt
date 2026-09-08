package com.bingyin.materialyouprefs.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bingyin.materialyouprefs.ui.components.PreferenceGroupSection
import com.bingyin.materialyouprefs.ui.viewmodel.FeatureViewModel

/**
 * Feature tab. M2.6+: reads commands from Room (tab == "feature") and
 * groups them by `group` field. Falls back to empty state until seed completes.
 */
@Composable
fun FeatureScreen(
    onItemClicked: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val viewModel = remember {
        FeatureViewModel.factory().create(FeatureViewModel::class.java)
    }
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
