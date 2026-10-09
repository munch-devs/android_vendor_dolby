/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.ui

import android.R as AndroidR
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.dolby.R
import kotlin.math.roundToInt

@Immutable
internal class Choices(val labels: List<String>, val values: List<Int>) {
    val size: Int
        get() = minOf(labels.size, values.size)

    fun indexOf(value: Int): Int = values.indexOf(value).coerceAtLeast(0)
}

@Composable
internal fun rememberChoices(@ArrayRes labels: Int, @ArrayRes values: Int): Choices {
    val res = LocalContext.current.resources
    val config = LocalConfiguration.current
    return remember(labels, values, config) {
        Choices(res.getStringArray(labels).toList(), res.getStringArray(values).map { it.toInt() })
    }
}

@Composable
internal fun SwitchWithCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        } else null
    )
}

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
    icon: ImageVector? = null,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = { SwitchWithCheck(checked = checked, onCheckedChange = onCheckedChange) },
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

@Composable
internal fun IntSliderRow(
    title: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    supporting: String? = null,
    icon: ImageVector? = null,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SliderLine(
            value = value,
            range = range,
            enabled = enabled,
            onSelect = onSelect,
            modifier = Modifier.padding(start = if (icon != null) 40.dp else 0.dp),
        )
    }
}

@Composable
internal fun ToggleSliderRow(
    @StringRes title: Int,
    checked: Boolean,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    supporting: String?,
    onCheckedChange: (Boolean) -> Unit,
    onValueChange: (Int) -> Unit,
    icon: ImageVector? = null,
) {
    Column {
        SwitchRow(title, checked, enabled, supporting, onCheckedChange, icon)
        SliderLine(
            value = value,
            range = range,
            enabled = enabled && checked,
            onSelect = onValueChange,
            modifier =
                Modifier.padding(start = if (icon != null) 56.dp else 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun SliderLine(
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var position by remember(value) { mutableFloatStateOf(value.coerceIn(range).toFloat()) }
    val current = position.roundToInt().coerceIn(range)
    val currentText = if (current == 0) stringResource(R.string.dolby_off) else current.toString()
    val discrete = range.last - range.first <= 16
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = position,
            onValueChange = { position = if (discrete) it else it.roundToInt().toFloat() },
            onValueChangeFinished = {
                val picked = position.roundToInt().coerceIn(range)
                if (picked != value) onSelect(picked)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (discrete) range.last - range.first - 1 else 0,
            enabled = enabled,
            modifier = Modifier.weight(1f).semantics { stateDescription = currentText },
        )
        Text(
            currentText,
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            color =
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp),
        )
    }
}

@Composable
internal fun OptionTile(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 40.dp,
) {
    val colors = MaterialTheme.colorScheme
    val container by
        animateColorAsState(
            if (selected) colors.primaryContainer else colors.surfaceContainerHighest,
            label = "tileContainer",
        )
    val content = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(iconSize))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

@Immutable internal data class TabItem(@StringRes val label: Int, val icon: ImageVector)

@Composable
internal fun FloatingTabBar(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(6.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, tab ->
                val selected = i == selectedIndex
                val bg by
                    animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        label = "tabBg",
                    )
                val fg =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                val label = stringResource(tab.label)
                Row(
                    Modifier.animateContentSize()
                        .clip(CircleShape)
                        .background(bg)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(i) })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = if (selected) null else label,
                        tint = fg,
                        modifier = Modifier.size(24.dp),
                    )
                    AnimatedVisibility(selected) {
                        Text(
                            label,
                            color = fg,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    text: String? = null,
    icon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(AndroidR.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(AndroidR.string.cancel)) }
        },
    )
}
