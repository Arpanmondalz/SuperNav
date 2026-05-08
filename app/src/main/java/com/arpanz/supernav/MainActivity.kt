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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
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

        // 2. Base Layout Setup (Scrollview wrapping a LinearLayout)
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

        // 3. Header Title
        val titleView = TextView(this).apply {
            text = "SUPER NAV"
            textSize = 28f
            setTextColor(accentColor)
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setPadding(0, 0, 0, 80)
        }
        root.addView(titleView)

        // 4. Status Card
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

        // 6. Settings & Diagnostics UI
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
            text = "Advanced Developer Settings"
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
                if (isChecked) {
                    statusCard.text = "Diagnostics Active.\nWaiting for Maps..."
                } else {
                    statusCard.text = "Diagnostics Disabled."
                }
            }
        }

        val distInput = EditText(this).apply {
            hint = "Distance Key (e.g. android.title)"
            setHintTextColor(0xFF555555.toInt())
            setTextColor(accentColor)
            setText(prefs.getString("KEY_DISTANCE", "android.title"))
            textSize = 14f
        }

        val instInput = EditText(this).apply {
            hint = "Instruction Key (e.g. android.text)"
            setHintTextColor(0xFF555555.toInt())
            setTextColor(accentColor)
            setText(prefs.getString("KEY_INSTRUCTION", "android.text"))
            textSize = 14f
        }

        val btnSaveSettings = createStyledButton("SAVE KEYS", false) {
            prefs.edit()
                .putString("KEY_DISTANCE", distInput.text.toString().trim())
                .putString("KEY_INSTRUCTION", instInput.text.toString().trim())
                .apply()
            statusCard.text = "Keys Saved successfully!"
        }

        settingsLayout.addView(settingsTitle)
        settingsLayout.addView(diagToggle)
        settingsLayout.addView(distInput)
        settingsLayout.addView(instInput)
        settingsLayout.addView(btnSaveSettings)

        root.addView(settingsLayout)

        // Set the active view to our scrollable container
        setContentView(scrollView)

        // 7. Register Broadcast Receiver
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
        try {
            unregisterReceiver(dataReceiver)
        } catch (e: Exception) {
            // Receiver already unregistered
        }
    }
}