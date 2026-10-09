/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioManager.AudioPlaybackCallback
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.util.Log
import androidx.preference.PreferenceManager
import com.android.dolby.DolbyConstants.Companion.dlog
import com.android.dolby.DolbyConstants.DsParam
import com.android.dolby.DolbyConstants.DsTuning

internal class DolbyController private constructor(private val context: Context) {

    private var dolbyEffect = DolbyAudioEffect(EFFECT_PRIORITY, audioSession = 0)
    private val audioManager = context.getSystemService(AudioManager::class.java)!!
    private val handler = Handler(context.mainLooper)
    private val stereoWideningSupported =
        context.resources.getBoolean(R.bool.dolby_stereo_widening_supported)

    private val playbackCallback =
        object : AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                val isPlaying =
                    configs.any {
                        it.playerState == AudioPlaybackConfiguration.PLAYER_STATE_STARTED
                    }
                dlog(TAG, "onPlaybackConfigChanged: isPlaying=$isPlaying")
                if (isPlaying) setCurrentProfile()
            }
        }

    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                dlog(TAG, "onAudioDevicesAdded")
                setCurrentProfile()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                dlog(TAG, "onAudioDevicesRemoved")
                setCurrentProfile()
            }
        }

    private var registerCallbacks = false
        set(value) {
            if (field == value) return
            field = value
            dlog(TAG, "setRegisterCallbacks($value)")
            if (value) {
                audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
            } else {
                audioManager.unregisterAudioPlaybackCallback(playbackCallback)
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            }
        }

    var dsOn: Boolean
        get() = readOr(false) { dolbyEffect.dsOn }.also { dlog(TAG, "getDsOn: $it") }
        set(value) {
            dlog(TAG, "setDsOn: $value")
            checkEffect()
            dolbyEffect.dsOn = value
            registerCallbacks = value
            if (value) setCurrentProfile()
        }

    var profile: Int
        get() = readOr(0) { dolbyEffect.profile }.also { dlog(TAG, "getProfile: $it") }
        set(value) {
            dlog(TAG, "setProfile: $value")
            checkEffect()
            val validProfile = if (isProfileSupported(value)) value else 0
            dolbyEffect.profile = validProfile
            persistProfile(validProfile)
        }

    private val audioServerReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching {
                        recreateEffect()
                        restoreAll()
                    }
                    .onFailure { Log.e(TAG, "Failed to restore after audio server restart", it) }
            }
        }

    init {
        dlog(TAG, "initialized")
        context.applicationContext.registerReceiver(
            audioServerReceiver,
            IntentFilter(DolbyConstants.ACTION_AUDIO_SERVER_RESTARTED),
            Context.RECEIVER_EXPORTED,
        )
    }

    fun getActivePort(): Int {
        val device = audioManager.getDevicesForAttributes(MEDIA_ATTRS).firstOrNull()
        return when (device?.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> DsTuning.HEADPHONE.id
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> DsTuning.BLUETOOTH.id
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> DsTuning.USB.id
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC -> DsTuning.HDMI.id
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> DsTuning.MIRACAST.id
            else -> DsTuning.INTERNAL_SPEAKER.id
        }
    }

    fun onBootCompleted() {
        dlog(TAG, "onBootCompleted")
        restoreAll()
    }

    private fun restoreAll() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        dsOn = prefs.getBoolean(DolbyConstants.PREF_ENABLE, true)
        context.resources
            .getStringArray(R.array.dolby_profile_values)
            .map { it.toInt() }
            .filter(::isProfileSupported)
            .forEach { p ->
                dolbyEffect.resetProfileSpecificSettings(p)
                restoreSettings(p)
            }
        setCurrentProfile()
    }

    private fun isProfileSupported(profile: Int) =
        profile in DolbyConstants.PROFILE_DYNAMIC..DolbyConstants.PROFILE_CUSTOM

    private fun recreateEffect() {
        runCatching { dolbyEffect.release() }
        dolbyEffect = DolbyAudioEffect(EFFECT_PRIORITY, audioSession = 0)
    }

    private fun restoreSettings(profile: Int) {
        dlog(TAG, "restoreSettings(profile=$profile)")
        val prefs = profilePrefs(profile)
        setPreset(prefs.getString(DolbyConstants.PREF_PRESET, getPreset(profile))!!, profile)

        for (port in DsTuning.PORT_IDS) {
            val ieqKey = "${DolbyConstants.PREF_IEQ}_port_$port"
            val savedIeq =
                prefs.getString(ieqKey, prefs.getString(DolbyConstants.PREF_IEQ, null))
                    ?.toIntOrNull()
                    ?: getIeqPreset(profile, port)
            guarded {
                setIeqPreset(savedIeq, profile, port, persist = false)
            }

            if (stereoWideningSupported) {
                val stereoKey = "${DolbyConstants.PREF_STEREO}_port_$port"
                val savedStereo =
                    prefs.getString(stereoKey, prefs.getString(DolbyConstants.PREF_STEREO, null))
                        ?.toIntOrNull()
                        ?: getStereoWideningAmount(profile, port)
                guarded {
                    setStereoWideningAmount(savedStereo, profile, port, persist = false)
                }
            }
        }

        guarded {
            setIeqAmount(
                prefs
                    .getString(DolbyConstants.PREF_IEQ_AMOUNT, getIeqAmount(profile).toString())!!
                    .toInt(),
                profile,
                persist = false,
            )
        }
        setHeadphoneVirtEnabled(
            prefs.getBoolean(DolbyConstants.PREF_HP_VIRTUALIZER, getHeadphoneVirtEnabled(profile)),
            profile,
            persist = false,
        )
        if (!monoSpeaker) {
            setSpeakerVirtEnabled(
                prefs.getBoolean(DolbyConstants.PREF_SPK_VIRTUALIZER, getSpeakerVirtEnabled(profile)),
                profile,
                persist = false,
            )
        }
        setDialogueEnhancerAmount(
            savedAmount(
                prefs,
                DolbyConstants.PREF_DIALOGUE,
                getDialogueEnhancerAmount(profile),
            ),
            profile,
            persist = false,
        )
        setDialogueEnhancerEnabled(
            savedEnabled(
                prefs,
                DolbyConstants.PREF_DIALOGUE_ENABLE,
                DolbyConstants.PREF_DIALOGUE,
                getDialogueEnhancerEnabled(profile),
            ),
            profile,
            persist = false,
        )
        setBassEnhancerEnabled(
            prefs.getBoolean(DolbyConstants.PREF_BASS, getBassEnhancerEnabled(profile)),
            profile,
            persist = false,
        )
        guarded {
            setVirtualBassEnabled(
                prefs.getBoolean(DolbyConstants.PREF_VIRTUAL_BASS, getVirtualBassEnabled(profile)),
                profile,
                persist = false,
            )
        }
        setVolumeLevelerEnabled(
            prefs.getBoolean(DolbyConstants.PREF_VOLUME, getVolumeLevelerEnabled(profile)),
            profile,
            persist = false,
        )
        guarded {
            setVolumeLevelerAmount(
                prefs
                    .getString(
                        DolbyConstants.PREF_VOLUME_AMOUNT,
                        getVolumeLevelerAmount(profile).toString(),
                    )!!
                    .toInt(),
                profile,
                persist = false,
            )
        }
        guarded {
            setReverbReductionAmount(
                savedAmount(
                    prefs,
                    DolbyConstants.PREF_REVERB,
                    getReverbReductionAmount(profile),
                ),
                profile,
                persist = false,
            )
            setReverbReductionEnabled(
                savedEnabled(
                    prefs,
                    DolbyConstants.PREF_REVERB_ENABLE,
                    DolbyConstants.PREF_REVERB,
                    getReverbReductionEnabled(profile),
                ),
                profile,
                persist = false,
            )
        }
    }

    private fun savedAmount(prefs: SharedPreferences, key: String, current: Int): Int =
        prefs.getString(key, null)?.toIntOrNull()?.takeIf { it > 0 } ?: current

    private fun savedEnabled(
        prefs: SharedPreferences,
        enableKey: String,
        amountKey: String,
        current: Boolean,
    ): Boolean =
        if (prefs.contains(enableKey)) prefs.getBoolean(enableKey, current)
        else prefs.getString(amountKey, null)?.toIntOrNull()?.let { it > 0 } ?: current

    private fun persistProfile(value: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getString(DolbyConstants.PREF_PROFILE, null) != value.toString()) {
            prefs.edit().putString(DolbyConstants.PREF_PROFILE, value.toString()).apply()
        }
    }

    private fun profilePrefs(profile: Int) =
        context.getSharedPreferences("profile_$profile", Context.MODE_PRIVATE)

    private fun saveBool(profile: Int, key: String, value: Boolean) =
        profilePrefs(profile).edit().putBoolean(key, value).apply()

    private fun saveInt(profile: Int, key: String, value: Int) =
        profilePrefs(profile).edit().putString(key, value.toString()).apply()

    private inline fun <T> readOr(default: T, block: () -> T): T =
        runCatching(block).getOrElse {
            Log.w(TAG, "read failed", it)
            default
        }

    private inline fun guarded(block: () -> Unit) {
        runCatching(block).onFailure { Log.w(TAG, "restore failed", it) }
    }

    private fun checkEffect() {
        if (!dolbyEffect.hasControl()) {
            Log.w(TAG, "lost control, recreating effect")
            recreateEffect()
        }
    }

    private fun setCurrentProfile() {
        dlog(TAG, "setCurrentProfile")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.getString(DolbyConstants.PREF_PROFILE, "0")!!.toInt()
        profile = if (isProfileSupported(saved)) saved else 0
    }

    fun setDsOnAndPersist(dsOn: Boolean) {
        this.dsOn = dsOn
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(DolbyConstants.PREF_ENABLE, dsOn)
            .apply()
    }

    fun getProfileName(): String? {
        val p = profile.toString()
        val profiles = context.resources.getStringArray(R.array.dolby_profile_values)
        val profileIndex = profiles.indexOf(p)
        dlog(TAG, "getProfileName: profile=$p index=$profileIndex")
        return if (profileIndex == -1) null
        else context.resources.getStringArray(R.array.dolby_profile_entries)[profileIndex]
    }

    fun resetProfileSpecificSettings() {
        dlog(TAG, "resetProfileSpecificSettings")
        checkEffect()
        dolbyEffect.resetProfileSpecificSettings()
        context.deleteSharedPreferences("profile_$profile")
    }

    val monoSpeaker: Boolean
        get() = readOr(false) { dolbyEffect.isMonoSpeaker }

    fun isProfileModified(profile: Int = this.profile) =
        readOr(false) { dolbyEffect.isProfileSpecificSettingsModified(profile) }

    fun resetAllProfiles() {
        dlog(TAG, "resetAllProfiles")
        checkEffect()
        context.resources
            .getStringArray(R.array.dolby_profile_values)
            .map { it.toInt() }
            .filter(::isProfileSupported)
            .forEach {
                dolbyEffect.resetProfileSpecificSettings(it)
                context.deleteSharedPreferences("profile_$it")
            }
        setDsOnAndPersist(true)
        profile = 0
    }

    fun getPreset(profile: Int = this.profile): String {
        val gains = readOr(IntArray(20)) { dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile) }
        return gains.joinToString(separator = ",").also { dlog(TAG, "getPreset: $it") }
    }

    fun setPreset(value: String, profile: Int = this.profile) {
        dlog(TAG, "setPreset: $value")
        checkEffect()
        val gains = value.split(",").map { it.toInt() }.toIntArray()
        dolbyEffect.setDapParameter(DsParam.GEQ_BAND_GAINS, gains, profile)
    }

    fun getPresetName(): String {
        val presets = context.resources.getStringArray(R.array.dolby_preset_values)
        val presetIndex = presets.indexOf(getPreset())
        return if (presetIndex == -1) {
            "Custom"
        } else {
            context.resources.getStringArray(R.array.dolby_preset_entries)[presetIndex]
        }
    }

    fun getHeadphoneVirtEnabled(profile: Int = this.profile) =
        readOr(false) {
            dolbyEffect.getDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER, profile)
        }.also { dlog(TAG, "getHeadphoneVirtEnabled: $it") }

    fun setHeadphoneVirtEnabled(value: Boolean, profile: Int = this.profile, persist: Boolean = true) {
        dlog(TAG, "setHeadphoneVirtEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.HEADPHONE_VIRTUALIZER, value, profile)
        if (persist) saveBool(profile, DolbyConstants.PREF_HP_VIRTUALIZER, value)
    }

    fun getSpeakerVirtEnabled(profile: Int = this.profile) =
        readOr(false) {
            dolbyEffect.getDapParameterBool(DsParam.SPEAKER_VIRTUALIZER, profile)
        }.also { dlog(TAG, "getSpeakerVirtEnabled: $it") }

    fun setSpeakerVirtEnabled(value: Boolean, profile: Int = this.profile, persist: Boolean = true) {
        if (monoSpeaker) return
        dlog(TAG, "setSpeakerVirtEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.SPEAKER_VIRTUALIZER, value, profile)
        if (persist) saveBool(profile, DolbyConstants.PREF_SPK_VIRTUALIZER, value)
    }

    fun getBassEnhancerEnabled(profile: Int = this.profile) =
        readOr(false) {
            dolbyEffect.getDapParameterBool(DsParam.BASS_ENHANCER_ENABLE, profile)
        }.also { dlog(TAG, "getBassEnhancerEnabled: $it") }

    fun setBassEnhancerEnabled(value: Boolean, profile: Int = this.profile, persist: Boolean = true) {
        dlog(TAG, "setBassEnhancerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.BASS_ENHANCER_ENABLE, value, profile)
        if (persist) saveBool(profile, DolbyConstants.PREF_BASS, value)
    }

    fun getVirtualBassEnabled(profile: Int = this.profile) =
        readOr(false) {
            dolbyEffect.getDapParameterBool(DsParam.VIRTUAL_BASS_ENABLE, profile)
        }.also { dlog(TAG, "getVirtualBassEnabled: $it") }

    fun setVirtualBassEnabled(value: Boolean, profile: Int = this.profile, persist: Boolean = true) {
        dlog(TAG, "setVirtualBassEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.VIRTUAL_BASS_ENABLE, value, profile)
        if (persist) saveBool(profile, DolbyConstants.PREF_VIRTUAL_BASS, value)
    }

    fun getVolumeLevelerEnabled(profile: Int = this.profile) =
        readOr(false) {
            dolbyEffect.getDapParameterBool(DsParam.VOLUME_LEVELER_ENABLE, profile)
        }.also { dlog(TAG, "getVolumeLevelerEnabled: $it") }

    fun setVolumeLevelerEnabled(value: Boolean, profile: Int = this.profile, persist: Boolean = true) {
        dlog(TAG, "setVolumeLevelerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.VOLUME_LEVELER_ENABLE, value, profile)
        if (persist) saveBool(profile, DolbyConstants.PREF_VOLUME, value)
    }

    fun getVolumeLevelerAmount(profile: Int = this.profile) =
        readOr(0) { dolbyEffect.getDapParameterInt(DsParam.VOLUME_LEVELER_AMOUNT, profile) }

    fun setVolumeLevelerAmount(value: Int, profile: Int = this.profile, persist: Boolean = true) {
        val amount = value.coerceIn(DolbyConstants.VOLUME_LEVELER_AMOUNT_RANGE)
        dlog(TAG, "setVolumeLevelerAmount: $amount")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.VOLUME_LEVELER_AMOUNT, amount, profile)
        if (persist) saveInt(profile, DolbyConstants.PREF_VOLUME_AMOUNT, amount)
    }

    fun getStereoWideningAmount(profile: Int = this.profile, port: Int = getActivePort()) =
        if (!stereoWideningSupported) {
            0
        } else {
            readOr(32) {
                dolbyEffect.getDapParameterInt(DsParam.STEREO_WIDENING_AMOUNT, profile, port)
            }.also { dlog(TAG, "getStereoWideningAmount(profile=$profile, port=$port): $it") }
        }

    fun setStereoWideningAmount(
        value: Int,
        profile: Int = this.profile,
        port: Int = getActivePort(),
        persist: Boolean = true,
    ) {
        if (!stereoWideningSupported) return
        val amount = value.coerceIn(DolbyConstants.STEREO_WIDENING_RANGE)
        dlog(TAG, "setStereoWideningAmount(profile=$profile, port=$port): $amount")
        checkEffect()
        if (getStereoWideningAmount(profile, port) != amount) {
            dolbyEffect.setDapParameter(DsParam.STEREO_WIDENING_AMOUNT, amount, profile, port)
        }
        if (persist) saveInt(profile, "${DolbyConstants.PREF_STEREO}_port_$port", amount)
    }

    // Port-specific IEQ Preset matching OEM DolbyAudioEffect.getIeqPreset(port)
    fun getIeqPreset(profile: Int = this.profile, port: Int = getActivePort()) =
        readOr(0) {
            dolbyEffect.getDapParameterInt(DsParam.IEQ_PRESET, profile, port)
        }.also { dlog(TAG, "getIeqPreset(profile=$profile, port=$port): $it") }

    fun setIeqPreset(
        value: Int,
        profile: Int = this.profile,
        port: Int = getActivePort(),
        persist: Boolean = true,
    ) {
        dlog(TAG, "setIeqPreset(profile=$profile, port=$port): $value")
        checkEffect()
        if (getIeqPreset(profile, port) != value) {
            dolbyEffect.setDapParameter(DsParam.IEQ_PRESET, value, profile, port)
        }
        if (persist) saveInt(profile, "${DolbyConstants.PREF_IEQ}_port_$port", value)
    }

    fun getIeqAmount(profile: Int = this.profile) =
        readOr(6) { dolbyEffect.getDapParameterInt(DsParam.IEQ_AMOUNT, profile) }

    fun setIeqAmount(value: Int, profile: Int = this.profile, persist: Boolean = true) {
        val amount = value.coerceIn(DolbyConstants.IEQ_AMOUNT_RANGE)
        dlog(TAG, "setIeqAmount: $amount")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.IEQ_AMOUNT, amount, profile)
        if (persist) saveInt(profile, DolbyConstants.PREF_IEQ_AMOUNT, amount)
    }

    fun getDialogueEnhancerEnabled(profile: Int = this.profile) =
        readOr(false) { dolbyEffect.getDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE, profile) }

    fun setDialogueEnhancerEnabled(
        value: Boolean,
        profile: Int = this.profile,
        persist: Boolean = true,
    ) {
        dlog(TAG, "setDialogueEnhancerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, value, profile)
        if (persist) saveBool(profile, DolbyConstants.PREF_DIALOGUE_ENABLE, value)
    }

    fun getDialogueEnhancerAmount(profile: Int = this.profile) =
        readOr(0) { dolbyEffect.getDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT, profile) }

    fun setDialogueEnhancerAmount(value: Int, profile: Int = this.profile, persist: Boolean = true) {
        val amount = value.coerceIn(DolbyConstants.DIALOGUE_AMOUNT_RANGE)
        dlog(TAG, "setDialogueEnhancerAmount: $amount")
        checkEffect()
        if (getDialogueEnhancerAmount(profile) != amount) {
            dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, amount, profile)
        }
        if (persist) saveInt(profile, DolbyConstants.PREF_DIALOGUE, amount)
    }

    fun getReverbReductionEnabled(profile: Int = this.profile) =
        readOr(false) { dolbyEffect.getDapParameterBool(DsParam.REVERB_REDUCTION_ENABLE, profile) }

    fun setReverbReductionEnabled(
        value: Boolean,
        profile: Int = this.profile,
        persist: Boolean = true,
    ) {
        dlog(TAG, "setReverbReductionEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.REVERB_REDUCTION_ENABLE, value, profile)
        if (persist) saveBool(profile, DolbyConstants.PREF_REVERB_ENABLE, value)
    }

    fun getReverbReductionAmount(profile: Int = this.profile) =
        readOr(0) { dolbyEffect.getDapParameterInt(DsParam.REVERB_REDUCTION_AMOUNT, profile) }

    fun setReverbReductionAmount(value: Int, profile: Int = this.profile, persist: Boolean = true) {
        val amount = value.coerceIn(DolbyConstants.REVERB_REDUCTION_AMOUNT_RANGE)
        dlog(TAG, "setReverbReductionAmount: $amount")
        checkEffect()
        if (getReverbReductionAmount(profile) != amount) {
            dolbyEffect.setDapParameter(DsParam.REVERB_REDUCTION_AMOUNT, amount, profile)
        }
        if (persist) saveInt(profile, DolbyConstants.PREF_REVERB, amount)
    }

    companion object {
        private const val TAG = "DolbyController"
        private const val EFFECT_PRIORITY = 100
        private val MEDIA_ATTRS: AudioAttributes =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()

        @Volatile private var instance: DolbyController? = null

        fun getInstance(context: Context) =
            instance
                ?: synchronized(this) {
                    instance ?: DolbyController(context.applicationContext).also { instance = it }
                }
    }
}
