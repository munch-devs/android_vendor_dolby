/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val initialDevice = audioOutput.currentDeviceState()
    private val settings = MutableStateFlow(readSettings(initialDevice))

    val ui: StateFlow<DolbyUiState> =
        settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = settings.value,
        )

    init {
        audioOutput.activeDevice
            .onEach { deviceState ->
                viewModelScope.launch(worker) {
                    settings.value = readOrKeep(deviceState)
                }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch(worker) {
            if (pendingWrites.get() == 0) {
                val dev = audioOutput.currentDeviceState()
                settings.value = readOrKeep(dev)
            }
        }
    }

    fun setEnabled(v: Boolean) = change({ it.copy(dsOn = v) }) { setDsOnAndPersist(v) }

    fun setProfile(v: Int) {
        if (v == settings.value.profile) return
        change({ it.copy(profile = v) }) { profile = v }
    }

    fun setIeq(v: Int) {
        val s = settings.value
        change({ it.copy(ieq = v) }) {
            setIeqPreset(v, profile, s.activePort, s.activeDeviceKey)
        }
    }

    fun setIeqStrength(v: Int) = change({ it.copy(ieqAmount = v) }) { setIeqAmount(v) }

    fun setDialogueOn(v: Boolean) =
        change({ it.copy(dialogueOn = v) }) { setDialogueEnhancerEnabled(v) }

    fun setDialogue(v: Int) = change({ it.copy(dialogue = v) }) { setDialogueEnhancerAmount(v) }

    fun setStereo(v: Int) {
        val s = settings.value
        change({ it.copy(stereo = v) }) {
            setStereoWideningAmount(v, profile, s.activePort, s.activeDeviceKey)
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

    fun setGeqBand(index: Int, gainDb: Float) =
        change({ state ->
            val updated = state.geqGains.toMutableList().apply { this[index] = gainDb }
            state.copy(geqGains = updated)
        }) {
            setGeqBarGain(index, gainDb)
        }

    fun reset() = change { resetProfileSpecificSettings() }

    fun resetAll() = change { resetAllProfiles() }

    private fun readOrKeep(deviceState: AudioDeviceState): DolbyUiState =
        try {
            readSettings(deviceState)
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
                    val dev = AudioOutputMonitor(controller.let { _ ->
                        return@let null
                    } ?: return@launch).currentDeviceState() // Fallback handled
                }
            }
        }
    }

    private fun readSettings(deviceState: AudioDeviceState): DolbyUiState {
        val p = controller.profile
        return DolbyUiState(
            dsOn = controller.dsOn,
            profile = p,
            activePort = deviceState.port,
            activeDeviceKey = deviceState.deviceKey,
            activeDeviceName = deviceState.deviceName,
            ieq = controller.getIeqPreset(p, deviceState.port),
            ieqAmount = controller.getIeqAmount(p),
            dialogueOn = controller.getDialogueEnhancerEnabled(p),
            dialogue = controller.getDialogueEnhancerAmount(p),
            stereo = controller.getStereoWideningAmount(p, deviceState.port),
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
