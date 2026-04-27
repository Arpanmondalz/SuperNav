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
        // Order matters! Check for specific infrastructure before generic left/right turns
        val text = instruction.lowercase()

        // 1. System Alerts (Highest Priority)
        if (text.contains("rerouting")) return "RER"
        if (text.contains("gps") || text.contains("lost")) return "GPS"

        // 2. Destination
        if (text.contains("arrive") || text.contains("destination")) return "DEST"

        // 3. Complex Maneuvers
        if (text.contains("u-turn") || text.contains("u turn")) {
            return if (text.contains("left")) "UTL" else "UTR"
        }
        if (text.contains("roundabout")) {
            return if (text.contains("left")) "RAL" else "RAR"
        }

        // 4. Modifiers (Must check before basic left/right)
        if (text.contains("sharp left")) return "SHL"
        if (text.contains("sharp right")) return "SHR"
        if (text.contains("slight left") || text.contains("keep left")) return "SL"
        if (text.contains("slight right") || text.contains("keep right")) return "SR"

        // 5. Basic Turns (This catches "left on North Ave" and exits safely!)
        if (text.contains("left")) return "TL"
        if (text.contains("right")) return "TR"

        // 6. Infrastructure
        if (text.contains("merge")) return "MG"
        if (text.contains("exit")) return "EX"
        if (text.contains("flyover") || text.contains("overpass")) return "FO"
        if (text.contains("service road") || text.contains("slip road")) return "SVR"

        // 7. Straight
        if (text.contains("straight") || text.contains("continue")) return "ST"

        // 8. Compass Directions (Lowest priority so street names don't trigger them)
        if (text.contains("northwest") || text.contains("north-west")) return "NW";
        if (text.contains("northeast") || text.contains("north-east")) return "NE";
        if (text.contains("southwest") || text.contains("south-west")) return "SW";
        if (text.contains("southeast") || text.contains("south-east")) return "SE";
        if (text.contains("north")) return "N";
        if (text.contains("south")) return "S";
        if (text.contains("east")) return "E";
        if (text.contains("west")) return "W";

        // 9. FALLBACK
        return "UNK"

    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}