/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.dolby.DolbyConstants
import com.android.dolby.DolbyController
import com.android.dolby.R
import com.android.dolby.geq.ui.EqualizerViewModel

@Composable
internal fun DolbyRoute() {
    val ctx = LocalContext.current
    val vm: DolbyViewModel = viewModel {
        val app = ctx.applicationContext
        DolbyViewModel(
            controller = DolbyController.getInstance(app),
            audioOutput = AudioOutputMonitor(app),
            stereoSupported = app.resources.getBoolean(R.bool.dolby_stereo_widening_supported),
        )
    }
    val eqVm: EqualizerViewModel = viewModel(factory = EqualizerViewModel.Factory)
    val state by vm.ui.collectAsStateWithLifecycle()

    LifecycleResumeEffect(vm) {
        vm.refresh()
        onPauseOrDispose {}
    }

    DisposableEffect(vm, ctx) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = vm.refresh()
            }
        val filter = IntentFilter().apply {
            addAction(DolbyConstants.ACTION_DAP_PARAMS_UPDATE)
            addAction(DolbyConstants.ACTION_AUDIO_SERVER_RESTARTED)
        }
        ctx.registerReceiver(
            receiver,
            filter,
            Context.RECEIVER_EXPORTED,
        )
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    val actions =
        remember(vm) {
            DolbyActions(
                onEnabledChange = vm::setEnabled,
                onProfileChange = vm::setProfile,
                onDeviceModeChange = vm::setDeviceMode,
                onIeqChange = vm::setIeq,
                onIeqAmountChange = vm::setIeqStrength,
                onDialogueEnabledChange = vm::setDialogueOn,
                onDialogueChange = vm::setDialogue,
                onStereoChange = vm::setStereo,
                onHpVirtChange = vm::setHpVirt,
                onSpkVirtChange = vm::setSpkVirt,
                onBassChange = vm::setBass,
                onVirtualBassChange = vm::setVirtualBass,
                onVolumeChange = vm::setVolume,
                onVolumeAmountChange = vm::setVolumeAmount,
                onReverbEnabledChange = vm::setReverbOn,
                onReverbChange = vm::setReverb,
                onReset = vm::reset,
                onResetAll = vm::resetAll,
            )
        }

    DolbyScreen(
        state = state,
        stereoSupported = vm.stereoSupported,
        actions = actions,
        equalizer = { enabled -> EqualizerPanel(eqVm, enabled) },
    )
}
