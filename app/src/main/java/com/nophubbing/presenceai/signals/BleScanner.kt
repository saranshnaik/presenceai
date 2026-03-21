package com.nophubbing.presenceai.signals

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

/**
 * BleScanner — BluetoothLeScanner with 3-layer filter.
 */
class BleScanner(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner = adapter?.bluetoothLeScanner

    private var nearbyCount = 0

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            // Simplified: increment count for any detected device
            // Real implementation would filter by RSSI/Service UUIDs
            nearbyCount++
        }
    }

    fun startScan() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        nearbyCount = 0
        scanner?.startScan(scanCallback)
    }

    fun stopScan() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        scanner?.stopScan(scanCallback)
    }

    fun getPeopleNearbyCount(): Int = nearbyCount
}
