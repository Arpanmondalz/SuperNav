package com.arpanz.supernav

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.*

class MainActivity : Activity() {

    private val darkBg = 0xFF121212.toInt()
    private val accent = 0xFF03DAC5.toInt()
    private val card   = 0xFF1E1E1E.toInt()
    private val white  = 0xFFFFFFFF.toInt()
    private val red    = 0xFFCF6679.toInt()
    private val yellow = 0xFFFFB300.toInt()
    private val grey   = 0xFF888888.toInt()

    private lateinit var bleDot:      TextView
    private lateinit var osmDot:      TextView
    private lateinit var navDataText: TextView
    private lateinit var btnSync:     Button
    private var isRunning = false

    // Receives navigation data from OsmAndConnector
    private val navReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val text = intent?.getStringExtra("nav_text") ?: return
            navDataText.text = text

            // Update the OsmAnd status dot based on the incoming messages
            when {
                text.contains("Connected ✓") || text.contains("→") -> {
                    osmDot.text = "● OsmAnd Connected"
                    osmDot.setTextColor(accent)
                }
                text.contains("disconnected", ignoreCase = true) || text.contains("not found") -> {
                    osmDot.text = "○ OsmAnd Disconnected"
                    osmDot.setTextColor(red)
                }
                text.contains("Start a route") -> {
                    osmDot.text = "◌ Waiting for Route..."
                    osmDot.setTextColor(yellow)
                }
            }
        }
    }

    // Receives BLE connection state from BleManager
    private val bleReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val label = intent?.getStringExtra("ble_label") ?: return
            val state = intent.getStringExtra("ble_state") ?: return
            bleDot.text = label
            bleDot.setTextColor(when (state) {
                "CONNECTED"    -> accent
                "DISCONNECTED" -> red
                else           -> yellow
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(darkBg)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 120, 64, 64)
            gravity = Gravity.CENTER_HORIZONTAL
            fitsSystemWindows = true
        }
        scroll.addView(root)

        // Title
        root.addView(label("SUPER NAV", 28f, accent,
            Typeface.create("sans-serif-condensed", Typeface.BOLD), pad = 8))

        // Status dots
        bleDot = label("○ HUD Disconnected", 13f, red, pad = 4)
        osmDot = label("○ OsmAnd Disconnected", 13f, red, pad = 32)
        root.addView(bleDot)
        root.addView(osmDot)

        // Nav data card
        root.addView(card("LIVE NAVIGATION") {
            navDataText = label("Press Start to begin.", 15f, white)
            addView(navDataText)
        })

        // Start / Stop button
        btnSync = button("▶  START", primary = true) { toggleSync() }
        root.addView(btnSync)

        // One-time setup buttons
        root.addView(button("DISABLE BATTERY OPTIMIZATION") { requestBatteryExemption() })

        setContentView(scroll)

        register(navReceiver, "NavDataUpdate")
        register(bleReceiver, "BleStatusUpdate")
    }

    private fun toggleSync() {
        isRunning = !isRunning
        if (isRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ), 1)
            }
            BleManager.start(this)
            val serviceIntent = Intent(this, SuperNavService::class.java)
            startForegroundService(serviceIntent)
            btnSync.text = "■  STOP"
            btnSync.background = rounded(red, 50f)
            osmDot.text = "◌ Connecting to OsmAnd..."
            osmDot.setTextColor(yellow)
        } else {
            BleManager.stop()
            val serviceIntent = Intent(this, SuperNavService::class.java)
            stopService(serviceIntent)
            btnSync.text = "▶  START"
            btnSync.background = rounded(accent, 50f)
            bleDot.text = "○ HUD Disconnected"; bleDot.setTextColor(red)
            osmDot.text = "○ OsmAnd Disconnected"; osmDot.setTextColor(red)
            navDataText.text = "Stopped."
        }
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } else {
                navDataText.text = "Battery optimization already disabled ✓"
            }
        }
    }

    // -------------------------------------------------------------------------
    // UI helpers (kept minimal)
    // -------------------------------------------------------------------------

    private fun label(
        txt: String, size: Float, color: Int,
        face: Typeface = Typeface.DEFAULT, pad: Int = 0
    ) = TextView(this).apply {
        text = txt; textSize = size; setTextColor(color); typeface = face
        if (pad > 0) setPadding(0, 0, 0, pad)
    }

    private fun button(lbl: String, primary: Boolean = false, onClick: () -> Unit) =
        Button(this).apply {
            text = lbl
            setTextColor(if (primary) darkBg else white)
            background = rounded(if (primary) accent else card, 50f)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 150).apply {
                setMargins(0, 20, 0, 20)
            }
        }

    private fun card(title: String, block: LinearLayout.() -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(card, 24f)
            setPadding(48, 40, 48, 40)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 32)
            }
            addView(label(title, 11f, grey, Typeface.DEFAULT_BOLD, pad = 16))
            block()
        }

    private fun rounded(color: Int, r: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = r; setColor(color)
    }

    private fun register(receiver: BroadcastReceiver, action: String) {
        val f = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        else
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, f)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(navReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(bleReceiver) } catch (e: Exception) {}
    }
}