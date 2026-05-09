package com.arpanz.supernav

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SuperNavService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // This notification tells Android OS NOT to kill the app
        val notification = NotificationCompat.Builder(this, "SuperNavChannel")
            .setContentTitle("SuperNav Connected")
            .setContentText("Bridging OsmAnd to OLED Display")
            .setSmallIcon(android.R.drawable.ic_dialog_map) // Uses default Android icon
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start our OsmAnd and BLE logic here!
        OsmAndConnector.start(this)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        OsmAndConnector.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "SuperNavChannel",
            "SuperNav Background Service",
            NotificationManager.IMPORTANCE_LOW // Low importance so it doesn't beep
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}