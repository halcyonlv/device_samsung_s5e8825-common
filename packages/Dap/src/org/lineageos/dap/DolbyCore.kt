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

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import android.util.Log
import android.os.Handler
import android.os.Looper

import org.lineageos.dap.DolbyFragment.Companion.PREF_DOLBY_MODES

import java.util.UUID

object DolbyCore {
    private const val EFFECT_PARAM_PROFILE = 0
    private const val EFFECT_PARAM_EFF_ENAB = 19

    private val EFFECT_TYPE_DAP = UUID.fromString("46d279d9-9be7-453d-9d7c-ef937f675587")

    const val PROFILE_AUTO = 0
    const val PROFILE_MOVIE = 1
    const val PROFILE_MUSIC = 2
    const val PROFILE_VOICE = 3
    const val PROFILE_GAME = 4
    const val PROFILE_OFF = 5
    const val PROFILE_GAME_1 = 6
    const val PROFILE_GAME_2 = 7
    const val PROFILE_SPACIAL_AUDIO = 8

    private var audioEffect: AudioEffect? = createAudioEffect()
    private var lastDevice: OutputDevice? = null

    private fun createAudioEffect(): AudioEffect? {
        return runCatching {
            AudioEffect(EFFECT_TYPE_DAP, AudioEffect.EFFECT_TYPE_NULL, 0, 0)
        }.getOrNull()
    }

    enum class OutputDevice(val key: String) {
        SPEAKER("speaker"),
        HEADPHONES("headphones"),
        BLUETOOTH("bluetooth"),
        OTHER("other")
    }

    private const val TAG = "DolbyCore"

    fun getCurrentOutputDevice(context: Context): OutputDevice {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var detected: OutputDevice = OutputDevice.OTHER
        devices.forEach {
            when (it.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> detected = OutputDevice.BLUETOOTH
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET -> detected = OutputDevice.HEADPHONES
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> detected = OutputDevice.SPEAKER
            }
        }
        return detected
    }

    private fun getPrefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun setEnabled(context: Context, enabled: Boolean) {
        val device = getCurrentOutputDevice(context)
        val prefs = getPrefs(context)
        val key = "enabled_${device.key}"
        prefs.edit().putBoolean(key, enabled).apply()
        audioEffect?.enabled = enabled
        Log.i(TAG, "Set enabled=$enabled for device=${device.name}")
    }

    fun isEnabled(context: Context): Boolean {
        val device = getCurrentOutputDevice(context)
        val prefs = getPrefs(context)
        val key = "enabled_${device.key}"
        val value = prefs.getBoolean(key, false)
        Log.i(TAG, "isEnabled for device=${device.name} = $value")
        return value
    }

    fun applyCurrentDeviceState(context: Context) {
        val device = getCurrentOutputDevice(context)
        val enabled = isEnabled(context)
        val profile = getProfile(context)
        val handler = Handler(Looper.getMainLooper())
        if (lastDevice != device) {
            Log.i(TAG, "Output device changed: ${lastDevice?.name} -> ${device.name}, resetting AudioEffect")
            audioEffect?.release()
            audioEffect = null
            lastDevice = device
            handler.postDelayed({
                audioEffect = createAudioEffect()
                audioEffect?.setParameter(EFFECT_PARAM_EFF_ENAB, 1)
                audioEffect?.enabled = false
                handler.postDelayed({
                    audioEffect?.enabled = enabled
                    audioEffect?.setParameter(EFFECT_PARAM_PROFILE, profile)
                    Log.i(TAG, "applyCurrentDeviceState: enabled=$enabled, profile=$profile for device ${device.name}")
                }, 100)
            }, 100)
        } else {
            audioEffect?.setParameter(EFFECT_PARAM_EFF_ENAB, 1)
            audioEffect?.enabled = false
            handler.postDelayed({
                audioEffect?.enabled = enabled
                audioEffect?.setParameter(EFFECT_PARAM_PROFILE, profile)
                Log.i(TAG, "applyCurrentDeviceState: enabled=$enabled, profile=$profile for device ${device.name}")
            }, 100)
        }
    }

    fun setProfile(context: Context, profile: Int) {
        val device = getCurrentOutputDevice(context)
        val prefs = getPrefs(context)
        val key = "profile_${device.key}"
        prefs.edit().putInt(key, profile).apply()
        if (audioEffect != null && isEnabled(context)) {
            audioEffect?.setParameter(EFFECT_PARAM_PROFILE, profile)
        }
    }

    fun getProfile(context: Context): Int {
        val device = getCurrentOutputDevice(context)
        val prefs = getPrefs(context)
        val key = "profile_${device.key}"
        return prefs.getInt(key, PROFILE_AUTO)
    }

    fun getProfileName(context: Context): String {
        val profile = getProfile(context)
        val resourceName = PREF_DOLBY_MODES.filter { it.value == profile }.keys.first()
        return context.resources.getString(context.resources.getIdentifier(
                resourceName, "string", context.packageName
        ))
    }
}
