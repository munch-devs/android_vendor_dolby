/*
 * Copyright (C) 2023 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dolbyvision;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;
import android.view.Display.HdrCapabilities;

import java.util.Arrays;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "DoviParts";
    private static final boolean DEBUG = true;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Received boot completed intent");
        }

        final DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        if (displayManager == null) {
            return;
        }

        final Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return;
        }

        final HdrCapabilities capabilities = display.getHdrCapabilities();
        final int[] supportedTypes = (capabilities != null)
                ? capabilities.getSupportedHdrTypes()
                : new int[0];

        for (int type : supportedTypes) {
            if (type == HdrCapabilities.HDR_TYPE_DOLBY_VISION) {
                return;
            }
        }

        final int[] overrideTypes = Arrays.copyOf(supportedTypes, supportedTypes.length + 1);
        overrideTypes[supportedTypes.length] = HdrCapabilities.HDR_TYPE_DOLBY_VISION;

        // Override HDR types to enable Dolby Vision
        displayManager.overrideHdrTypes(Display.DEFAULT_DISPLAY, overrideTypes);
    }
}
