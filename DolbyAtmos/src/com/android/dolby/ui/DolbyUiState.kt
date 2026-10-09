/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.ui

import androidx.compose.runtime.Immutable
import com.android.dolby.DolbyConstants.DsTuning

@Immutable
internal data class DolbyUiState(
    val dsOn: Boolean = true,
    val profile: Int = 0,
    val activePort: Int = DsTuning.INTERNAL_SPEAKER.id,
    val headsetModeSelected: Boolean = false,
    val ieq: Int = 0,
    val ieqAmount: Int = 6,
    val dialogueOn: Boolean = false,
    val dialogue: Int = 0,
    val stereo: Int = 32,
    val hpVirt: Boolean = false,
    val spkVirt: Boolean = false,
    val bass: Boolean = false,
    val virtualBass: Boolean = false,
    val volume: Boolean = false,
    val volumeAmount: Int = 0,
    val reverbOn: Boolean = false,
    val reverb: Int = 0,
    val modified: Boolean = false,
    val monoSpeaker: Boolean = false,
    val presetName: String = "",
) {
    val isHeadsetConnected: Boolean
        get() = activePort == DsTuning.HEADPHONE.id ||
            activePort == DsTuning.USB.id ||
            activePort == DsTuning.BLUETOOTH.id

    val isExternalDisplayAudio: Boolean
        get() = activePort == DsTuning.HDMI.id || activePort == DsTuning.MIRACAST.id

    val isDeviceModeMismatch: Boolean
        get() = headsetModeSelected && !isHeadsetConnected

    val effectivePort: Int
        get() = if (!headsetModeSelected) {
            DsTuning.INTERNAL_SPEAKER.id
        } else if (isHeadsetConnected) {
            activePort
        } else {
            DsTuning.HEADPHONE.id
        }
}
