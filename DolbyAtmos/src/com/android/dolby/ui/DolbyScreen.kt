/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.ui

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.dolby.DolbyController
import com.android.dolby.DolbyUiState
import com.android.dolby.DolbyViewModel
import com.android.dolby.R
import com.android.dolby.geq.EqualizerActivity
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.drop

private val MEDIA_ATTRS = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DolbyScreen() {
    val ctx = LocalContext.current
    val vm: DolbyViewModel = viewModel {
        DolbyViewModel(
            DolbyController.getInstance(ctx.applicationContext),
            ctx.resources.getBoolean(R.bool.dolby_stereo_widening_supported),
        )
    }
    val s = vm.ui

    // Track speaker / headphones
    DisposableEffect(Unit) {
        val am = ctx.getSystemService(AudioManager::class.java)!!
        fun onSpeaker() =
            am.getDevicesForAttributes(MEDIA_ATTRS).firstOrNull()?.type ==
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        val cb =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(d: Array<AudioDeviceInfo>) =
                    vm.refresh(onSpeaker())

                override fun onAudioDevicesRemoved(d: Array<AudioDeviceInfo>) =
                    vm.refresh(onSpeaker())
            }
        am.registerAudioDeviceCallback(cb, null)
        vm.refresh(onSpeaker())
        onDispose { am.unregisterAudioDeviceCallback(cb) }
    }
    // Pick up changes made elsewhere (QS tile, Graphic EQ screen)
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose {}
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.dolby_title)) },
                actions = {
                    Switch(
                        checked = s.dsOn,
                        onCheckedChange = vm::setEnabled,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            ProfilePager(s, vm)
            Spacer(Modifier.height(16.dp))
            OptionsSection(s, vm)
        }
    }
}

@Composable
private fun ProfilePager(s: DolbyUiState, vm: DolbyViewModel) {
    val names = stringArrayResource(R.array.dolby_profile_entries)
    // Single source of truth: res/values/arrays.xml (dolby_profile_values)
    val profileValues = stringArrayResource(R.array.dolby_profile_values).map { it.toInt() }
    val pager =
        rememberPagerState(
            initialPage = profileValues.indexOf(s.profile).coerceAtLeast(0),
            pageCount = { profileValues.size },
        )

    // Apply profile only after the swipe settles; drop(1) so the initial page
    // never overwrites an unknown profile (-1).
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { vm.setProfile(profileValues[it]) }
    }
    // Profile changed from outside -> follow it
    LaunchedEffect(s.profile) {
        val target = profileValues.indexOf(s.profile)
        if (target >= 0 && target != pager.currentPage) pager.animateScrollToPage(target)
    }

    HorizontalPager(
        state = pager,
        userScrollEnabled = s.dsOn,
        contentPadding = PaddingValues(horizontal = 56.dp),
        pageSpacing = 12.dp,
    ) { page ->
        val offset =
            ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue
        val scale by animateFloatAsState(1f - 0.08f * offset.coerceIn(0f, 1f), label = "scale")
        val selected = page == pager.currentPage && s.dsOn
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().height(150.dp).scale(scale),
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor =
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
        ) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(names[page], style = MaterialTheme.typography.headlineSmall)
                Text("${page + 1} / ${profileValues.size}", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
        repeat(profileValues.size) { i ->
            val on = pager.currentPage == i
            Box(
                Modifier.padding(3.dp)
                    .size(if (on) 10.dp else 6.dp)
                    .background(
                        if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    )
            )
        }
    }
}

@Composable
private fun OptionsSection(s: DolbyUiState, vm: DolbyViewModel) {
    val ctx = LocalContext.current
    val enabled = s.dsOn && s.profile != -1
    val headphonesHint = if (s.isOnSpeaker) stringResource(R.string.dolby_connect_headphones) else null

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionCard {
            ListItem(
                headlineContent = { Text(stringResource(R.string.dolby_preset)) },
                supportingContent = { Text(s.presetName) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier =
                    if (enabled)
                        Modifier.clickable {
                            ctx.startActivity(Intent(ctx, EqualizerActivity::class.java))
                        }
                    else Modifier,
            )
        }

        OptionCard {
            ChoiceRow(
                title = stringResource(R.string.dolby_ieq),
                enabled = enabled,
                labels = stringArrayResource(R.array.dolby_ieq_entries).toList(),
                values = stringArrayResource(R.array.dolby_ieq_values).map { it.toInt() },
                selected = s.ieq,
                onSelect = vm::setIeq,
            )
        }

        OptionCard {
            ChoiceRow(
                title = stringResource(R.string.dolby_dialogue_enhancer),
                enabled = enabled,
                labels = stringArrayResource(R.array.dolby_dialogue_entries).toList(),
                values = stringArrayResource(R.array.dolby_dialogue_values).map { it.toInt() },
                selected = s.dialogue,
                onSelect = vm::setDialogue,
            )
            if (vm.stereoSupported) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                ChoiceRow(
                    title = stringResource(R.string.dolby_stereo_widening),
                    enabled = enabled && !s.isOnSpeaker,
                    supporting = headphonesHint,
                    labels = stringArrayResource(R.array.dolby_stereo_entries).toList(),
                    values = stringArrayResource(R.array.dolby_stereo_values).map { it.toInt() },
                    selected = s.stereo,
                    onSelect = vm::setStereo,
                )
            }
        }

        OptionCard {
            SwitchRow(R.string.dolby_spk_virtualizer, s.spkVirt, enabled, null, vm::setSpkVirt)
            SwitchRow(
                R.string.dolby_hp_virtualizer,
                s.hpVirt,
                enabled && !s.isOnSpeaker,
                headphonesHint,
                vm::setHpVirt,
            )
            SwitchRow(R.string.dolby_bass_enhancer, s.bass, enabled, null, vm::setBass)
            SwitchRow(R.string.dolby_volume_leveler, s.volume, enabled, null, vm::setVolume)
        }

        FilledTonalButton(
            onClick = vm::reset,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        ) {
            Icon(painterResource(R.drawable.reset_settings_24px), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.dolby_reset_profile))
        }
    }
}

@Composable
private fun OptionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp), content = content)
    }
}

@Composable
private fun SwitchRow(
    @StringRes title: Int,
    checked: Boolean,
    enabled: Boolean,
    supporting: String?,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(
    title: String,
    labels: List<String>,
    values: List<Int>,
    selected: Int,
    enabled: Boolean,
    supporting: String? = null,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
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
