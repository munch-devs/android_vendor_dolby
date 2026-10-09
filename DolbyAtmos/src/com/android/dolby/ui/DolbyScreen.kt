/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NoiseAware
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.dolby.DolbyConstants
import com.android.dolby.DolbyConstants.DsTuning
import com.android.dolby.R

@Immutable
internal data class DolbyActions(
    val onEnabledChange: (Boolean) -> Unit = {},
    val onProfileChange: (Int) -> Unit = {},
    val onIeqChange: (Int) -> Unit = {},
    val onIeqAmountChange: (Int) -> Unit = {},
    val onDialogueEnabledChange: (Boolean) -> Unit = {},
    val onDialogueChange: (Int) -> Unit = {},
    val onStereoChange: (Int) -> Unit = {},
    val onHpVirtChange: (Boolean) -> Unit = {},
    val onSpkVirtChange: (Boolean) -> Unit = {},
    val onBassChange: (Boolean) -> Unit = {},
    val onVolumeChange: (Boolean) -> Unit = {},
    val onVolumeAmountChange: (Int) -> Unit = {},
    val onReverbEnabledChange: (Boolean) -> Unit = {},
    val onReverbChange: (Int) -> Unit = {},
    val onGeqBandChange: (Int, Float) -> Unit = { _, _ -> },
    val onReset: () -> Unit = {},
    val onResetAll: () -> Unit = {},
)

private const val TAB_HOME = 0
private const val TAB_EQUALIZER = 1
private const val TAB_ADVANCED = 2

private val Tabs =
    listOf(
        TabItem(R.string.dolby_tab_home, Icons.Default.Home),
        TabItem(R.string.dolby_tab_equalizer, Icons.Default.Equalizer),
        TabItem(R.string.dolby_tab_advanced, Icons.Default.Settings),
    )

private val DIALOGUE_RANGE = 1..DolbyConstants.DIALOGUE_AMOUNT_RANGE.last
private val STEREO_RANGE = DolbyConstants.STEREO_WIDENING_RANGE
private val IEQ_RANGE = 1..DolbyConstants.IEQ_AMOUNT_RANGE.last
private val VOLUME_RANGE = 1..DolbyConstants.VOLUME_LEVELER_AMOUNT_RANGE.last
private val REVERB_RANGE = 1..DolbyConstants.REVERB_REDUCTION_AMOUNT_RANGE.last

private enum class ModalDialog {
    RESET_PROFILE,
    RESET_ALL,
    PROFILE_HELP,
    EXPLORE_DOLBY,
}

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
    val context = LocalContext.current

    var modalDialog by remember { mutableStateOf<ModalDialog?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }

    val profiles = rememberChoices(R.array.dolby_profile_entries, R.array.dolby_profile_values)
    val profileName = profiles.labels.getOrNull(profiles.values.indexOf(state.profile)) ?: state.presetName

    val heroSummary =
        when {
            !state.dsOn -> stringResource(R.string.dolby_off)
            profileName.isNotEmpty() -> stringResource(R.string.dolby_on_with_profile_and_device, profileName, state.activeDeviceName)
            else -> stringResource(R.string.dolby_on)
        }

    val canReset = state.dsOn && state.profile != -1 && !state.isExternalDisplayAudio
    val contentAlpha by animateFloatAsState(if (canReset) 1f else 0.5f, label = "contentAlpha")

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val deviceIcon = when (state.activePort) {
                                DsTuning.BLUETOOTH.id -> Icons.Default.Bluetooth
                                DsTuning.HEADPHONE.id -> Icons.Default.Headphones
                                DsTuning.USB.id -> Icons.Default.Cable
                                DsTuning.HDMI.id, DsTuning.MIRACAST.id -> Icons.Default.Tv
                                else -> Icons.Default.Speaker
                            }
                            Icon(
                                deviceIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                state.activeDeviceName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                expandedHeight = 92.dp,
                actions = {
                    if (state.profile != DolbyConstants.PROFILE_DYNAMIC) {
                        IconButton(
                            onClick = { modalDialog = ModalDialog.PROFILE_HELP },
                            enabled = canReset,
                        ) {
                            Icon(
                                Icons.Default.HelpOutline,
                                contentDescription = stringResource(R.string.dolby_help_title),
                            )
                        }
                    }
                    if (tab != TAB_EQUALIZER) {
                        Box(Modifier.padding(end = 8.dp)) {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.dolby_more_options),
                                )
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.dolby_explore_title)) },
                                    leadingIcon = { Icon(Icons.Default.Explore, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        modalDialog = ModalDialog.EXPLORE_DOLBY
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.dolby_reset_profile)) },
                                    leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                                    enabled = canReset && state.modified,
                                    onClick = {
                                        showMenu = false
                                        modalDialog = ModalDialog.RESET_PROFILE
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.dolby_reset_all)) },
                                    leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        modalDialog = ModalDialog.RESET_ALL
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            if (!state.isExternalDisplayAudio) {
                FloatingTabBar(tabs = Tabs, selectedIndex = tab, onSelect = { tab = it })
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { pad ->
        if (state.isExternalDisplayAudio) {
            Page(
                bottomPadding = pad.calculateBottomPadding() + 24.dp,
                modifier =
                    Modifier.padding(
                        top = pad.calculateTopPadding(),
                        start = pad.calculateStartPadding(direction) + 16.dp,
                        end = pad.calculateEndPadding(direction) + 16.dp,
                    ),
            ) {
                val isMiracast = state.activePort == DsTuning.MIRACAST.id
                SettingsCard(
                    title =
                        stringResource(
                            if (isMiracast) R.string.dolby_miracast_connected_title
                            else R.string.dolby_hdmi_connected_title
                        ),
                    icon = Icons.Default.Tv,
                ) {
                    Text(
                        stringResource(
                            if (isMiracast) R.string.dolby_miracast_connected_desc
                            else R.string.dolby_hdmi_connected_desc
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        } else {
            Crossfade(
                targetState = tab,
                label = "tab",
                modifier =
                    Modifier.fillMaxSize()
                        .padding(
                            top = pad.calculateTopPadding(),
                            start = pad.calculateStartPadding(direction),
                            end = pad.calculateEndPadding(direction),
                        ),
            ) { current ->
                Page(bottomPadding = pad.calculateBottomPadding() + FloatingBarClearance) {
                    when (current) {
                        TAB_HOME ->
                            HomePage(
                                state = state,
                                enabled = canReset,
                                heroSummary = heroSummary,
                                contentAlpha = contentAlpha,
                                actions = actions,
                            )
                        TAB_EQUALIZER ->
                            Column(
                                Modifier.padding(horizontal = 16.dp).graphicsLayer { alpha = contentAlpha }
                            ) {
                                equalizer(canReset)
                            }
                        TAB_ADVANCED ->
                            Column(Modifier.graphicsLayer { alpha = contentAlpha }) {
                                AdvancedPage(
                                    state = state,
                                    enabled = canReset,
                                    stereoSupported = stereoSupported,
                                    actions = actions,
                                )
                            }
                    }
                }
            }
        }
    }

    when (modalDialog) {
        ModalDialog.RESET_PROFILE ->
            ConfirmDialog(
                title = stringResource(R.string.dolby_reset_profile),
                text = stringResource(R.string.dolby_reset_profile_desc),
                icon = Icons.Default.RestartAlt,
                onConfirm = {
                    actions.onReset()
                    modalDialog = null
                },
                onDismiss = { modalDialog = null },
            )
        ModalDialog.RESET_ALL ->
            ConfirmDialog(
                title = stringResource(R.string.dolby_reset_all),
                text = stringResource(R.string.dolby_reset_all_desc),
                icon = Icons.Default.Restore,
                onConfirm = {
                    actions.onResetAll()
                    modalDialog = null
                },
                onDismiss = { modalDialog = null },
            )
        ModalDialog.PROFILE_HELP ->
            ProfileHelpDialog(
                profile = state.profile,
                onDismiss = { modalDialog = null },
            )
        ModalDialog.EXPLORE_DOLBY ->
            AlertDialog(
                onDismissRequest = { modalDialog = null },
                icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                title = { Text(stringResource(R.string.dolby_explore_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.dolby_explore_desc))
                        TextButton(
                            onClick = {
                                val intent =
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("http://www.dolby.com/us/en/technologies/dolby-atmos.html"),
                                    )
                                context.startActivity(intent)
                            }
                        ) {
                            Text(stringResource(R.string.dolby_learn_more_web))
                        }
                        TextButton(
                            onClick = {
                                val intent =
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("http://www.dolby.com/androidapps"),
                                    )
                                context.startActivity(intent)
                            }
                        ) {
                            Text(stringResource(R.string.dolby_access_apps))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { modalDialog = null }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            )
        null -> {}
    }
}

@Composable
private fun ProfileHelpDialog(profile: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.HelpOutline, contentDescription = null) },
        title = { Text(stringResource(R.string.dolby_help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (profile == DolbyConstants.PROFILE_MUSIC || profile == DolbyConstants.PROFILE_CUSTOM) {
                    HelpSection(
                        title = stringResource(R.string.dolby_ieq),
                        body = stringResource(R.string.dolby_help_ieq_desc),
                    )
                    HorizontalDivider()
                }
                if (profile == DolbyConstants.PROFILE_CUSTOM) {
                    HelpSection(
                        title = stringResource(R.string.dolby_hp_virtualizer),
                        body = stringResource(R.string.dolby_help_stereo_widening_desc),
                    )
                    HorizontalDivider()
                    HelpSection(
                        title = stringResource(R.string.dolby_reverb_reduction),
                        body = stringResource(R.string.dolby_help_reverb_reduction_desc),
                    )
                    HorizontalDivider()
                }
                if (profile == DolbyConstants.PROFILE_MOVIE || profile == DolbyConstants.PROFILE_CUSTOM) {
                    HelpSection(
                        title = stringResource(R.string.dolby_dialogue_enhancer),
                        body = stringResource(R.string.dolby_help_dialogue_enhancer_desc),
                    )
                }
                if (profile == DolbyConstants.PROFILE_MUSIC || profile == DolbyConstants.PROFILE_CUSTOM) {
                    if (profile == DolbyConstants.PROFILE_CUSTOM) HorizontalDivider()
                    HelpSection(
                        title = stringResource(R.string.dolby_preset),
                        body = stringResource(R.string.dolby_help_geq_desc),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Page(bottomPadding: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

@Composable
private fun HomePage(
    state: DolbyUiState,
    enabled: Boolean,
    heroSummary: String,
    contentAlpha: Float,
    actions: DolbyActions,
) {
    val ieq = rememberChoices(R.array.dolby_ieq_entries, R.array.dolby_ieq_values)

    Column(Modifier.padding(horizontal = 16.dp)) {
        HeroCard(on = state.dsOn, summary = heroSummary, onChange = actions.onEnabledChange)
    }

    Column(
        Modifier.graphicsLayer { alpha = contentAlpha },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SettingsCard(title = stringResource(R.string.dolby_profile_title), icon = Icons.Default.Style) {
                ProfileCarousel(
                    profile = state.profile,
                    enabled = state.dsOn,
                    onProfileChange = actions.onProfileChange,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
            }
        }

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
                IntSliderRow(
                    title = stringResource(R.string.dolby_ieq_amount),
                    value = state.ieqAmount,
                    range = IEQ_RANGE,
                    enabled = enabled && state.ieq != 0,
                    onSelect = actions.onIeqAmountChange,
                    icon = Icons.Default.Tune,
                )
            }
        }
    }
}

@Composable
private fun HeroCard(on: Boolean, summary: String, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val containerColor by
        animateColorAsState(
            targetValue = if (on) colors.primaryContainer else colors.surfaceContainerHigh,
            label = "heroContainer",
        )
    val contentColor by
        animateColorAsState(
            targetValue = if (on) colors.onPrimaryContainer else colors.onSurface,
            label = "heroContent",
        )
    val supportingColor by
        animateColorAsState(
            targetValue = if (on) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
            label = "heroSupporting",
        )

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.dolby_enable),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
            },
            supportingContent = {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = supportingColor,
                )
            },
            trailingContent = { SwitchWithCheck(checked = on, onCheckedChange = onChange) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier =
                Modifier.toggleable(value = on, role = Role.Switch, onValueChange = onChange)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AdvancedPage(
    state: DolbyUiState,
    enabled: Boolean,
    stereoSupported: Boolean,
    actions: DolbyActions,
) {
    val headphonesEditable = enabled && state.isHeadsetConnected
    val hpDescription =
        if (state.isHeadsetConnected) stringResource(R.string.dolby_hp_virtualizer_desc)
        else stringResource(R.string.dolby_connect_headphones)

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard(
            title = stringResource(R.string.dolby_surround_virtualizer),
            icon = Icons.Default.SurroundSound,
        ) {
            if (!state.monoSpeaker) {
                SwitchRow(
                    R.string.dolby_spk_virtualizer,
                    state.spkVirt,
                    enabled && !state.isHeadsetConnected,
                    stringResource(R.string.dolby_spk_virtualizer_desc),
                    actions.onSpkVirtChange,
                    icon = Icons.Default.Speaker,
                )
            }
            if (stereoSupported) {
                ToggleSliderRow(
                    title = R.string.dolby_hp_virtualizer,
                    checked = state.hpVirt,
                    value = state.stereo,
                    range = STEREO_RANGE,
                    enabled = headphonesEditable,
                    supporting = hpDescription,
                    onCheckedChange = actions.onHpVirtChange,
                    onValueChange = actions.onStereoChange,
                    icon = Icons.Default.Headphones,
                )
            } else {
                SwitchRow(
                    R.string.dolby_hp_virtualizer,
                    state.hpVirt,
                    headphonesEditable,
                    hpDescription,
                    actions.onHpVirtChange,
                    icon = Icons.Default.Headphones,
                )
            }
        }

        SettingsCard(title = stringResource(R.string.dolby_audio_tuning), icon = Icons.Default.Tune) {
            SwitchRow(
                R.string.dolby_bass_enhancer,
                state.bass,
                enabled,
                stringResource(R.string.dolby_bass_enhancer_desc),
                actions.onBassChange,
                icon = Icons.Default.MusicNote,
            )
            ToggleSliderRow(
                title = R.string.dolby_dialogue_enhancer,
                checked = state.dialogueOn,
                value = state.dialogue,
                range = DIALOGUE_RANGE,
                enabled = enabled,
                supporting = stringResource(R.string.dolby_dialogue_enhancer_desc),
                onCheckedChange = actions.onDialogueEnabledChange,
                onValueChange = actions.onDialogueChange,
                icon = Icons.Default.RecordVoiceOver,
            )
            ToggleSliderRow(
                title = R.string.dolby_reverb_reduction,
                checked = state.reverbOn,
                value = state.reverb,
                range = REVERB_RANGE,
                enabled = enabled,
                supporting = stringResource(R.string.dolby_reverb_reduction_desc),
                onCheckedChange = actions.onReverbEnabledChange,
                onValueChange = actions.onReverbChange,
                icon = Icons.Default.NoiseAware,
            )
        }

        SettingsCard(title = stringResource(R.string.dolby_volume_leveler), icon = Icons.Default.VolumeUp) {
            ToggleSliderRow(
                title = R.string.dolby_volume_leveler,
                checked = state.volume,
                value = state.volumeAmount,
                range = VOLUME_RANGE,
                enabled = enabled,
                supporting = stringResource(R.string.dolby_volume_leveler_desc),
                onCheckedChange = actions.onVolumeChange,
                onValueChange = actions.onVolumeAmountChange,
                icon = Icons.Default.BarChart,
            )
        }
    }
}

private fun ieqIcon(value: Int): Int =
    when (value) {
        1 -> R.drawable.ic_ieq_detailed
        2 -> R.drawable.ic_ieq_balanced
        3 -> R.drawable.ic_ieq_warm
        else -> R.drawable.ic_ieq_off
    }
