/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.geq.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.dolby.DolbyConstants.Companion.dlog
import com.android.dolby.geq.data.EQ_BAND_COUNTS
import com.android.dolby.geq.data.EqualizerRepository
import com.android.dolby.geq.data.Preset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val TAG = "EqViewModel"

private fun interpolate(gains: MutableList<Int>, points: List<Int>) {
    for (k in 0 until points.size - 1) {
        val a = points[k]
        val b = points[k + 1]
        for (i in a + 1 until b) {
            gains[i] = (gains[a] + (gains[b] - gains[a]) * (i - a) / (b - a).toFloat()).roundToInt()
        }
    }
}

class EqualizerViewModel(private val repository: EqualizerRepository) : ViewModel() {
    private val _presets = MutableStateFlow(repository.builtInPresets)
    val presets = _presets.asStateFlow()

    private val _preset = MutableStateFlow(repository.defaultPreset)
    val preset = _preset.asStateFlow()

    private val _bandCount = MutableStateFlow(repository.bandCount)
    val bandCount = _bandCount.asStateFlow()

    private val _curveView = MutableStateFlow(repository.curveView)
    val curveView = _curveView.asStateFlow()

    private var presetRestored = false

    init {
        repository.userPresets
            .onEach { presets ->
                dlog(TAG, "updated userPresets: $presets")
                _presets.value =
                    mutableListOf<Preset>()
                        .apply {
                            addAll(presets)
                            addAll(repository.builtInPresets)
                        }
                        .toList()

                if (!presetRestored) {
                    val bandGains = repository.getBandGains()
                    _preset.value =
                        _presets.value.find { bandGains == it.bandGains }
                            ?: Preset(name = "Custom", bandGains = bandGains)
                    dlog(TAG, "restored preset: ${_preset.value}")
                    presetRestored = true
                }
            }
            .launchIn(viewModelScope)

        _preset
            .drop(1)
            .onEach {
                if (!presetRestored) {
                    return@onEach
                }
                dlog(TAG, "updated preset: $it")
                repository.setBandGains(it.bandGains)
                if (it.isUserDefined) {
                    repository.addPreset(it)
                }
            }
            .launchIn(viewModelScope)
    }

    fun reset() {
        dlog(TAG, "reset()")
        if (_preset.value.isUserDefined) {
            _preset.value = _preset.value.copy(bandGains = repository.defaultPreset.bandGains)
        } else {
            _preset.value = repository.defaultPreset
        }
    }

    fun setPreset(preset: Preset) {
        dlog(TAG, "setPreset($preset)")
        _preset.value = preset
    }

    fun setBandCount(count: Int) {
        if (count !in EQ_BAND_COUNTS) return
        _bandCount.value = count
        repository.bandCount = count
    }

    fun setCurveView(curve: Boolean) {
        _curveView.value = curve
        repository.curveView = curve
    }

    fun setGain(index: Int, gain: Int, controlPoints: List<Int> = emptyList()) {
        dlog(TAG, "setGain($index, $gain)")
        _preset.value =
            _preset.value.run {
                val gains = bandGains.map { it.gain }.toMutableList()
                gains[index] = gain
                if (controlPoints.size in 2 until gains.size) interpolate(gains, controlPoints)
                copy(
                    name = if (!isUserDefined) "Custom" else name,

                    bandGains = bandGains.mapIndexed { i, band -> band.copy(gain = gains[i]) },
                    isMutated = true,
                )
            }
    }

    private fun validatePresetName(name: String): PresetNameValidationError? {
        return if (_presets.value.any { it.name.equals(name.trim(), ignoreCase = true) }) {
            PresetNameValidationError.NAME_EXISTS
        } else if (name.length > 50) {
            PresetNameValidationError.NAME_TOO_LONG
        } else null
    }

    fun createNewPreset(name: String): PresetNameValidationError? {
        dlog(TAG, "createNewPreset($name)")
        validatePresetName(name)?.let {
            dlog(TAG, "createNewPreset failed: $it")
            return it
        }
        _preset.value =
            _preset.value.copy(name = name.trim(), isUserDefined = true, isMutated = false)
        return null
    }

    fun renamePreset(preset: Preset, name: String): PresetNameValidationError? {
        dlog(TAG, "renamePreset($preset, $name)")

        createNewPreset(name = name)?.let {
            dlog(TAG, "renamePreset failed")
            return it
        }

        deletePreset(preset, shouldReset = false)
        return null
    }

    fun deletePreset(preset: Preset, shouldReset: Boolean = true) {
        dlog(TAG, "deletePreset($preset)")
        viewModelScope.launch { repository.removePreset(preset) }
        if (shouldReset) {
            _preset.value = repository.defaultPreset
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                EqualizerViewModel(
                    repository =
                        EqualizerRepository(
                            this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                        )
                )
            }
        }
    }
}
