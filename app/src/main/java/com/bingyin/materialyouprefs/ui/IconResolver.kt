package com.bingyin.materialyouprefs.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps `CommandEntity.iconKey` (uppercase string from seed JSON) to a
 * Material icon. Unknown keys fall back to [Icons.Outlined.Info].
 *
 * Kept as a top-level function so both ViewModels and UI components
 * can share it. If icon resolution needs more than a `when`, move to
 * an `IconRegistry` object.
 */
fun iconKeyToIcon(key: String): ImageVector = when (key.uppercase()) {
    "KEY" -> Icons.Outlined.Key
    "VBMETA" -> Icons.Outlined.Verified
    "HASHTREE" -> Icons.Outlined.Fingerprint
    "VERIFY" -> Icons.Outlined.Verified
    "META" -> Icons.Outlined.Info
    "ALGO" -> Icons.Outlined.AutoAwesome
    "CONFIG" -> Icons.Outlined.Tune
    "ABOUT" -> Icons.Outlined.Info
    "INFO" -> Icons.Outlined.Info
    "DELETE" -> Icons.Outlined.Info // TODO M4: use Outlined.Delete
    "ZERO" -> Icons.Outlined.AutoAwesome
    "RESIZE" -> Icons.Outlined.AutoAwesome
    "EXTRACT" -> Icons.Outlined.Shield
    "SHA" -> Icons.Outlined.Fingerprint
    "AB" -> Icons.Outlined.RecordVoiceOver
    "TEST" -> Icons.Outlined.Tune
    else -> Icons.Outlined.Info
}
