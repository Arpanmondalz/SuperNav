package com.arpanz.supernav

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusCard: TextView

    // Receiver to catch data from MapsListener.kt
    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val rawData = intent?.getStringExtra("raw_data")
            if (rawData != null) {
                statusCard.text = rawData
                statusCard.setTextColor(0xFF03DAC5.toInt()) // Highlight teal when data arrives
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Theme Colors
        val darkBg = 0xFF121212.toInt()
        val accentColor = 0xFF03DAC5.toInt()
        val cardColor = 0xFF1E1E1E.toInt()
        val whiteText = 0xFFFFFFFF.toInt()

        // 2. Root Layout
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(darkBg)
            setPadding(64, 120, 64, 64)
            gravity = Gravity.CENTER_HORIZONTAL
            fitsSystemWindows = true
        }

        // 3. Header Title
        val titleView = TextView(this).apply {
            text = "SUPER NAV"
            textSize = 28f
            setTextColor(accentColor)
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setPadding(0, 0, 0, 80)
        }
        root.addView(titleView)

        // 4. Status Card (Displays current navigation info)
        statusCard = TextView(this).apply {
            text = "Ready to Sync"
            setTextColor(whiteText)
            textSize = 18f
            background = getRoundedDrawable(cardColor, 24f)
            setPadding(48, 60, 48, 60)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 80)
            }
        }
        root.addView(statusCard)

        // Helper to create styled buttons
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

        // 5. Action Buttons
        val btnPerm = createStyledButton("1. GRANT PERMISSIONS") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val btnConnect = createStyledButton("2. CONNECT HUD", true) {
            // BLE Permissions Check
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
        }

        root.addView(btnPerm)
        root.addView(btnConnect)
        setContentView(root)

        // 6. Register Broadcast Receiver
        val filter = IntentFilter("MapsDataUpdate")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dataReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(dataReceiver, filter)
        }
    }

    // Helper function for rounded corners
    private fun getRoundedDrawable(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(dataReceiver)
        } catch (e: Exception) {
            // Receiver already unregistered
        }
    }
}