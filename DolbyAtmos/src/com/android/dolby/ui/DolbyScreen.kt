/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.dolby.R

/** All user intents of the screen. Defaults make previews/tests trivial. */
@Immutable
internal data class DolbyActions(
    val onEnabledChange: (Boolean) -> Unit = {},
    val onProfileChange: (Int) -> Unit = {},
    val onIeqChange: (Int) -> Unit = {},
    val onDialogueChange: (Int) -> Unit = {},
    val onStereoChange: (Int) -> Unit = {},
    val onHpVirtChange: (Boolean) -> Unit = {},
    val onSpkVirtChange: (Boolean) -> Unit = {},
    val onBassChange: (Boolean) -> Unit = {},
    val onVolumeChange: (Boolean) -> Unit = {},
    val onReset: () -> Unit = {},
    val onOpenEqualizer: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DolbyScreen(
    state: DolbyUiState,
    stereoSupported: Boolean,
    actions: DolbyActions,
) {
    val direction = LocalLayoutDirection.current
    var showResetDialog by remember { mutableStateOf(false) }

    val profileNames = stringArrayResource(R.array.dolby_profile_entries)
    val profileValues = intsResource(R.array.dolby_profile_values)
    val subtitle = profileNames.getOrNull(profileValues.indexOf(state.profile)) ?: state.presetName
    val enabled = state.dsOn && state.profile != -1

    // Dolby off -> everything below the main card is dimmed (and disabled), not hidden.
    val contentAlpha by animateFloatAsState(if (state.dsOn) 1f else 0.5f, label = "contentAlpha")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.dolby_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (subtitle.isNotEmpty()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                expandedHeight = 92.dp,
                actions = {
                    IconButton(onClick = { showResetDialog = true }, enabled = enabled) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = stringResource(R.string.dolby_reset_profile),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { pad ->
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .padding(
                        top = pad.calculateTopPadding(),
                        start = pad.calculateStartPadding(direction),
                        end = pad.calculateEndPadding(direction),
                    ),
            contentPadding =
                PaddingValues(top = 8.dp, bottom = 16.dp + pad.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "main") {
                DolbyMainCard(
                    title = stringResource(R.string.dolby_title),
                    enabled = state.dsOn,
                    onEnabledChange = actions.onEnabledChange,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "content") {
                Column(
                    modifier = Modifier.graphicsLayer { alpha = contentAlpha },
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ProfileSelector(
                        profile = state.profile,
                        names = profileNames,
                        values = profileValues,
                        enabled = state.dsOn,
                        onProfileChange = actions.onProfileChange,
                    )
                    OptionsSection(state, enabled, stereoSupported, actions)
                }
            }
        }
    }

    if (showResetDialog) {
        ConfirmDialog(
            title = stringResource(R.string.dolby_reset_profile),
            icon = Icons.Default.RestartAlt,
            onConfirm = {
                actions.onReset()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false },
        )
    }
}

/** Profile tiles in two columns; the selected one is highlighted. */
@Composable
private fun ProfileSelector(
    profile: Int,
    names: Array<String>,
    values: List<Int>,
    enabled: Boolean,
    onProfileChange: (Int) -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (0 until minOf(names.size, values.size)).chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { i ->
                    ProfileTile(
                        name = names[i],
                        selected = values[i] == profile,
                        enabled = enabled,
                        onClick = { onProfileChange(values[i]) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProfileTile(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.primaryContainer else colors.surfaceContainerHigh
    val content = if (selected) colors.onPrimaryContainer else colors.onSurface
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = container,
                contentColor = content,
                // Dimming is done once, on the whole section.
                disabledContainerColor = container,
                disabledContentColor = content,
            ),
        modifier = modifier,
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (selected) Icon(Icons.Default.Check, contentDescription = null, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun OptionsSection(
    state: DolbyUiState,
    enabled: Boolean,
    stereoSupported: Boolean,
    actions: DolbyActions,
) {
    val headphonesOnly = enabled && !state.isOnSpeaker
    val headphonesHint =
        if (state.isOnSpeaker) stringResource(R.string.dolby_connect_headphones) else null

    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard {
            ListItem(
                leadingContent = { Icon(Icons.Default.Equalizer, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.dolby_preset)) },
                supportingContent = { Text(state.presetName) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(enabled = enabled, onClick = actions.onOpenEqualizer),
            )
        }

        SettingsCard(title = stringResource(R.string.dolby_ieq), icon = Icons.Default.GraphicEq) {
            ChoiceRow(
                enabled = enabled,
                labels = stringArrayResource(R.array.dolby_ieq_entries).toList(),
                values = intsResource(R.array.dolby_ieq_values),
                selected = state.ieq,
                onSelect = actions.onIeqChange,
            )
        }

        SettingsCard {
            ChoiceRow(
                title = stringResource(R.string.dolby_dialogue_enhancer),
                enabled = enabled,
                labels = stringArrayResource(R.array.dolby_dialogue_entries).toList(),
                values = intsResource(R.array.dolby_dialogue_values),
                selected = state.dialogue,
                onSelect = actions.onDialogueChange,
            )
            if (stereoSupported) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                ChoiceRow(
                    title = stringResource(R.string.dolby_stereo_widening),
                    enabled = headphonesOnly,
                    supporting = headphonesHint,
                    labels = stringArrayResource(R.array.dolby_stereo_entries).toList(),
                    values = intsResource(R.array.dolby_stereo_values),
                    selected = state.stereo,
                    onSelect = actions.onStereoChange,
                )
            }
        }

        SettingsCard {
            SwitchRow(
                R.string.dolby_spk_virtualizer,
                state.spkVirt,
                enabled,
                null,
                actions.onSpkVirtChange,
            )
            SwitchRow(
                R.string.dolby_hp_virtualizer,
                state.hpVirt,
                headphonesOnly,
                headphonesHint,
                actions.onHpVirtChange,
            )
            SwitchRow(R.string.dolby_bass_enhancer, state.bass, enabled, null, actions.onBassChange)
            SwitchRow(
                R.string.dolby_volume_leveler,
                state.volume,
                enabled,
                null,
                actions.onVolumeChange,
            )
        }
    }
}
