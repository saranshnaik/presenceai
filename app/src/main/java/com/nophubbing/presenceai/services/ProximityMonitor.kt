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

    fun detectProximity(): Float {

        if (bluetoothAdapter == null) {
            Log.e("PresenceAI", "Bluetooth not supported")
            return -100f
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.e("PresenceAI", "Bluetooth disabled")
            return -100f
        }

        if (!PermissionManager.hasBluetoothPermission(context)) {
            Log.e("PresenceAI", "Bluetooth permissions not granted")
            return -100f
        }

        Log.d("PresenceAI", "Starting proximity detection...")
        val latch = CountDownLatch(1)
        var maxRssi = -100f

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        
                        val rssi: Short = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        if (rssi != Short.MIN_VALUE) {
                            val rssiF = rssi.toFloat()
                            if (rssiF > maxRssi) maxRssi = rssiF
                            Log.d("PresenceAI", "Nearby device found: ${device?.address}, RSSI=$rssi")
                        }
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
                return -100f
            }

            // Wait for a short burst (3s) to get immediate results for UI
            latch.await(3, TimeUnit.SECONDS)
            
            bluetoothAdapter.cancelDiscovery()
            context.unregisterReceiver(receiver)

            maxRssi

        } catch (e: SecurityException) {
            Log.e("PresenceAI", "Bluetooth permission error during discovery", e)
            -100f
        } catch (e: Exception) {
            Log.e("PresenceAI", "Bluetooth proximity detection failed", e)
            -100f
        }
    }
}