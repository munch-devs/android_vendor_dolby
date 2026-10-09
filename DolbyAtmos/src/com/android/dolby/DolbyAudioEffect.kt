/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby

import android.media.audiofx.AudioEffect
import android.util.Log
import com.android.dolby.DolbyConstants.Companion.dlog
import com.android.dolby.DolbyConstants.DsParam
import com.android.dolby.DolbyConstants.DsTuning
import java.util.UUID

class DolbyAudioEffect(priority: Int, audioSession: Int) :
    AudioEffect(EFFECT_TYPE_NULL, EFFECT_TYPE_DAP, priority, audioSession) {

    override fun hasControl(): Boolean =
        try {
            super.hasControl()
        } catch (e: IllegalStateException) {
            Log.e(TAG, e.toString())
            false
        }

    var dsOn: Boolean
        get() = getIntParam(EFFECT_PARAM_ENABLE) > 0
        set(value) {
            setIntParam(EFFECT_PARAM_ENABLE, if (value) 1 else 0)
            enabled = value
        }

    var profile: Int
        get() = getIntParam(EFFECT_PARAM_PROFILE)
        set(value) {
            setIntParam(EFFECT_PARAM_PROFILE, value)
        }

    val numOfProfiles: Int
        get() = getIntParam(PARAM_PROFILE_COUNT)

    val numOfIeqPresets: Int
        get() = getIntParam(PARAM_IEQ_PRESETS_COUNT)

    val isMonoSpeaker: Boolean
        get() = getIntParam(PARAM_MONO_SPEAKER) > 0

    private fun setIntParam(param: Int, value: Int) {
        dlog(TAG, "setIntParam($param, $value)")
        val buf = ByteArray(12)
        int32ToByteArray(param, buf, 0)
        int32ToByteArray(1, buf, 4)
        int32ToByteArray(value, buf, 8)
        checkReturnValue(setParameter(EFFECT_PARAM_CPDP_VALUES, buf))
    }

    private fun getIntParam(param: Int): Int {
        val buf = ByteArray(12)
        int32ToByteArray(param, buf, 0)
        checkReturnValue(getParameter(EFFECT_PARAM_CPDP_VALUES + param, buf))
        return byteArrayToInt32(buf).also { dlog(TAG, "getIntParam($param): $it") }
    }

    fun isProfileSpecificSettingsModified(profile: Int): Boolean {
        val buf = ByteArray(12)
        val packProfile = profile shl 16
        checkReturnValue(getParameter(EFFECT_PARAM_GET_PROFILE_MODIFIED + packProfile, buf))
        return byteArrayToInt32(buf) > 0
    }

    fun resetProfileSpecificSettings(profile: Int = this.profile) {
        dlog(TAG, "resetProfileSpecificSettings: profile=$profile")
        setIntParam(EFFECT_PARAM_RESET_PROFILE_SETTINGS, profile)
    }

    fun setDapParameter(param: DsParam, values: IntArray, profile: Int = this.profile) {
        dlog(TAG, "setDapParameter: profile=$profile param=$param")
        if (isMonoSpeaker && param == DsParam.SPEAKER_VIRTUALIZER) {
            throw UnsupportedOperationException()
        }
        val length = values.size
        val buf = ByteArray((length + 4) * 4)
        int32ToByteArray(EFFECT_PARAM_SET_PROFILE_PARAMETER, buf, 0)
        int32ToByteArray(length + 1, buf, 4)
        int32ToByteArray(profile, buf, 8)
        int32ToByteArray(param.id, buf, 12)
        int32ArrayToByteArray(values, buf, 16)
        checkReturnValue(setParameter(EFFECT_PARAM_CPDP_VALUES, buf))
    }

    fun setDapParameter(param: DsParam, values: IntArray, profile: Int, port: Int) {
        dlog(TAG, "setDapParameter: profile=$profile port=$port param=$param")
        require(port in DsTuning.INTERNAL_SPEAKER.id..DsTuning.USB.id) {
            "Invalid port $port"
        }
        if (isMonoSpeaker && param == DsParam.SPEAKER_VIRTUALIZER) {
            throw UnsupportedOperationException()
        }
        val length = values.size
        val buf = ByteArray((length + 5) * 4)
        int32ToByteArray(EFFECT_PARAM_SET_PROFILE_PORT_PARAMETER, buf, 0)
        int32ToByteArray(length + 1, buf, 4)
        int32ToByteArray(profile, buf, 8)
        int32ToByteArray(port, buf, 12)
        int32ToByteArray(param.id, buf, 16)
        int32ArrayToByteArray(values, buf, 20)
        checkReturnValue(setParameter(EFFECT_PARAM_CPDP_VALUES, buf))
    }

    fun setDapParameter(param: DsParam, enable: Boolean, profile: Int = this.profile) =
        setDapParameter(param, intArrayOf(if (enable) 1 else 0), profile)

    fun setDapParameter(param: DsParam, value: Int, profile: Int = this.profile) =
        setDapParameter(param, intArrayOf(value), profile)

    fun setDapParameter(param: DsParam, value: Int, profile: Int, port: Int) =
        setDapParameter(param, intArrayOf(value), profile, port)

    fun getDapParameter(param: DsParam, profile: Int = this.profile): IntArray {
        dlog(TAG, "getDapParameter: profile=$profile param=$param")
        val length = param.length
        val buf = ByteArray((length + 2) * 4)
        val p = (param.id shl 16) + (profile shl 8) + EFFECT_PARAM_GET_PROFILE_PARAMETER
        checkReturnValue(getParameter(p, buf))
        return byteArrayToInt32Array(buf, length)
    }

    fun getDapParameter(param: DsParam, profile: Int, port: Int): IntArray {
        dlog(TAG, "getDapParameter: profile=$profile port=$port param=$param")
        require(port in DsTuning.INTERNAL_SPEAKER.id..DsTuning.USB.id) {
            "Invalid port $port"
        }
        val length = param.length
        val buf = ByteArray((length + 2) * 4)
        val packId = param.id shl 16
        val profilePortId = (port shl 8) or (profile shl 12)
        checkReturnValue(getParameter(EFFECT_PARAM_GET_PROFILE_PORT_PARAMETER + packId + profilePortId, buf))
        return byteArrayToInt32Array(buf, length)
    }

    fun getDapParameterBool(param: DsParam, profile: Int = this.profile): Boolean =
        getDapParameter(param, profile)[0] != 0

    fun getDapParameterInt(param: DsParam, profile: Int = this.profile): Int =
        getDapParameter(param, profile)[0]

    fun getDapParameterInt(param: DsParam, profile: Int, port: Int): Int =
        getDapParameter(param, profile, port)[0]

    private fun checkReturnValue(ret: Int) {
        if (ret < 0) {
            when (ret) {
                -5 -> throw UnsupportedOperationException("DolbyAudioEffect: invalid parameter operation")
                -4 -> throw IllegalArgumentException("DolbyAudioEffect: bad parameter value")
                else -> throw RuntimeException("DolbyAudioEffect: set/get parameter error ($ret)")
            }
        }
    }

    companion object {
        private const val PARAM_PROFILE_COUNT = 0x3000000 // 50331648
        private const val PARAM_IEQ_PRESETS_COUNT = 0x4000000 // 67108864
        private const val PARAM_MONO_SPEAKER = 0xD000000 // 218103808
        private const val TAG = "DolbyAudioEffect"
        private val EFFECT_TYPE_DAP = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")

        private const val EFFECT_PARAM_ENABLE = 0
        private const val EFFECT_PARAM_CPDP_VALUES = 5
        private const val EFFECT_PARAM_PROFILE = 0xA000000 // 167772160
        private const val EFFECT_PARAM_SET_PROFILE_PARAMETER = 0x1000000 // 16777216
        private const val EFFECT_PARAM_GET_PROFILE_PARAMETER = 0x1000005 // 16777221
        private const val EFFECT_PARAM_SET_PROFILE_PORT_PARAMETER = 0x2000000 // 33554432
        private const val EFFECT_PARAM_GET_PROFILE_PORT_PARAMETER = 0x2000005 // 33554437
        private const val EFFECT_PARAM_GET_PROFILE_MODIFIED = 0xB000005 // 184549381
        private const val EFFECT_PARAM_RESET_PROFILE_SETTINGS = 0xC000000 // 201326592

        private fun int32ToByteArray(value: Int, dst: ByteArray, index: Int) {
            var idx = index
            dst[idx++] = (value and 0xff).toByte()
            dst[idx++] = ((value ushr 8) and 0xff).toByte()
            dst[idx++] = ((value ushr 16) and 0xff).toByte()
            dst[idx] = ((value ushr 24) and 0xff).toByte()
        }

        private fun byteArrayToInt32(ba: ByteArray): Int {
            return ((ba[3].toInt() and 0xff) shl 24) or
                ((ba[2].toInt() and 0xff) shl 16) or
                ((ba[1].toInt() and 0xff) shl 8) or
                (ba[0].toInt() and 0xff)
        }

        private fun int32ArrayToByteArray(src: IntArray, dst: ByteArray, index: Int) {
            var idx = index
            for (x in src) {
                dst[idx++] = (x and 0xff).toByte()
                dst[idx++] = ((x ushr 8) and 0xff).toByte()
                dst[idx++] = ((x ushr 16) and 0xff).toByte()
                dst[idx++] = ((x ushr 24) and 0xff).toByte()
            }
        }

        private fun byteArrayToInt32Array(ba: ByteArray, dstLength: Int): IntArray {
            val srcLength = ba.size shr 2
            val dst = IntArray(dstLength.coerceAtMost(srcLength))
            for (i in dst.indices) {
                dst[i] =
                    ((ba[i * 4 + 3].toInt() and 0xff) shl 24) or
                        ((ba[i * 4 + 2].toInt() and 0xff) shl 16) or
                        ((ba[i * 4 + 1].toInt() and 0xff) shl 8) or
                        (ba[i * 4].toInt() and 0xff)
            }
            return dst
        }
    }
}
