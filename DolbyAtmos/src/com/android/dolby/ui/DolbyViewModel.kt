/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.dolby.DolbyConstants.DsTuning
import com.android.dolby.DolbyController
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "DolbyViewModel"

internal class DolbyViewModel(
    private val controller: DolbyController,
    audioOutput: AudioOutputMonitor,
    val stereoSupported: Boolean,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val pendingWrites = AtomicInteger(0)

    private val initialPort = audioOutput.currentPort()
    private val initialHeadsetMode = isHeadsetPort(initialPort)
    private val settings = MutableStateFlow(readSettings(initialPort, initialHeadsetMode))

    val ui: StateFlow<DolbyUiState> =
        settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = settings.value,
        )

    init {
        audioOutput.activePort
            .onEach { port ->
                val autoHeadsetMode = isHeadsetPort(port)
                viewModelScope.launch(worker) {
                    settings.value = readOrKeep(port, autoHeadsetMode)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun isHeadsetPort(port: Int): Boolean =
        port == DsTuning.HEADPHONE.id ||
            port == DsTuning.USB.id ||
            port == DsTuning.BLUETOOTH.id

    private fun resolveEffectivePort(activePort: Int, headsetModeSelected: Boolean): Int =
        if (!headsetModeSelected) {
            DsTuning.INTERNAL_SPEAKER.id
        } else if (isHeadsetPort(activePort)) {
            activePort
        } else {
            DsTuning.HEADPHONE.id
        }

    fun refresh() {
        viewModelScope.launch(worker) {
            if (pendingWrites.get() == 0) {
                val port = controller.getActivePort()
                settings.value = readOrKeep(port, settings.value.headsetModeSelected)
            }
        }
    }

    fun setDeviceMode(headsetMode: Boolean) {
        viewModelScope.launch(worker) {
            val port = controller.getActivePort()
            settings.value = readOrKeep(port, headsetMode)
        }
    }

    fun setEnabled(v: Boolean) = change({ it.copy(dsOn = v) }) { setDsOnAndPersist(v) }

    fun setProfile(v: Int) {
        if (v == settings.value.profile) return
        change({ it.copy(profile = v) }) { profile = v }
    }

    fun setIeq(v: Int) {
        val targetPort = settings.value.effectivePort
        change({ it.copy(ieq = v) }) {
            setIeqPreset(v, profile, targetPort)
        }
    }

    fun setIeqStrength(v: Int) = change({ it.copy(ieqAmount = v) }) { setIeqAmount(v) }

    fun setDialogueOn(v: Boolean) =
        change({ it.copy(dialogueOn = v) }) { setDialogueEnhancerEnabled(v) }

    fun setDialogue(v: Int) = change({ it.copy(dialogue = v) }) { setDialogueEnhancerAmount(v) }

    fun setStereo(v: Int) {
        val targetPort = settings.value.effectivePort
        change({ it.copy(stereo = v) }) {
            setStereoWideningAmount(v, profile, targetPort)
        }
    }

    fun setHpVirt(v: Boolean) = change({ it.copy(hpVirt = v) }) { setHeadphoneVirtEnabled(v) }

    fun setSpkVirt(v: Boolean) = change({ it.copy(spkVirt = v) }) { setSpeakerVirtEnabled(v) }

    fun setBass(v: Boolean) = change({ it.copy(bass = v) }) { setBassEnhancerEnabled(v) }

    fun setVirtualBass(v: Boolean) =
        change({ it.copy(virtualBass = v) }) { setVirtualBassEnabled(v) }

    fun setVolume(v: Boolean) = change({ it.copy(volume = v) }) { setVolumeLevelerEnabled(v) }

    fun setVolumeAmount(v: Int) =
        change({ it.copy(volumeAmount = v) }) { setVolumeLevelerAmount(v) }

    fun setReverbOn(v: Boolean) =
        change({ it.copy(reverbOn = v) }) { setReverbReductionEnabled(v) }

    fun setReverb(v: Int) = change({ it.copy(reverb = v) }) { setReverbReductionAmount(v) }

    fun reset() = change { resetProfileSpecificSettings() }

    fun resetAll() = change { resetAllProfiles() }

    private fun readOrKeep(activePort: Int, headsetModeSelected: Boolean): DolbyUiState =
        try {
            readSettings(activePort, headsetModeSelected)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Dolby settings", e)
            settings.value
        }

    private fun change(
        optimistic: (DolbyUiState) -> DolbyUiState = { it },
        write: DolbyController.() -> Unit,
    ) {
        settings.update(optimistic)
        pendingWrites.incrementAndGet()
        viewModelScope.launch(worker) {
            try {
                controller.write()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply Dolby setting", e)
            } finally {
                if (pendingWrites.decrementAndGet() == 0) {
                    val current = settings.value
                    settings.value = readOrKeep(controller.getActivePort(), current.headsetModeSelected)
                }
            }
        }
    }

    private fun readSettings(activePort: Int, headsetModeSelected: Boolean): DolbyUiState {
        val p = controller.profile
        val effectivePort = resolveEffectivePort(activePort, headsetModeSelected)
        return DolbyUiState(
            dsOn = controller.dsOn,
            profile = p,
            activePort = activePort,
            headsetModeSelected = headsetModeSelected,
            ieq = controller.getIeqPreset(p, effectivePort),
            ieqAmount = controller.getIeqAmount(p),
            dialogueOn = controller.getDialogueEnhancerEnabled(p),
            dialogue = controller.getDialogueEnhancerAmount(p),
            stereo = controller.getStereoWideningAmount(p, effectivePort),
            hpVirt = controller.getHeadphoneVirtEnabled(p),
            spkVirt = controller.getSpeakerVirtEnabled(p),
            bass = controller.getBassEnhancerEnabled(p),
            virtualBass = controller.getVirtualBassEnabled(p),
            volume = controller.getVolumeLevelerEnabled(p),
            volumeAmount = controller.getVolumeLevelerAmount(p),
            reverbOn = controller.getReverbReductionEnabled(p),
            reverb = controller.getReverbReductionAmount(p),
            modified = controller.isProfileModified(p),
            monoSpeaker = controller.monoSpeaker,
            presetName = controller.getPresetName(),
        )
    }
}
