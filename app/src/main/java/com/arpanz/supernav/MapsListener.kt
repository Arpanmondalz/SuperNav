package com.arpanz.supernav // **MAKE SURE THIS MATCHES YOUR PACKAGE NAME**

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MapsListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.google.android.apps.maps") {
            val extras = sbn.notification.extras

            // 1. Extract the specific strings (using .toString() because they are often stored as formatted text)
            val distance = extras.getCharSequence("android.title")?.toString() ?: ""
            val instruction = extras.getCharSequence("android.text")?.toString() ?: ""

            // Ignore empty notifications (like "Maps is running in background")
            if (distance.isEmpty() || instruction.isEmpty()) return

            // 2. Convert instruction to our shortcode
            val code = getManeuverCode(instruction)

            // 3. Create the exact payload we will send over Bluetooth
            val payload = "$code|$distance"

            BleManager.sendData(payload)

            // 4. Broadcast it to the MainActivity
            val intent = Intent("MapsDataUpdate")
            intent.putExtra("raw_data", payload)
            intent.setPackage(applicationContext.packageName) // Security rule
            sendBroadcast(intent)
        }
    }

    // This function checks the Google Maps text and returns our ESP32 shortcode
    private fun getManeuverCode(instruction: String): String {
        val lowerText = instruction.lowercase()

        // Order matters! Check for specific infrastructure before generic left/right turns
        return when {
            "flyover" in lowerText || "bridge" in lowerText -> "FO"
            "slip road" in lowerText || "service road" in lowerText -> "SVR"
            "roundabout" in lowerText -> "RA"
            "u-turn" in lowerText -> "UT"
            "sharp right" in lowerText -> "SHR"
            "sharp left" in lowerText -> "SHL"
            "slight right" in lowerText -> "SR"
            "slight left" in lowerText -> "SL"
            "turn right" in lowerText -> "TR"
            "turn left" in lowerText -> "TL"
            "keep right" in lowerText -> "KR"
            "keep left" in lowerText -> "KL"
            "merge" in lowerText -> "MG"
            "exit" in lowerText || "ramp" in lowerText -> "EX"
            "head" in lowerText || "continue" in lowerText || "straight" in lowerText -> "ST"
            "arrive" in lowerText || "destination" in lowerText -> "DEST"
            else -> "UNK" // Unknown instruction
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}