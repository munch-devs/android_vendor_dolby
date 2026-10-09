/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.geq.data

import kotlin.math.roundToInt

data class BandGain(val band: Int, var gain: Int = 0)

fun formatHz(hz: Int): String =
    when {
        hz < 1000 -> "$hz"
        hz % 1000 == 0 -> "${hz / 1000}k"
        else -> String.format(java.util.Locale.US, "%.1fk", hz / 1000f)
    }

val EQ_BAND_FREQUENCIES = intArrayOf(
    47, 141, 234, 328, 469, 656, 844, 1031, 1313, 1688,
    2250, 3000, 3750, 4688, 5813, 7125, 9000, 11250, 13875, 19688,
)

val EQ_BAND_COUNTS = listOf(10, 15, 20)

fun bandControlPoints(count: Int): List<Int> {
    val last = EQ_BAND_FREQUENCIES.size - 1
    if (count > last) return List(last + 1) { it }
    return List(count) { (it * last / (count - 1f)).roundToInt() }
}

fun formatHzLong(hz: Int): String =
    if (hz < 1000) "$hz Hz" else formatHz(hz).replace("k", " kHz")
