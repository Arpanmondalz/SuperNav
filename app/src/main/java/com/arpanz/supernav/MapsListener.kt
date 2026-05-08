package com.arpanz.supernav

import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MapsListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.google.android.apps.maps") {
            val extras = sbn.notification.extras ?: return

            // 1. Load the user's custom settings from memory
            val prefs = applicationContext.getSharedPreferences("SuperNavPrefs", Context.MODE_PRIVATE)
            val distanceKey = prefs.getString("KEY_DISTANCE", "android.title") ?: "android.title"
            val instructionKey = prefs.getString("KEY_INSTRUCTION", "android.text") ?: "android.text"
            val isDiagnosticsOn = prefs.getBoolean("DIAGNOSTICS_MODE", false)

            // 2. DIAGNOSTICS MODE: Dump everything to the screen
            if (isDiagnosticsOn) {
                val dump = StringBuilder("--- DIAGNOSTICS DUMP ---\n\n")
                for (key in extras.keySet()) {
                    @Suppress("DEPRECATION")
                    dump.append("$key : ${extras.get(key)}\n\n")
                }

                val intent = Intent("MapsDataUpdate")
                intent.putExtra("raw_data", dump.toString())
                intent.setPackage(applicationContext.packageName)
                sendBroadcast(intent)
                return
            }

            // 3. NORMAL MODE: Extract the raw strings
            // Based on your dump,
            // android.title contains "Head northeast" and android.text is null.
            val rawDistance = extras.getCharSequence(distanceKey)?.toString() ?: ""
            val rawInstruction = extras.getCharSequence(instructionKey)?.toString() ?: ""

            // Ignore only if BOTH are completely empty
            if (rawDistance.isEmpty() && rawInstruction.isEmpty()) return

            // Google Maps edge case: Combine them so our parser finds the instruction
            // even if it's hiding in the "Title" field.
            val combinedText = "$rawDistance $rawInstruction"
            val code = getManeuverCode(combinedText)

            // If rawInstruction is empty, it means Maps hasn't provided a numerical
            // distance yet (like "Head northeast").
            val finalDistance = if (rawInstruction.isEmpty()) "" else rawDistance

            // 4. Create the exact payload we will send over Bluetooth
            val payload = "$code|$finalDistance"

            // 5. Send to Xiao
            BleManager.sendData(payload)

            // 6. Broadcast to the MainActivity for debugging (Variables corrected here!)
            val intent = Intent("MapsDataUpdate")
            intent.putExtra("raw_data", "Sending Payload: $payload\n\nDebug Info:\nCode: $code\nDist: $finalDistance\nRaw Text: $combinedText")
            intent.setPackage(applicationContext.packageName)
            sendBroadcast(intent)
        }
    }

    private fun getManeuverCode(instruction: String): String {
        val text = instruction.lowercase()

        if (text.contains("rerouting")) return "RER"
        if (text.contains("gps") || text.contains("lost")) return "GPS"
        if (text.contains("arrive") || text.contains("destination")) return "DEST"

        if (text.contains("u-turn") || text.contains("u turn")) {
            return if (text.contains("left")) "UTL" else "UTR"
        }
        if (text.contains("roundabout")) {
            return if (text.contains("left")) "RAL" else "RAR"
        }

        if (text.contains("sharp left")) return "SHL"
        if (text.contains("sharp right")) return "SHR"
        if (text.contains("slight left") || text.contains("keep left")) return "SL"
        if (text.contains("slight right") || text.contains("keep right")) return "SR"

        if (text.contains("left")) return "TL"
        if (text.contains("right")) return "TR"

        if (text.contains("merge")) return "MG"
        if (text.contains("exit")) return "EX"
        if (text.contains("flyover") || text.contains("overpass")) return "FO"
        if (text.contains("service road") || text.contains("slip road")) return "SVR"

        if (text.contains("straight") || text.contains("continue")) return "ST"

        if (text.contains("northwest") || text.contains("north-west")) return "NW"
        if (text.contains("northeast") || text.contains("north-east")) return "NE"
        if (text.contains("southwest") || text.contains("south-west")) return "SW"
        if (text.contains("southeast") || text.contains("south-east")) return "SE"
        if (text.contains("north")) return "N"
        if (text.contains("south")) return "S"
        if (text.contains("east")) return "E"
        if (text.contains("west")) return "W"

        return "UNK"
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}