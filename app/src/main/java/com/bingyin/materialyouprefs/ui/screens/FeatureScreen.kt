package com.bingyin.materialyouprefs.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bingyin.materialyouprefs.data.PrefData
import com.bingyin.materialyouprefs.ui.components.PreferenceGroupSection

@Composable
fun FeatureScreen(
    onItemClicked: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        items(PrefData.featureGroups) { group ->
            PreferenceGroupSection(
                group = group,
                onItemClicked = onItemClicked,
            )
        }
    }
}
