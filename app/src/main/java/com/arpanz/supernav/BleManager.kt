package com.arpanz.supernav

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID

object BleManager {

    private val SERVICE_UUID        = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private const val SCAN_TIMEOUT  = 15_000L
    private const val RETRY_DELAY   = 3_000L

    enum class State { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

    var state = State.DISCONNECTED
        private set(v) { field = v; broadcastState(v) }

    private var appContext: Context?                      = null
    private var gatt:       BluetoothGatt?               = null
    private var writeChar:  BluetoothGattCharacteristic? = null
    private var autoConnect = false
    private val handler =   Handler(Looper.getMainLooper())
    private var scanner:    BluetoothLeScanner?          = null

    // -------------------------------------------------------------------------
    // Permission helper — centralised check for BLUETOOTH_CONNECT
    // -------------------------------------------------------------------------
    private fun hasBluetoothPermission(): Boolean {
        val ctx = appContext ?: return false
        // BLUETOOTH_CONNECT is only required on Android 12+
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // -------------------------------------------------------------------------
    // Scan callback
    // -------------------------------------------------------------------------
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.removeCallbacksAndMessages(null)
            // Add permission check before stopping scan
            try {
                scanner?.stopScan(this)
            } catch (e: SecurityException) {
                // Permission denied, but we already got a result, so continue
            }

            if (!hasBluetoothPermission()) {
                state = State.DISCONNECTED
                return
            }
            state = State.CONNECTING
            try {
                gatt = result.device.connectGatt(
                    appContext!!, false, gattCallback, BluetoothDevice.TRANSPORT_LE
                )
            } catch (e: SecurityException) {
                state = State.DISCONNECTED
                scheduleRetry()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            state = State.DISCONNECTED
            scheduleRetry()
        }
    }

    // -------------------------------------------------------------------------
    // GATT callback
    // -------------------------------------------------------------------------
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    state = State.CONNECTED
                    try {
                        g.discoverServices()
                    } catch (e: SecurityException) {
                        closeGatt(g)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    closeGatt(g)
                    state = State.DISCONNECTED
                    scheduleRetry()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeChar = g.getService(SERVICE_UUID)
                    ?.getCharacteristic(CHARACTERISTIC_UUID)
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt(g)
                state = State.DISCONNECTED
                scheduleRetry()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun start(context: Context) {
        appContext = context.applicationContext
        autoConnect = true
        if (state == State.DISCONNECTED) scan()
    }

    fun stop() {
        autoConnect = false
        handler.removeCallbacksAndMessages(null)
        try { scanner?.stopScan(scanCallback) } catch (e: SecurityException) {}
        gatt?.let { closeGatt(it) }
        writeChar = null
        state = State.DISCONNECTED
    }

    fun sendData(payload: String) {
        val c = writeChar ?: return
        val g = gatt     ?: return
        if (state != State.CONNECTED) return
        if (!hasBluetoothPermission()) return

        try {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // New API — Android 13+, no deprecation warning
                g.writeCharacteristic(
                    c,
                    bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
            } else {
                // Legacy API — Android 12 and below
                @Suppress("DEPRECATION")
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                c.value = bytes
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
        } catch (e: SecurityException) {
            // Permission was revoked mid-session
            closeGatt(g)
            state = State.DISCONNECTED
            scheduleRetry()
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun scan() {
        val ctx = appContext ?: return
        if (state != State.DISCONNECTED) return
        if (!hasBluetoothPermission()) return

        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter ?: return

        if (!adapter.isEnabled) { scheduleRetry(); return }

        state = State.SCANNING
        scanner = adapter.bluetoothLeScanner

        val filter   = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            state = State.DISCONNECTED
            return
        }

        // Safety timeout — if no device found within 15s, retry
        handler.postDelayed({
            if (state == State.SCANNING) {
                try { scanner?.stopScan(scanCallback) } catch (e: SecurityException) {}
                state = State.DISCONNECTED
                scheduleRetry()
            }
        }, SCAN_TIMEOUT)
    }

    private fun closeGatt(g: BluetoothGatt) {
        try { g.close() } catch (e: SecurityException) {}
        if (gatt == g) {
            gatt = null
            writeChar = null
        }
    }

    private fun scheduleRetry() {
        if (!autoConnect) return
        handler.postDelayed({
            if (state == State.DISCONNECTED) scan()
        }, RETRY_DELAY)
    }

    private fun broadcastState(s: State) {
        val ctx = appContext ?: return
        Intent("BleStatusUpdate").also {
            it.putExtra("ble_state", s.name)
            it.putExtra("ble_label", when (s) {
                State.CONNECTED    -> "● HUD Connected"
                State.SCANNING     -> "◌ Scanning..."
                State.CONNECTING   -> "◌ Connecting..."
                State.DISCONNECTED -> "○ HUD Disconnected"
            })
            it.setPackage(ctx.packageName)
            ctx.sendBroadcast(it)
        }
    }
}