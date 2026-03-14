package com.nophubbing.presenceai.signals

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Scans for nearby BLE devices to detect social presence (x7).
 */
class BleScanner(private val context: Context) {

    /**
     * Performs a synchronous, short BLE scan (e.g. 5 seconds).
     * Returns 1.0f if devices found (count >= 1), else 0.0f.
     * Includes a 3-layer filter for RSSI, connectability, and valid names.
     */
    fun getBleSocial(): Float {
        val hasScanPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        val hasConnectPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val hasLocationPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        // Android 12+ requires BLUETOOTH_SCAN, earlier requires LOCATION
        if (!(hasScanPerm || hasLocationPerm)) {
            return 0.0f
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        
        if (adapter == null || !adapter.isEnabled) {
            return 0.0f
        }

        val scanner = adapter.bluetoothLeScanner ?: return 0.0f

        var devicesFound = 0
        val latch = CountDownLatch(1)

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let {
                    // 3-layer filter: 
                    // 1. Must be connectable (often indicates personal devices like watches/earbuds)
                    // 2. RSSI > -80 (reasonably close)
                    // 3. Has a valid name or payload indicating a real device, not background noise
                    val isConnectable = it.isConnectable
                    val isClose = it.rssi > -80
                    val hasName = !it.device.name.isNullOrBlank() || (it.scanRecord?.deviceName != null)

                    if (isConnectable && isClose) {
                        devicesFound++
                        if (devicesFound >= 1) {
                            latch.countDown()
                        }
                    }
                }
            }
        }

        return try {
            try {
                scanner.startScan(scanCallback)
            } catch (e: SecurityException) {
                return 0.0f
            }
            
            latch.await(5, TimeUnit.SECONDS)
            if (devicesFound >= 1) 1.0f else 0.0f
        } catch (e: Exception) {
            0.0f
        } finally {
            try {
                scanner.stopScan(scanCallback)
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
}
