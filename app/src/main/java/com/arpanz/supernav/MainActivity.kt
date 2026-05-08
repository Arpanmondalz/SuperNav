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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusCard: TextView

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val rawData = intent?.getStringExtra("raw_data") ?: return
            statusCard.text = rawData
            statusCard.setTextColor(0xFF03DAC5.toInt())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while the app is open — critical for in-pocket use
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val darkBg = 0xFF121212.toInt()
        val accentColor = 0xFF03DAC5.toInt()
        val cardColor = 0xFF1E1E1E.toInt()
        val whiteText = 0xFFFFFFFF.toInt()

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isFillViewport = true
            setBackgroundColor(darkBg)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 120, 64, 64)
            gravity = Gravity.CENTER_HORIZONTAL
            fitsSystemWindows = true
        }
        scrollView.addView(root)

        val titleView = TextView(this).apply {
            text = "SUPER NAV"
            textSize = 28f
            setTextColor(accentColor)
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setPadding(0, 0, 0, 80)
        }
        root.addView(titleView)

        statusCard = TextView(this).apply {
            text = "Ready to Sync"
            setTextColor(whiteText)
            textSize = 16f
            background = getRoundedDrawable(cardColor, 24f)
            setPadding(48, 60, 48, 60)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 80)
            }
        }
        root.addView(statusCard)

        fun createStyledButton(label: String, isPrimary: Boolean = false, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                setTextColor(if (isPrimary) darkBg else whiteText)
                background = if (isPrimary) getRoundedDrawable(accentColor, 50f) else getRoundedDrawable(cardColor, 50f)
                typeface = Typeface.DEFAULT_BOLD
                setOnClickListener { onClick() }
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 150).apply {
                    setMargins(0, 20, 0, 20)
                }
            }
        }

        val btnPerm = createStyledButton("1. GRANT PERMISSIONS") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val btnBattery = createStyledButton("2. DISABLE BATTERY OPTIMIZATION") {
            // Without this, Android kills the listener service after ~1 min in background
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } else {
                    statusCard.text = "Battery optimization\nalready disabled ✓"
                    statusCard.setTextColor(0xFF03DAC5.toInt())
                }
            }
        }

        val btnConnect = createStyledButton("3. CONNECT HUD", true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ), 1)
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
            BleManager.connect(this@MainActivity)
            statusCard.text = "Searching for SuperNav..."
            statusCard.setTextColor(0xFFFFFFFF.toInt())
        }

        root.addView(btnPerm)
        root.addView(btnBattery)
        root.addView(btnConnect)

        // Diagnostics toggle — kept for developer use
        val prefs = getSharedPreferences("SuperNavPrefs", Context.MODE_PRIVATE)

        val settingsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getRoundedDrawable(cardColor, 24f)
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(0, 40, 0, 40)
            }
        }

        val settingsTitle = TextView(this).apply {
            text = "Developer Settings"
            setTextColor(0xFF888888.toInt())
            textSize = 12f
            setPadding(0, 0, 0, 30)
        }

        val diagToggle = Switch(this).apply {
            text = "Enable Diagnostics Dump"
            setTextColor(whiteText)
            isChecked = prefs.getBoolean("DIAGNOSTICS_MODE", false)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("DIAGNOSTICS_MODE", isChecked).apply()
                statusCard.text = if (isChecked) "Diagnostics Active.\nWaiting for Maps..." else "Diagnostics Disabled."
                statusCard.setTextColor(0xFFFFFFFF.toInt())
            }
        }

        settingsLayout.addView(settingsTitle)
        settingsLayout.addView(diagToggle)
        root.addView(settingsLayout)

        setContentView(scrollView)

        val filter = IntentFilter("MapsDataUpdate")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dataReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(dataReceiver, filter)
        }
    }

    private fun getRoundedDrawable(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(dataReceiver) } catch (e: Exception) {}
    }
}