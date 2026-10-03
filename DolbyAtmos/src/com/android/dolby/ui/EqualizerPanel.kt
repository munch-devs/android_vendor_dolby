/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolby.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.dolby.R
import com.android.dolby.geq.ui.EqualizerBands
import com.android.dolby.geq.ui.EqualizerViewModel
import com.android.dolby.geq.ui.PresetSelector

private enum class EqView { Bars, Curve }

/**
 * The graphic equalizer, embedded in the main screen: preset bar, a Bars/Curve switch, and the
 * bands. Reuses the existing preset selector and band sliders; only the curve view is new.
 */
@Composable
internal fun EqualizerPanel(
    viewModel: EqualizerViewModel,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val preset by viewModel.preset.collectAsState()
    var view by rememberSaveable { mutableStateOf(EqView.Bars) }

    Box(modifier) {
        SettingsCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PresetSelector(viewModel)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = view == EqView.Bars,
                        onClick = { view = EqView.Bars },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { Icon(Icons.Default.BarChart, null, Modifier.height(18.dp)) },
                    ) {
                        Text(stringResource(R.string.dolby_geq_view_bars), maxLines = 1)
                    }
                    SegmentedButton(
                        selected = view == EqView.Curve,
                        onClick = { view = EqView.Curve },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { Icon(Icons.Default.ShowChart, null, Modifier.height(18.dp)) },
                    ) {
                        Text(stringResource(R.string.dolby_geq_view_curve), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(16.dp))
                when (view) {
                    EqView.Bars -> EqualizerBands(viewModel)
                    EqView.Curve ->
                        EqualizerCurve(
                            gains = preset.bandGains,
                            onGainChangeFinished = viewModel::setGain,
                        )
                }
            }
        }
        // Dolby off: the page is dimmed by the screen; this makes it non-interactive too.
        if (!enabled) {
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            )
        }
    }
}
