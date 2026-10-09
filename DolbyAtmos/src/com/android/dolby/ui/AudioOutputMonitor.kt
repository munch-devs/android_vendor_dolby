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

data class AudioDeviceState(
    val port: Int,
    val deviceKey: String,
    val deviceName: String,
)

internal class AudioOutputMonitor(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)!!

    fun currentDeviceState(): AudioDeviceState {
        val device = audioManager.getDevicesForAttributes(MEDIA_ATTRS).firstOrNull()
        val name = device?.name?.toString()?.takeUnless { it.isNullOrBlank() }

        return when (device?.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioDeviceState(
                port = DsTuning.HEADPHONE.id,
                deviceKey = "headphone_35mm",
                deviceName = name ?: "Wired Headphones"
            )

            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                val addr = device.address.takeIf { !it.isNullOrBlank() } ?: "generic_bt"
                AudioDeviceState(
                    port = DsTuning.BLUETOOTH.id,
                    deviceKey = "bt_$addr",
                    deviceName = name ?: "Bluetooth Audio"
                )
            }

            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> {
                val addrStr = device.address.takeIf { !it.isNullOrBlank() }?.replace("[^A-Za-z0-9]".toRegex(), "") ?: "generic"
                val idStr = name?.replace("\\s+".toRegex(), "_") ?: "usb"
                AudioDeviceState(
                    port = DsTuning.USB.id,
                    deviceKey = "usb_${idStr}_$addrStr",
                    deviceName = name ?: "USB Audio Device"
                )
            }

            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC -> AudioDeviceState(
                port = DsTuning.HDMI.id,
                deviceKey = "hdmi_display",
                deviceName = name ?: "HDMI Audio"
            )

            AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> AudioDeviceState(
                port = DsTuning.MIRACAST.id,
                deviceKey = "miracast_display",
                deviceName = name ?: "Wireless Display"
            )

            else -> AudioDeviceState(
                port = DsTuning.INTERNAL_SPEAKER.id,
                deviceKey = "speaker",
                deviceName = name ?: "Phone Speaker"
            )
        }
    }

    val activeDevice: Flow<AudioDeviceState> =
        callbackFlow {
                val callback =
                    object : AudioDeviceCallback() {
                        override fun onAudioDevicesAdded(devices: Array<AudioDeviceInfo>) {
                            trySend(currentDeviceState())
                        }

                        override fun onAudioDevicesRemoved(devices: Array<AudioDeviceInfo>) {
                            trySend(currentDeviceState())
                        }
                    }

                audioManager.registerAudioDeviceCallback(callback, null)
                trySend(currentDeviceState())

                awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
            }
            .distinctUntilChanged()

    private companion object {
        val MEDIA_ATTRS: AudioAttributes =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
    }
}
