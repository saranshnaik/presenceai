package com.nophubbing.presenceai.services

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.nophubbing.presenceai.utils.PermissionManager
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

        if (!PermissionManager.hasBluetoothPermission(context)) {
            Log.e("PresenceAI", "Bluetooth permissions not granted")
            return -1
        }

        Log.d("PresenceAI", "Starting proximity detection...")
        val latch = CountDownLatch(1)
        var deviceDetected = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                Log.d("PresenceAI", "Received broadcast: ${intent?.action}")
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        deviceDetected = true
                        Log.d("PresenceAI", "Nearby bluetooth device detected")
                        latch.countDown()
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d("PresenceAI", "Discovery finished")
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            val started = bluetoothAdapter.startDiscovery()
            Log.d("PresenceAI", "Bluetooth discovery started: $started")

            if (!started) {
                context.unregisterReceiver(receiver)
                return -1
            }

            val countMet = latch.await(10, TimeUnit.SECONDS)
            Log.d("PresenceAI", "Latch wait finished, deviceDetected=$deviceDetected, timeout=${!countMet}")

            bluetoothAdapter.cancelDiscovery()
            context.unregisterReceiver(receiver)

            if (deviceDetected) 1 else 0

        } catch (e: SecurityException) {
            Log.e("PresenceAI", "Bluetooth permission error during discovery", e)
            -1
        } catch (e: Exception) {
            Log.e("PresenceAI", "Bluetooth proximity detection failed", e)
            -1
        }
    }
}