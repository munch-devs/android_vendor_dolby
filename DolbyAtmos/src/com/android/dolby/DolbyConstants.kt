/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby

import android.util.Log

class DolbyConstants {

    enum class DsParam(val id: Int, val length: Int = 1) {
        HEADPHONE_VIRTUALIZER(101),
        SPEAKER_VIRTUALIZER(102),
        VOLUME_LEVELER_ENABLE(103),
        IEQ_PRESET(104),
        DIALOGUE_ENHANCER_ENABLE(105),
        GEQ_ENABLE(106),
        IEQ_AMOUNT(107),
        DIALOGUE_ENHANCER_AMOUNT(108),
        DIALOGUE_ENHANCER_DUCKING(109),
        GEQ_BAND_GAINS(110, 20),
        BASS_ENHANCER_ENABLE(111),
        VIRTUAL_BASS_ENABLE(112),
        STEREO_WIDENING_AMOUNT(113),
        REVERB_REDUCTION_ENABLE(114),
        REVERB_REDUCTION_AMOUNT(115),
        VOLUME_LEVELER_AMOUNT(116);

        override fun toString(): String = "${name}(${id})"
    }

    enum class DsTuning(val id: Int, val portName: String) {
        INTERNAL_SPEAKER(0, "internal_speaker"),
        HDMI(1, "hdmi"),
        MIRACAST(2, "miracast"),
        HEADPHONE(3, "headphone"),
        BLUETOOTH(4, "bluetooth"),
        USB(5, "usb");

        override fun toString(): String = portName
    }

    companion object {
        const val ACTION_DAP_PARAMS_UPDATE = "com.dolby.intent.action.DAP_PARAMS_UPDATE"
        const val ACTION_AUDIO_SERVER_RESTARTED = "audio_server_restarted"
        const val EVENT_NAME = "event name"
        const val CMD_INT_VALUE = "Integer Value"
        const val EVENT_DS_STATE_CHANGE = "ds_state_change"
        const val EVENT_PROFILE_CHANGE = "profile_change"
        const val EVENT_PROFILE_SETTING_CHANGE = "profile_setting_change"
        const val EVENT_RESET_PROFILE_SETTING = "reset_profile_setting"

        const val PROFILE_DYNAMIC = 0
        const val PROFILE_MOVIE = 1
        const val PROFILE_MUSIC = 2
        const val PROFILE_CUSTOM = 3

        val DIALOGUE_AMOUNT_RANGE = 0..16
        val VOLUME_LEVELER_AMOUNT_RANGE = 0..10
        val REVERB_REDUCTION_AMOUNT_RANGE = 0..9
        val STEREO_WIDENING_RANGE = 4..160
        val IEQ_AMOUNT_RANGE = 0..10

        const val TAG = "DolbyAtmos"

        const val PREF_ENABLE = "dolby_enable"
        const val PREF_PROFILE = "dolby_profile"
        const val PREF_PRESET = "dolby_preset"
        const val PREF_IEQ = "dolby_ieq"
        const val PREF_IEQ_AMOUNT = "dolby_ieq_amount"
        const val PREF_HP_VIRTUALIZER = "dolby_virtualizer"
        const val PREF_SPK_VIRTUALIZER = "dolby_spk_virtualizer"
        const val PREF_STEREO = "dolby_stereo"
        const val PREF_DIALOGUE = "dolby_dialogue"
        const val PREF_BASS = "dolby_bass"
        const val PREF_VOLUME = "dolby_volume"
        const val PREF_DIALOGUE_ENABLE = "dolby_dialogue_enable"
        const val PREF_REVERB_ENABLE = "dolby_reverb_enable"
        const val PREF_VOLUME_AMOUNT = "dolby_volume_amount"
        const val PREF_REVERB = "dolby_reverb"

        fun dlog(tag: String, msg: String) {
            if (Log.isLoggable(TAG, Log.DEBUG) || Log.isLoggable(tag, Log.DEBUG)) {
                Log.d("$TAG-$tag", msg)
            }
        }
    }
}
