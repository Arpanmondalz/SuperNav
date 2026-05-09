package com.arpanz.supernav

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent

import net.osmand.aidlapi.IOsmAndAidlCallback
import net.osmand.aidlapi.IOsmAndAidlInterface
import net.osmand.aidlapi.gpx.AGpxBitmap
import net.osmand.aidlapi.navigation.ADirectionInfo
import net.osmand.aidlapi.navigation.ANavigationUpdateParams
import net.osmand.aidlapi.navigation.OnVoiceNavigationParams
import net.osmand.aidlapi.search.SearchResult

object OsmAndConnector {

    private const val TAG = "OsmAndConnector"
    private val OSMAND_PACKAGES = listOf("net.osmand", "net.osmand.plus")
    private const val OSMAND_SERVICE = "net.osmand.aidl.OsmandAidlServiceV2"

    private var aidlInterface: IOsmAndAidlInterface? = null
    private var callbackId: Long = -1L
    private var appContext: Context? = null
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    //Keep track of the last thing we sent
    private var lastSentPayload: String = ""

    // THIS WILL SHOW RED TEXT INITIALLY - WE WILL FIX IT IN STEP 2!
    private val aidlCallback = object : IOsmAndAidlCallback.Stub() {
        override fun onSearchComplete(resultSet: List<SearchResult?>?) {
            TODO("Not yet implemented")
        }

        override fun onUpdate() {
            TODO("Not yet implemented")
        }

        override fun onAppInitialized() {
            TODO("Not yet implemented")
        }

        override fun onGpxBitmapCreated(bitmap: AGpxBitmap?) {
            TODO("Not yet implemented")
        }

        override fun updateNavigationInfo(directionInfo: ADirectionInfo?) {
            directionInfo ?: return

            val code = turnTypeToCode(directionInfo.turnType)
            val distance = formatDistance(directionInfo.distanceTo)

            val payload = "$code|$distance"

            // Only send data to BLE/UI if the payload actually changed!
            if (payload != lastSentPayload) {
                lastSentPayload = payload
                BleManager.sendData(payload)
                broadcastToUi("→ $code  |  $distance")
                Log.d(TAG, "Sent to OLED: $payload")
            }
        }

        override fun onContextMenuButtonClicked(
            buttonId: Int,
            pointId: String?,
            layerId: String?
        ) {
        }

        override fun onVoiceRouterNotify(params: OnVoiceNavigationParams?) {
        }

        override fun onKeyEvent(params: KeyEvent?) {
        }


    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "✓ Service connected: $name")
            aidlInterface = IOsmAndAidlInterface.Stub.asInterface(binder)
            subscribeToNavigation()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "✗ Service disconnected")
            aidlInterface = null
            callbackId = -1L
            broadcastToUi("OsmAnd disconnected.\nRetrying...")
            if (isRunning) {
                handler.postDelayed({ bindToOsmAnd() }, 5_000L)
            }
        }
    }

    fun start(context: Context) {
        if (isRunning) return
        Log.d(TAG, "▶ Starting OsmAndConnector")
        appContext = context.applicationContext
        isRunning = true

        acquireWakeLock()
        bindToOsmAnd()
    }

    fun stop() {
        if (!isRunning) return
        Log.d(TAG, "■ Stopping OsmAndConnector")
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        unsubscribeFromNavigation()
        try {
            appContext?.unbindService(serviceConnection)
        } catch (e: Exception) {
            Log.d(TAG, "Unbind failed")
        }
        aidlInterface = null
        callbackId = -1L
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = appContext?.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SuperNav::ConnectorWakeLock"
        ).apply {
            Log.d(TAG, "Acquiring WakeLock")
            acquire(2 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            Log.d(TAG, "Releasing WakeLock")
            wakeLock?.release()
            wakeLock = null
        }
    }

    private fun bindToOsmAnd() {
        val ctx = appContext ?: return
        Log.d(TAG, "Attempting to bind to OsmAnd...")
        for (pkg in OSMAND_PACKAGES) {
            val intent = Intent().apply { component = ComponentName(pkg, OSMAND_SERVICE) }
            try {
                if (ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
                    Log.d(TAG, "✓ Bind initiated for $pkg")
                    broadcastToUi("Binding to OsmAnd...")
                    return
                }
            } catch (e: Exception) { continue }
        }
        Log.e(TAG, "✗ OsmAnd not found on device")
        broadcastToUi("OsmAnd not found.")
        if (isRunning) {
            handler.postDelayed({ bindToOsmAnd() }, 5_000L)
        }
    }

    private fun subscribeToNavigation() {
        Log.d(TAG, "Attempting subscription...")
        try {
            // FIXED: Using the empty constructor and apply block
            val params = ANavigationUpdateParams().apply {
                callbackId = -1L
                subscribeToUpdates = true
            }
            val iface = aidlInterface ?: return

            callbackId = iface.registerForNavigationUpdates(params, aidlCallback)

            Log.d(TAG, "OsmAnd response: callbackId=$callbackId")
            when {
                callbackId >= 0L -> {
                    Log.d(TAG, "SUCCESS: Subscribed with id=$callbackId")
                    broadcastToUi("OsmAnd Connected ✓\nReceiving navigation data...")
                }
                else -> {
                    Log.w(TAG, "REJECTED: OsmAnd returned 0 or -1. Is navigation active?")
                    broadcastToUi("OsmAnd Connected.\nStart a route in OsmAnd, then\nre-toggle START here.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during subscription: ${e.message}")
        }
    }

    private fun unsubscribeFromNavigation() {
        val id = callbackId
        if (id > 0) {
            Log.d(TAG, "Unsubscribing callbackId=$id")
            try {
                // FIXED: Using the empty constructor and apply block
                val params = ANavigationUpdateParams().apply {
                    callbackId = id
                    subscribeToUpdates = false
                }
                aidlInterface?.registerForNavigationUpdates(params, aidlCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Unsubscribe error: ${e.message}")
            }
            callbackId = -1L
        }
    }

    // OsmAnd TurnType integer → Custom firmware codes (INDIA / Left-Hand Traffic)
    private fun turnTypeToCode(type: Int): String = when (type) {
        1  -> "ST"    // C    = Continue straight
        2  -> "TL"    // TL   = Turn left (90°)
        3  -> "SL"    // TSLL = Slight left (~15–30°)
        4  -> "SHL"   // TSHL = Sharp left (~120–150°)
        5  -> "TR"    // TR   = Turn right (90°)
        6  -> "SR"    // TSLR = Slight right (~15–30°)
        7  -> "SHR"   // TSHR = Sharp right (~120–150°)
        8  -> "SL"    // KL   = Keep left (lane fork) — common on Indian highways
        9  -> "SR"    // KR   = Keep right (lane fork)
        10 -> "UTL"   // TU   = U-turn LEFT (rare in India — appears only if route crosses into RHD logic)
        11 -> "UTR"   // TRU  = U-turn RIGHT ⭐ STANDARD U-TURN IN INDIA
        12 -> "MG"    // OFFR = Off-route / merge fallback
        13 -> "RAR"   // RNDB = Counter-clockwise roundabout (rare in India)
        14 -> "RAL"   // RNLB = Clockwise roundabout ⭐ STANDARD ROUNDABOUT IN INDIA
        else -> "UNK"
    }

    private fun formatDistance(metres: Int): String = when {
        metres <= 10 -> {
            val roundedDown = 0
            "$roundedDown m"
        }
        metres < 1000 -> {
            // Round down to nearest 10m
            val roundedDown = (metres / 10) * 10
            "$roundedDown m"
        }
        else -> {
            // Over 1km: Show 1 decimal place (e.g., 1.2 km)
            "${"%.1f".format(metres / 1000.0)} km"
        }
    }

    private fun broadcastToUi(message: String) {
        val ctx = appContext ?: return
        Intent("NavDataUpdate").also {
            it.putExtra("nav_text", message)
            it.setPackage(ctx.packageName)
            ctx.sendBroadcast(it)
        }
    }
}