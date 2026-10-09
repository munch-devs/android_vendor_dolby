/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.android.dolby.DolbyConstants.DsTuning
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal class AudioOutputMonitor(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)!!

    fun currentPort(): Int {
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

    val activePort: Flow<Int> =
        callbackFlow {
                val callback =
                    object : AudioDeviceCallback() {
                        override fun onAudioDevicesAdded(devices: Array<AudioDeviceInfo>) {
                            trySend(currentPort())
                        }

                        override fun onAudioDevicesRemoved(devices: Array<AudioDeviceInfo>) {
                            trySend(currentPort())
                        }
                    }
                audioManager.registerAudioDeviceCallback(callback, null)
                trySend(currentPort())
                awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
            }
            .distinctUntilChanged()

    private companion object {
        val MEDIA_ATTRS: AudioAttributes =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
    }
}
