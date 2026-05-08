package com.arpanz.supernav

import android.content.Intent
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.regex.Pattern

class MapsListener : NotificationListenerService() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        // Acquire a CPU WakeLock so Doze mode doesn't freeze us mid-navigation
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SuperNav::MapsListenerWakeLock"
        ).also {
            it.acquire(60 * 60 * 1000L) // 1 hour max, re-acquired on each notification
        }

        // Broadcast status so MainActivity knows we're alive
        sendStatusBroadcast("Listener Active.\nWaiting for Maps...")
    }

    // Called by the system when it successfully binds to your listener
    override fun onListenerConnected() {
        super.onListenerConnected()
        sendStatusBroadcast("Listener Connected.\nWaiting for Maps...")
    }

    // Called when the system unbinds — lets you know it happened so you can react in the UI
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        sendStatusBroadcast("Listener Disconnected!\nRe-grant permissions if needed.")
        requestRebind(android.content.ComponentName(this, MapsListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.google.android.apps.maps") return

        val extras = sbn.notification.extras ?: return

        // Re-acquire WakeLock on every notification to keep CPU alive
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(60 * 60 * 1000L)
        }

        // Scrape every CharSequence value from the bundle dynamically
        val allData = StringBuilder()
        for (key in extras.keySet()) {
            @Suppress("DEPRECATION")
            val value = extras.get(key)
            if (value is CharSequence && value.isNotBlank()) {
                allData.append(value).append(" ")
            }
        }

        val masterString = allData.toString()
        if (masterString.isBlank()) return

        val distance = extractDistance(masterString)
        val code = getManeuverCode(masterString)
        val payload = "$code|$distance"

        BleManager.sendData(payload)
        sendStatusBroadcast("Sent: $payload\nScraped: $masterString")
    }

    private fun sendStatusBroadcast(message: String) {
        val intent = Intent("MapsDataUpdate").apply {
            putExtra("raw_data", message)
            setPackage(applicationContext.packageName)
        }
        sendBroadcast(intent)
    }

    private fun extractDistance(input: String): String {
        val pattern = Pattern.compile(
            "(\\d+(\\.\\d+)?\\s*(kilometers|kilometer|meters|meter|km|m|ft|mi|yd))",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(input)
        return if (matcher.find()) matcher.group(0) ?: "" else ""
    }

    private fun getManeuverCode(text: String): String {
        val t = text.lowercase()
        return when {
            t.contains("rerouting") || t.contains("re-routing") || t.contains("calculating") -> "RER"
            t.contains("gps") || t.contains("lost") || t.contains("searching") -> "GPS"
            t.contains("destination") || t.contains("arrived") || t.contains("reached") || t.contains("end") -> "DEST"
            t.contains("sharp left") || t.contains("hard left") -> "SHL"
            t.contains("sharp right") || t.contains("hard right") -> "SHR"
            t.contains("slight left") || t.contains("keep left") || t.contains("bear left") || t.contains("stay left") -> "SL"
            t.contains("slight right") || t.contains("keep right") || t.contains("bear right") || t.contains("stay right") -> "SR"
            t.contains("u-turn") || t.contains("u turn") -> if (t.contains("left")) "UTL" else "UTR"
            t.contains("left") -> "TL"
            t.contains("right") -> "TR"
            t.contains("merge") -> "MG"
            t.contains("exit") || t.contains("take the ramp") -> "EX"
            t.contains("roundabout") -> if (t.contains("left")) "RAL" else "RAR"
            t.contains("straight") || t.contains("continue") -> "ST"
            t.contains("northwest") || t.contains("north-west") || t.contains("north west") -> "NW"
            t.contains("northeast") || t.contains("north-east") || t.contains("north east") -> "NE"
            t.contains("southwest") || t.contains("south-west") || t.contains("south west") -> "SW"
            t.contains("southeast") || t.contains("south-east") || t.contains("south east") -> "SE"
            t.contains("north") -> "N"
            t.contains("south") -> "S"
            t.contains("east") -> "E"
            t.contains("west") -> "W"
            else -> "UNK"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}