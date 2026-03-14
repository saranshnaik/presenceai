package com.nophubbing.presenceai.services

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ProximityMonitor — Bluetooth-based social proximity detection.
 *
 * Strategy (two-tier):
 *
 * TIER 1 — BluetoothManager.getConnectedDevices() for each profile (instant).
 *   Uses the official API instead of reflection. Works reliably on MIUI/ColorOS.
 *   Checks A2DP, HEADSET, and GATT profiles for any connected bonded device.
 *   Logs the device name so it appears in the debug output.
 *   Returns 0.9 (strong confidence) immediately.
 *
 * TIER 2 — Short classic BT discovery scan (only if Tier 1 finds nothing).
 *   Cancels as soon as the first device is found. Max 4 seconds.
 *   Returns 0.5 (weaker, anonymous proximity).
 *   Logs the discovered device name.
 *
 * Return values:
 *   > 0f  — proximity confidence [0.5 .. 1.0]
 *   = 0f  — no devices found
 *  -1f   — Bluetooth disabled or permissions not granted
 */
class ProximityMonitor(private val context: Context) {

    companion object {
        private const val TAG          = "PresenceAI"
        private const val SCAN_MS      = 4_000L
        private const val SCORE_PAIRED = 0.9f
        private const val SCORE_SCAN   = 0.5f
    }

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val adapter get() = bluetoothManager?.adapter

    fun detectProximity(): Float {
        val mgr = bluetoothManager ?: run {
            Log.e(TAG, "BT: hardware not available")
            return -1f
        }
        val adp = adapter ?: run {
            Log.e(TAG, "BT: adapter null")
            return -1f
        }
        if (!adp.isEnabled) {
            Log.e(TAG, "BT: disabled — turn on Bluetooth for proximity detection")
            return -1f
        }
        if (!hasPermissions()) {
            Log.e(TAG, "BT: permissions not granted")
            return -1f
        }

        // ── Tier 1: connected devices via official BluetoothManager API ───────
        val connectedScore = checkConnectedDevices(mgr)
        if (connectedScore > 0f) return connectedScore

        // ── Tier 2: short discovery scan ─────────────────────────────────────
        return runDiscoveryScan(adp)
    }

    // ── Tier 1 ────────────────────────────────────────────────────────────────

    private fun checkConnectedDevices(mgr: BluetoothManager): Float {
        return try {
            // BluetoothProfile constants:
            //   HEADSET = 1, A2DP = 2, GATT = 7, GATT_SERVER = 8
            // NOT all profiles are supported on every device — catch per-profile.
            val profilesToCheck = listOf(
                BluetoothProfile.A2DP,        // 2 — headphones, speakers
                BluetoothProfile.HEADSET,     // 1 — earpiece, hands-free
                BluetoothProfile.GATT,        // 7 — BLE (smartwatch, band)
                BluetoothProfile.GATT_SERVER  // 8 — BLE server role
            )

            for (profile in profilesToCheck) {
                val connected: List<BluetoothDevice> = try {
                    mgr.getConnectedDevices(profile)
                } catch (e: SecurityException) {
                    Log.d(TAG, "BT: SecurityException on profile=$profile — skipping")
                    continue
                } catch (e: IllegalArgumentException) {
                    Log.d(TAG, "BT: Profile $profile not supported on this device — skipping")
                    continue
                } catch (e: Exception) {
                    Log.d(TAG, "BT: Profile $profile error (${e.message}) — skipping")
                    continue
                }

                if (connected.isNotEmpty()) {
                    val names = connected.mapNotNull { device ->
                        try {
                            if (hasConnectPermission()) device.name?.takeIf { it.isNotBlank() }
                            else null
                        } catch (_: Exception) { null }
                    }.joinToString(", ").ifBlank { "unnamed device" }

                    val profileName = when (profile) {
                        BluetoothProfile.A2DP        -> "A2DP (audio)"
                        BluetoothProfile.HEADSET     -> "HEADSET (hands-free)"
                        BluetoothProfile.GATT        -> "GATT/BLE"
                        BluetoothProfile.GATT_SERVER -> "GATT_SERVER/BLE"
                        else                         -> "profile=$profile"
                    }
                    Log.d(TAG, "BT: Connected via $profileName → \"$names\" → score=$SCORE_PAIRED")
                    return SCORE_PAIRED
                }
            }

            Log.d(TAG, "BT: No connected devices found on any profile")
            0f
        } catch (e: Exception) {
            Log.d(TAG, "BT: Tier 1 failed (${e.message}), falling back to scan")
            0f
        }
    }

    // ── Tier 2 ────────────────────────────────────────────────────────────────

    private fun runDiscoveryScan(adp: android.bluetooth.BluetoothAdapter): Float {
        val latch = CountDownLatch(1)
        var foundName = ""

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val name = try {
                            if (hasConnectPermission()) device?.name ?: "unnamed" else "unnamed"
                        } catch (_: Exception) { "unnamed" }
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        foundName = name
                        Log.d(TAG, "BT scan: device found → \"$name\" (RSSI: $rssi dBm)")
                        latch.countDown()
                    }
                    android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d(TAG, "BT scan: discovery finished, no device found")
                        latch.countDown()
                    }
                }
            }
        }

        return try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            val started = adp.startDiscovery()
            if (!started) {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                Log.d(TAG, "BT scan: startDiscovery() returned false")
                return 0f
            }

            latch.await(SCAN_MS, TimeUnit.MILLISECONDS)
            adp.cancelDiscovery()
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}

            val score = if (foundName.isNotEmpty()) SCORE_SCAN else 0f
            Log.d(TAG, "BT scan: result → found=${foundName.isNotEmpty()} name=\"$foundName\" score=$score")
            score

        } catch (e: SecurityException) {
            Log.e(TAG, "BT scan: SecurityException — ${e.message}")
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            -1f
        } catch (e: Exception) {
            Log.e(TAG, "BT scan: error — ${e.message}")
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            0f
        }
    }

    // ── Permission helpers ─────────────────────────────────────────────────────

    private fun hasPermissions(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        has(Manifest.permission.BLUETOOTH_SCAN) && has(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        has(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            has(Manifest.permission.BLUETOOTH_CONNECT)
        else true

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
