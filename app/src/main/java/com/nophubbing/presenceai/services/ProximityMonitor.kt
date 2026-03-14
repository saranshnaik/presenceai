package com.nophubbing.presenceai.services

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ProximityMonitor(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    fun detectProximity(): Int {

        if (bluetoothAdapter == null) {
            Log.e("PresenceAI", "Bluetooth not supported")
            return -1
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.e("PresenceAI", "Bluetooth disabled")
            return -1
        }

        val latch = CountDownLatch(1)
        var deviceDetected = false

        val receiver = object : BroadcastReceiver() {

            override fun onReceive(ctx: Context?, intent: Intent?) {

                when (intent?.action) {

                    BluetoothDevice.ACTION_FOUND -> {
                        deviceDetected = true
                        Log.d("PresenceAI", "Nearby bluetooth device detected")
                        latch.countDown()
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        latch.countDown()
                    }
                }
            }
        }

        return try {

            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }

            context.registerReceiver(receiver, filter)

            bluetoothAdapter.startDiscovery()

            latch.await(5, TimeUnit.SECONDS)

            bluetoothAdapter.cancelDiscovery()

            context.unregisterReceiver(receiver)

            val result = if (deviceDetected) 1 else 0

            Log.d("PresenceAI", "PROXIMITY_SCAN_COMPLETE deviceDetected=$deviceDetected result=$result")

            result

        } catch (e: Exception) {

            Log.e("PresenceAI", "Bluetooth proximity detection failed", e)
            -1
        }
    }
}