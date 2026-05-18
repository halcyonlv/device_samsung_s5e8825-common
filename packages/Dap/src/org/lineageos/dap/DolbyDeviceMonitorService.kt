package org.lineageos.dap

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.os.Build

class DolbyDeviceMonitorService : Service() {
    private val TAG = "DolbyDeviceMonitorService"
    private val handler = Handler(Looper.getMainLooper())
    // Some ROMs don't send output change broadcasts for Bluetooth disconnects.
    // So we poll every 2s as a fallback to catch missed transitions.
    private val bluetoothActions = setOf(
        "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.device.action.ACL_CONNECTED",
        "android.bluetooth.device.action.ACL_DISCONNECTED",
        "android.media.ACTION_SCO_AUDIO_STATE_UPDATED"
    )
    private var lastDevice: DolbyCore.OutputDevice? = null
    private val pollRunnable = object : Runnable {
        override fun run() {
            // Poll for output device changes in case we miss a broadcast
            val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val devices = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            val currentDevice = DolbyCore.getCurrentOutputDevice(this@DolbyDeviceMonitorService)
            if (currentDevice != lastDevice) {
                Log.i(TAG, "Polling detected device change: ${lastDevice?.name} -> ${currentDevice.name}")
                Log.i(TAG, "Detected output device: ${currentDevice.name} (devices: ${devices.joinToString { it.type.toString() }})")
                DolbyCore.applyCurrentDeviceState(this@DolbyDeviceMonitorService)
                lastDevice = currentDevice
            }
            handler.postDelayed(this, 2000)
        }
    }
    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            Log.i(TAG, "Received intent: $action")
            // Bluetooth output changes can be delayed, so we wait a bit before applying
            if (action in bluetoothActions) {
                handler.postDelayed({
                    DolbyCore.applyCurrentDeviceState(this@DolbyDeviceMonitorService)
                }, 750)
            } else {
                DolbyCore.applyCurrentDeviceState(this@DolbyDeviceMonitorService)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created, registering device change receivers and starting poll")
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
            addAction("android.media.ACTION_SCO_AUDIO_STATE_UPDATED")
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.media.ACTION_AUDIO_BECOMING_NOISY")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(deviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(deviceReceiver, filter)
        }
        // Start polling fallback
        lastDevice = DolbyCore.getCurrentOutputDevice(this)
        handler.post(pollRunnable)
        // Apply state at service start
        DolbyCore.applyCurrentDeviceState(this)
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed, unregistering device change receivers and stopping poll")
        unregisterReceiver(deviceReceiver)
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 