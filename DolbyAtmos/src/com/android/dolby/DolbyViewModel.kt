/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class DolbyUiState(
    val dsOn: Boolean = true,
    val profile: Int = 0,
    val isOnSpeaker: Boolean = true,
    val ieq: Int = 0,
    val dialogue: Int = 0,
    val stereo: Int = 0,
    val hpVirt: Boolean = false,
    val spkVirt: Boolean = false,
    val bass: Boolean = false,
    val volume: Boolean = false,
    val presetName: String = "",
)

internal class DolbyViewModel(
    private val controller: DolbyController,
    val stereoSupported: Boolean,
) : ViewModel() {

    var ui by mutableStateOf(DolbyUiState())
        private set

    init {
        // State must be correct from the first frame, otherwise the pager would
        // overwrite the real profile with page 0.
        refresh()
    }

    fun refresh(isOnSpeaker: Boolean = ui.isOnSpeaker) {
        val p = controller.profile
        ui =
            DolbyUiState(
                dsOn = controller.dsOn,
                profile = p,
                isOnSpeaker = isOnSpeaker,
                ieq = controller.getIeqPreset(p),
                dialogue = controller.getDialogueEnhancerAmount(p),
                stereo = controller.getStereoWideningAmount(p),
                hpVirt = controller.getHeadphoneVirtEnabled(p),
                spkVirt = controller.getSpeakerVirtEnabled(p),
                bass = controller.getBassEnhancerEnabled(p),
                volume = controller.getVolumeLevelerEnabled(p),
                presetName = controller.getPresetName(),
            )
    }

    fun setEnabled(v: Boolean) {
        controller.dsOn = v
        refresh()
    }

    fun setProfile(v: Int) {
        if (v == controller.profile) return
        controller.profile = v
        refresh()
    }

    fun setIeq(v: Int) = apply { controller.setIeqPreset(v) }.refresh()

    fun setDialogue(v: Int) = apply { controller.setDialogueEnhancerAmount(v) }.refresh()

    fun setStereo(v: Int) = apply { controller.setStereoWideningAmount(v) }.refresh()

    fun setHpVirt(v: Boolean) = apply { controller.setHeadphoneVirtEnabled(v) }.refresh()

    fun setSpkVirt(v: Boolean) = apply { controller.setSpeakerVirtEnabled(v) }.refresh()

    fun setBass(v: Boolean) = apply { controller.setBassEnhancerEnabled(v) }.refresh()

    fun setVolume(v: Boolean) = apply { controller.setVolumeLevelerEnabled(v) }.refresh()

    fun reset() = apply { controller.resetProfileSpecificSettings() }.refresh()
}
