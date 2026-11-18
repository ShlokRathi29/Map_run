package app.stridelocal.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.stridelocal.StrideLocalApp
import app.stridelocal.data.local.RunEntity
import app.stridelocal.data.local.LapEntity
import app.stridelocal.data.local.HeartRateSampleEntity
import app.stridelocal.data.local.TrackPointEntity
import app.stridelocal.sensors.BluetoothHeartRateConnection
import kotlinx.coroutines.*

class RunTrackingService : Service(), LocationListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private lateinit var locationManager: LocationManager
    private val points = mutableListOf<Location>()
    private var startedAt = 0L
    private var pausedAt = 0L
    private var pausedDurationMs = 0L
    private var distanceMeters = 0.0
    private var elevationGainMeters = 0.0
    private var lastAltitude: Double? = null
    private var autoPaused = false
    private var autoPausedAt = 0L
    private var totalAutoPausedMs = 0L
    private var runId = 0L
    private var nextSequence = 0L
    private var finishPending = false
    private var lapSequence = 0
    private var lapStartedAtEpochMs = 0L
    private var lapElapsedMs = 0L
    private var lapStartDistanceMeters = 0.0
    private var autoPauseEnabled = true
    private var audioCuesEnabled = true
    private var nextDistanceCueMeters = 1000.0
    private var toneGenerator: ToneGenerator? = null
    private var heartRateConnection: BluetoothHeartRateConnection? = null
    private var heartRateSequence = 0L
    private var latestHeartRate = 0

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_LAP -> recordLap()
            ACTION_FINISH -> finishTracking()
        }
        return START_NOT_STICKY
    }

    private fun startTracking() {
        autoPauseEnabled = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(PREF_AUTO_PAUSE, true)
        audioCuesEnabled = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(PREF_AUDIO_CUES, true)
        if (startedAt == 0L) {
            startedAt = System.currentTimeMillis()
            lapStartedAtEpochMs = startedAt
            nextDistanceCueMeters = 1000.0
            val dao = (application as StrideLocalApp).database.runDao()
            serviceScope.launch {
                runId = dao.insertRun(RunEntity(startedAtEpochMs = startedAt, endedAtEpochMs = null,
                    durationSeconds = 0L, distanceMeters = 0.0, elevationGainMeters = 0.0))
                connectHeartRate()
                if (finishPending) withContext(Dispatchers.Main) { finishTracking() }
                else requestGpsUpdates()
            }
            startForeground(NOTIFICATION_ID, notification("Run in progress"))
            return
        }
        startForeground(NOTIFICATION_ID, notification("Run in progress"))
        requestGpsUpdates()
    }

    private fun requestGpsUpdates() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, mainLooper)
        } catch (_: SecurityException) { stopSelf() }
    }

    private fun connectHeartRate() {
        val prefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        val address = prefs.getString(PREF_HEART_RATE_ADDRESS, null) ?: return
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        heartRateConnection?.close()
        heartRateConnection = BluetoothHeartRateConnection(this, address) { bpm, time ->
            if (runId == 0L || pausedAt != 0L || autoPaused) return@BluetoothHeartRateConnection
            latestHeartRate = bpm
            val sampleSequence = heartRateSequence++
            val activeRunId = runId
            serviceScope.launch {
                (application as StrideLocalApp).database.runDao().insertHeartRateSample(
                    HeartRateSampleEntity(activeRunId, sampleSequence, time, bpm, "Bluetooth sensor"),
                )
            }
            sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_HEART_RATE, bpm))
        }
    }

    private fun pauseTracking() {
        if (pausedAt != 0L) return
        pausedAt = System.currentTimeMillis()
        locationManager.removeUpdates(this)
        publishStatus()
        updateNotification("Run paused")
    }

    private fun resumeTracking() {
        if (pausedAt != 0L) {
            val pauseDuration = System.currentTimeMillis() - pausedAt
            pausedDurationMs += pauseDuration
        }
        pausedAt = 0L
        startTracking()
    }

    private fun recordLap() {
        if (runId == 0L || startedAt == 0L) return
        val now = System.currentTimeMillis()
        val currentElapsed = activeElapsedMs(now)
        val lapSeconds = ((currentElapsed - lapElapsedMs).coerceAtLeast(0) / 1000)
        val lapDistance = (distanceMeters - lapStartDistanceMeters).coerceAtLeast(0.0)
        val dao = (application as StrideLocalApp).database.runDao()
        val sequence = lapSequence++
        serviceScope.launch {
            dao.insertLap(LapEntity(runId, sequence, lapStartedAtEpochMs, lapSeconds, lapDistance))
        }
        lapStartedAtEpochMs = now
        lapElapsedMs = currentElapsed
        lapStartDistanceMeters = distanceMeters
        updateNotification("Lap ${sequence + 1} saved")
        if (audioCuesEnabled) toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
    }

    private fun activeElapsedMs(now: Long): Long {
        val manualPause = if (pausedAt != 0L) now - pausedAt else 0L
        val automaticPause = if (autoPaused && autoPausedAt != 0L) now - autoPausedAt else 0L
        return (now - startedAt - pausedDurationMs - totalAutoPausedMs - manualPause - automaticPause).coerceAtLeast(0)
    }

    override fun onLocationChanged(location: Location) {
        if (location.accuracy > 35f) return
        val previous = points.lastOrNull()
        val speed = location.speed.takeIf { location.hasSpeed() } ?: 0f
        val now = System.currentTimeMillis()
        if (autoPauseEnabled && speed < 0.7f) {
            if (autoPausedAt == 0L) autoPausedAt = now
            autoPaused = now - autoPausedAt >= 8000L
        } else if (autoPausedAt != 0L) {
            if (autoPaused) totalAutoPausedMs += now - autoPausedAt
            autoPausedAt = 0L
            autoPaused = false
        }
        if (autoPaused) {
            publishStatus()
            return
        }
        if (previous != null) {
            val delta = previous.distanceTo(location).toDouble()
            if (delta in 1.0..100.0) distanceMeters += delta
        }
        if (audioCuesEnabled && distanceMeters >= nextDistanceCueMeters) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 220)
            nextDistanceCueMeters = (distanceMeters / 1000.0).toInt() * 1000.0 + 1000.0
        }
        if (location.hasAltitude()) {
            lastAltitude?.let { prior -> if (location.altitude > prior + 2.0) elevationGainMeters += location.altitude - prior }
            lastAltitude = location.altitude
        }
        points += location
        val db = (application as StrideLocalApp).database.runDao()
        val sequence = nextSequence++
        val activeElapsedNanos = activeElapsedMs(now) * 1_000_000L
        serviceScope.launch {
            // Persist each GPS fix immediately so a process interruption does not lose the route.
            db.insertPoint(
                TrackPointEntity(
                    runId = runId, sequence = sequence, latitude = location.latitude,
                    longitude = location.longitude, altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
                    accuracyMeters = location.accuracy, activeElapsedNanos = activeElapsedNanos,
                    timestampEpochMs = location.time,
                )
            )
        }
        publishStatus()
        updateNotification("${"%.2f".format(distanceMeters / 1000.0)} km recorded")
    }

    private fun finishTracking() {
        if (startedAt == 0L) return
        if (runId == 0L) { finishPending = true; return }
        locationManager.removeUpdates(this)
        val endedAt = System.currentTimeMillis()
        val elapsed = (activeElapsedMs(endedAt) / 1000)
        heartRateConnection?.close()
        heartRateConnection = null
        val activeRunId = runId
        val activeStart = startedAt
        val activeDistance = distanceMeters
        val activeElevation = elevationGainMeters
        serviceScope.launch {
            val dao = (application as StrideLocalApp).database.runDao()
            dao.updateRun(RunEntity(id = activeRunId, startedAtEpochMs = activeStart, endedAtEpochMs = endedAt,
                durationSeconds = elapsed, distanceMeters = activeDistance, elevationGainMeters = activeElevation))
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        startedAt = 0L; pausedAt = 0L; pausedDurationMs = 0L; totalAutoPausedMs = 0L; finishPending = false
        distanceMeters = 0.0; points.clear(); runId = 0L; nextSequence = 0L; autoPausedAt = 0L; autoPaused = false
        lapSequence = 0; lapStartedAtEpochMs = 0L; lapElapsedMs = 0L; lapStartDistanceMeters = 0.0; nextDistanceCueMeters = 1000.0
        heartRateSequence = 0L; latestHeartRate = 0
    }

    private fun publishStatus() {
        val now = System.currentTimeMillis()
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_DISTANCE, distanceMeters)
            .putExtra(EXTRA_DURATION, (activeElapsedMs(now) / 1000))
            .putExtra(EXTRA_PAUSED, pausedAt != 0L || autoPaused)
            .putExtra(EXTRA_AUTO_PAUSED, autoPaused)
            .putExtra(EXTRA_HEART_RATE, latestHeartRate))
    }
    private fun updateNotification(text: String) = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(text))
    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Stride Local").setContentText(text)
        .setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    private fun createChannel() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Run tracking", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onProviderDisabled(provider: String) { updateNotification("GPS unavailable — check location settings") }
    override fun onProviderEnabled(provider: String) = Unit
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { locationManager.removeUpdates(this); heartRateConnection?.close(); toneGenerator?.release(); toneGenerator = null; serviceScope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_START = "app.stridelocal.action.START"
        const val ACTION_PAUSE = "app.stridelocal.action.PAUSE"
        const val ACTION_RESUME = "app.stridelocal.action.RESUME"
        const val ACTION_LAP = "app.stridelocal.action.LAP"
        const val ACTION_FINISH = "app.stridelocal.action.FINISH"
        const val ACTION_STATUS = "app.stridelocal.action.STATUS"
        const val EXTRA_DISTANCE = "distance"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_PAUSED = "paused"
        const val EXTRA_AUTO_PAUSED = "autoPaused"
        const val EXTRA_HEART_RATE = "heartRate"
        const val PREFERENCES = "stride-local-settings"
        const val PREF_AUTO_PAUSE = "auto_pause"
        const val PREF_AUDIO_CUES = "audio_cues"
        const val PREF_HEART_RATE_ADDRESS = "heart_rate_sensor_address"
        const val PREF_HEART_RATE_NAME = "heart_rate_sensor_name"
        private const val CHANNEL_ID = "run-tracking"
        private const val NOTIFICATION_ID = 21
    }
}
