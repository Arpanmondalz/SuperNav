package com.arpanz.supernav

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission") // We will request permissions in MainActivity
object BleManager {
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null

    // The exact UUIDs we programmed into the Xiao ESP32C3
    private val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHAR_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    fun connect(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val scanner = adapter.bluetoothLeScanner

        // Scan specifically for the Xiao's name to make connection instant
        val filters = listOf(ScanFilter.Builder().setDeviceName("SuperNav").build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        scanner?.startScan(filters, settings, object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // We found it! Stop scanning to save battery, then connect.
                scanner.stopScan(this)
                bluetoothGatt = result.device.connectGatt(context, false, gattCallback)
            }
        })
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Connected successfully! Now search for its mailboxes (services).
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // Find our specific mailbox characteristic
            val service = gatt.getService(SERVICE_UUID)
            characteristic = service?.getCharacteristic(CHAR_UUID)
        }
    }

    fun sendData(data: String) {
        characteristic?.let {
            // Convert our string to bytes and fire it over the air!
            @Suppress("DEPRECATION")
            it.value = data.toByteArray()

            @Suppress("DEPRECATION")
            bluetoothGatt?.writeCharacteristic(it)
        }
    }
}