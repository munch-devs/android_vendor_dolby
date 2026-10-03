/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
)

private const val TAB_PROFILE = 0
private const val TAB_EQUALIZER = 1
private const val TAB_EFFECTS = 2

private val Tabs =
    listOf(
        TabItem(R.string.dolby_tab_profile, Icons.Default.Style),
        TabItem(R.string.dolby_tab_equalizer, Icons.Default.Equalizer),
        TabItem(R.string.dolby_tab_effects, Icons.Default.SurroundSound),
    )

// Space reserved under scrolling content so the floating bar never hides the last item.
private val FloatingBarClearance = 96.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DolbyScreen(
    state: DolbyUiState,
    stereoSupported: Boolean,
    actions: DolbyActions,
    equalizer: @Composable (enabled: Boolean) -> Unit,
) {
    val direction = LocalLayoutDirection.current
    var showResetDialog by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(TAB_PROFILE) }

    val profiles = rememberChoices(R.array.dolby_profile_entries, R.array.dolby_profile_values)
    val profileName = profiles.labels.getOrNull(profiles.values.indexOf(state.profile)) ?: state.presetName
    // The single place that says "Dolby Atmos"; the subtitle carries the live state.
    val subtitle = if (state.dsOn) profileName else stringResource(R.string.dolby_off)
    val canReset = state.dsOn && state.profile != -1

    // Dolby off -> pages are dimmed (and disabled), not hidden; the tab bar stays usable.
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
                    // The Equalizer page has its own reset (resets gains, not the profile).
                    if (tab != TAB_EQUALIZER) {
                        IconButton(onClick = { showResetDialog = true }, enabled = canReset) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = stringResource(R.string.dolby_reset_profile),
                            )
                        }
                    }
                    Switch(
                        checked = state.dsOn,
                        onCheckedChange = actions.onEnabledChange,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            FloatingTabBar(tabs = Tabs, selectedIndex = tab, onSelect = { tab = it })
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { pad ->
        // Not swipeable between tabs on purpose: the profile carousel owns horizontal drags.
        Crossfade(
            targetState = tab,
            label = "tab",
            modifier =
                Modifier.fillMaxSize()
                    .padding(
                        top = pad.calculateTopPadding(),
                        start = pad.calculateStartPadding(direction),
                        end = pad.calculateEndPadding(direction),
                    )
                    .graphicsLayer { alpha = contentAlpha },
        ) { current ->
            Page(bottomPadding = pad.calculateBottomPadding() + FloatingBarClearance) {
                when (current) {
                    TAB_PROFILE -> ProfilePage(state, enabled = canReset, actions)
                    TAB_EQUALIZER ->
                        Column(Modifier.padding(horizontal = 16.dp)) { equalizer(canReset) }
                    TAB_EFFECTS -> EffectsPage(state, enabled = canReset, stereoSupported, actions)
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

@Composable
private fun Page(bottomPadding: Dp, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

/** Profile flashcards + the intelligent EQ (one row of four, since it's a quick either/or). */
@Composable
private fun ProfilePage(state: DolbyUiState, enabled: Boolean, actions: DolbyActions) {
    val ieq = rememberChoices(R.array.dolby_ieq_entries, R.array.dolby_ieq_values)

    ProfileCarousel(
        profile = state.profile,
        enabled = state.dsOn,
        onProfileChange = actions.onProfileChange,
    )
    Column(Modifier.padding(horizontal = 16.dp)) {
        SettingsCard(title = stringResource(R.string.dolby_ieq), icon = Icons.Default.GraphicEq) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(ieq.size) { i ->
                    OptionTile(
                        label = ieq.labels[i],
                        icon = ieqIcon(ieq.values[i]),
                        selected = ieq.values[i] == state.ieq,
                        enabled = enabled,
                        onClick = { actions.onIeqChange(ieq.values[i]) },
                        modifier = Modifier.weight(1f),
                        iconSize = 32.dp,
                    )
                }
            }
        }
    }
}

/** Everything else: dialogue / stereo as stepped sliders, then the four toggles. */
@Composable
private fun EffectsPage(
    state: DolbyUiState,
    enabled: Boolean,
    stereoSupported: Boolean,
    actions: DolbyActions,
) {
    val dialogue = rememberChoices(R.array.dolby_dialogue_entries, R.array.dolby_dialogue_values)
    val stereo = rememberChoices(R.array.dolby_stereo_entries, R.array.dolby_stereo_values)
    val headphonesOnly = enabled && !state.isOnSpeaker
    val headphonesHint = if (state.isOnSpeaker) stringResource(R.string.dolby_connect_headphones) else null

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard {
            StepSliderRow(
                title = stringResource(R.string.dolby_dialogue_enhancer),
                choices = dialogue,
                selected = state.dialogue,
                enabled = enabled,
                onSelect = actions.onDialogueChange,
            )
            if (stereoSupported) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp, horizontal = 16.dp))
                StepSliderRow(
                    title = stringResource(R.string.dolby_stereo_widening),
                    choices = stereo,
                    selected = state.stereo,
                    enabled = headphonesOnly,
                    supporting = headphonesHint,
                    onSelect = actions.onStereoChange,
                )
            }
        }
        SettingsCard {
            SwitchRow(R.string.dolby_spk_virtualizer, state.spkVirt, enabled, null, actions.onSpkVirtChange)
            SwitchRow(R.string.dolby_hp_virtualizer, state.hpVirt, headphonesOnly, headphonesHint, actions.onHpVirtChange)
            SwitchRow(R.string.dolby_bass_enhancer, state.bass, enabled, null, actions.onBassChange)
            SwitchRow(R.string.dolby_volume_leveler, state.volume, enabled, null, actions.onVolumeChange)
        }
    }
}

/** IEQ value (see dolby_ieq_values) -> existing drawable. */
private fun ieqIcon(value: Int): Int =
    when (value) {
        1 -> R.drawable.ic_ieq_balanced
        2 -> R.drawable.ic_ieq_warm
        3 -> R.drawable.ic_ieq_detailed
        else -> R.drawable.ic_ieq_off
    }
