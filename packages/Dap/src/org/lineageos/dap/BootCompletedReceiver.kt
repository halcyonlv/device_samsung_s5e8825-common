/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.dap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import androidx.preference.PreferenceManager

import org.lineageos.dap.DolbyFragment.Companion.PREF_DOLBY_ENABLE
import org.lineageos.dap.DolbyFragment.Companion.PREF_DOLBY_MODES

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Restore per-device state for all known device types
        for (device in DolbyCore.OutputDevice.values()) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val profileKey = "profile_${device.key}"
            val enabledKey = "enabled_${device.key}"
            // If not set, skip
            if (!prefs.contains(profileKey) && !prefs.contains(enabledKey)) continue
        }
        // Apply state for the current output device
        DolbyCore.applyCurrentDeviceState(context)
        // Start the device monitor service
        context.startService(Intent(context, DolbyDeviceMonitorService::class.java))
    }
}
