/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.ui

import android.R as AndroidR
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** String-array resource whose items are integers (the *_values arrays). */
@Composable
internal fun intsResource(@ArrayRes id: Int): List<Int> =
    stringArrayResource(id).map { it.toInt() }

/** Big on/off card at the top of the screen. */
@Composable
internal fun DolbyMainCard(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container by
        animateColorAsState(
            if (enabled) colors.primaryContainer else colors.surfaceContainerHigh,
            label = "mainCardContainer",
        )
    Card(
        onClick = { onEnabledChange(!enabled) },
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = container,
                contentColor = if (enabled) colors.onPrimaryContainer else colors.onSurface,
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

/** Rounded surface card that groups related rows, with an optional icon + title header. */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            if (title != null) {
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            content()
        }
    }
}

@Composable
internal fun SwitchRow(
    @StringRes title: Int,
    checked: Boolean,
    enabled: Boolean,
    supporting: String?,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = supporting?.let { { Text(it) } },
        // The whole row toggles; the Switch itself is display-only (better for TalkBack).
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier =
            Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoiceRow(
    labels: List<String>,
    values: List<Int>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    title: String? = null,
    supporting: String? = null,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.bodyLarge)
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (title != null || supporting != null) Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = values[i] == selected,
                    onClick = { onSelect(values[i]) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                ) {
                    Text(label, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    icon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(AndroidR.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(AndroidR.string.cancel)) }
        },
    )
}
