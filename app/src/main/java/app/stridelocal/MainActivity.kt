package app.stridelocal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.stridelocal.data.local.RunEntity
import app.stridelocal.data.local.TrainingGoalEntity
import app.stridelocal.data.local.LocalChallengeEntity
import app.stridelocal.data.local.SavedRouteEntity
import app.stridelocal.data.local.LapEntity
import app.stridelocal.data.local.TrackPointEntity
import app.stridelocal.data.local.RoutePointEntity
import app.stridelocal.data.local.HeartRateSampleEntity
import app.stridelocal.analysis.RunAnalysis
import app.stridelocal.analysis.PersonalRecords
import app.stridelocal.analysis.personalRecords
import app.stridelocal.analysis.LocalSegmentAttempt
import app.stridelocal.analysis.SegmentComparison
import app.stridelocal.analysis.TrainingProgress
import app.stridelocal.routes.GpxRouteIO
import app.stridelocal.maps.OfflineMapPack
import app.stridelocal.maps.OfflineMapPacks
import app.stridelocal.maps.OfflineRouteMap
import app.stridelocal.maps.MapPoint
import app.stridelocal.sensors.BluetoothHeartRateScanner
import app.stridelocal.sensors.HeartRateSensor
import app.stridelocal.sensors.HealthConnectHeartRate
import androidx.health.connect.client.PermissionController
import app.stridelocal.tracking.RunTrackingService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { StrideLocalScreen((application as StrideLocalApp).database) } }
    }
}

@Composable
private fun StrideLocalScreen(database: app.stridelocal.data.local.StrideDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tracking by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var autoPaused by remember { mutableStateOf(false) }
    var durationSeconds by remember { mutableLongStateOf(0L) }
    var distanceMeters by remember { mutableDoubleStateOf(0.0) }
    var heartRateBpm by remember { mutableIntStateOf(0) }
    var autoPauseSetting by remember { mutableStateOf(context.getSharedPreferences(RunTrackingService.PREFERENCES, android.content.Context.MODE_PRIVATE).getBoolean(RunTrackingService.PREF_AUTO_PAUSE, true)) }
    var audioCueSetting by remember { mutableStateOf(context.getSharedPreferences(RunTrackingService.PREFERENCES, android.content.Context.MODE_PRIVATE).getBoolean(RunTrackingService.PREF_AUDIO_CUES, true)) }
    var permissionReady by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    var runs by remember { mutableStateOf<List<RunEntity>>(emptyList()) }
    var records by remember { mutableStateOf(PersonalRecords()) }
    var goals by remember { mutableStateOf<List<TrainingGoalEntity>>(emptyList()) }
    var challenges by remember { mutableStateOf<List<LocalChallengeEntity>>(emptyList()) }
    var routes by remember { mutableStateOf<List<SavedRouteEntity>>(emptyList()) }
    var routeImportMessage by remember { mutableStateOf<String?>(null) }
    var mapPacks by remember { mutableStateOf<List<OfflineMapPack>>(emptyList()) }
    var mapDownloadStatus by remember { mutableStateOf("No map pack downloaded.") }
    var mapDownloadRunning by remember { mutableStateOf(false) }
    var showMapConsent by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showChallengeDialog by remember { mutableStateOf(false) }
    var exportRunId by remember { mutableLongStateOf(0L) }
    var selectedRun by remember { mutableStateOf<RunEntity?>(null) }
    var selectedRunPoints by remember { mutableStateOf<List<TrackPointEntity>>(emptyList()) }
    var selectedRunLaps by remember { mutableStateOf<List<LapEntity>>(emptyList()) }
    var selectedRunHeartRate by remember { mutableStateOf<List<HeartRateSampleEntity>>(emptyList()) }
    var selectedRoute by remember { mutableStateOf<SavedRouteEntity?>(null) }
    var selectedRoutePoints by remember { mutableStateOf<List<RoutePointEntity>>(emptyList()) }
    var segmentAttempts by remember { mutableStateOf<List<LocalSegmentAttempt>>(emptyList()) }
    var selectedSensorAddress by remember { mutableStateOf(context.getSharedPreferences(RunTrackingService.PREFERENCES, android.content.Context.MODE_PRIVATE).getString(RunTrackingService.PREF_HEART_RATE_ADDRESS, null)) }
    var selectedSensorName by remember { mutableStateOf(context.getSharedPreferences(RunTrackingService.PREFERENCES, android.content.Context.MODE_PRIVATE).getString(RunTrackingService.PREF_HEART_RATE_NAME, null)) }
    var sensorScanResults by remember { mutableStateOf<List<HeartRateSensor>>(emptyList()) }
    var sensorScanStatus by remember { mutableStateOf("No sensor selected.") }
    var sensorScanning by remember { mutableStateOf(false) }
    var healthStatus by remember { mutableStateOf("Health Connect data is imported only after you choose a run.") }
    var pendingHealthRunId by remember { mutableLongStateOf(0L) }
    val sensorScanner = remember(context) { BluetoothHeartRateScanner(context.applicationContext) }
    val exportRun = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null && exportRunId != 0L) scope.launch {
            val routePoints = withContext(Dispatchers.IO) { database.runDao().pointsForRun(exportRunId) }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    writer.appendLine("<gpx version=\"1.1\" creator=\"Stride Local\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
                    writer.appendLine("<trk><name>Stride Local run</name><trkseg>")
                    routePoints.forEach { point ->
                        writer.append("<trkpt lat=\"").append(point.latitude.toString()).append("\" lon=\"")
                            .append(point.longitude.toString()).append("\">")
                        point.altitudeMeters?.let { writer.append("<ele>").append(it.toString()).append("</ele>") }
                        writer.append("<time>").append(java.time.Instant.ofEpochMilli(point.timestampEpochMs).toString())
                            .appendLine("</time></trkpt>")
                    }
                    writer.appendLine("</trkseg></trk></gpx>")
                }
            }
            exportRunId = 0L
        }
    }
    val importRoute = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val route = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(GpxRouteIO::read)
                        ?: error("The selected route file could not be opened.")
                }
                withContext(Dispatchers.IO) {
                    val id = database.runDao().insertRoute(SavedRouteEntity(name = route.name,
                        createdAtEpochMs = System.currentTimeMillis(), distanceMeters = route.distanceMeters))
                    database.runDao().insertRoutePoints(route.points.map { it.copy(routeId = id) })
                }
                routeImportMessage = "Saved ${route.name} on this phone."
            } catch (error: Exception) {
                routeImportMessage = error.message ?: "Could not read that GPX file."
            }
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionReady = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startRun(context)
            tracking = true
            paused = false
            durationSeconds = 0L
            distanceMeters = 0.0
        }
    }
    val mapLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestCurrentGpsLocation(context) { location ->
            if (location == null) { mapDownloadStatus = "GPS could not find your location. Move outdoors and try again."; mapDownloadRunning = false }
            else OfflineMapPacks.downloadAround(context, location.latitude, location.longitude,
                onStatus = { mapDownloadStatus = it },
                onComplete = { _, message -> mapDownloadRunning = false; mapDownloadStatus = message; OfflineMapPacks.list(context) { mapPacks = it } })
        } else { mapDownloadStatus = "Location permission is needed to center the map download."; mapDownloadRunning = false }
    }
    val sensorPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val allowed = if (Build.VERSION.SDK_INT >= 31) {
            result[Manifest.permission.BLUETOOTH_SCAN] == true && result[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (allowed) {
            sensorScanning = true
            sensorScanStatus = "Scanning for Bluetooth heart-rate sensors…"
            sensorScanner.start(
                onDevice = { device -> sensorScanResults = (sensorScanResults + device).distinctBy { it.address } },
                onStopped = {
                    sensorScanning = false
                    sensorScanStatus = if (sensorScanResults.isEmpty()) "No heart-rate sensor found." else "Choose a sensor to use on your next run."
                },
            )
        } else sensorScanStatus = "Bluetooth permission was not granted."
    }
    val healthPermissionRequest = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        val target = runs.firstOrNull { it.id == pendingHealthRunId }
        if (target != null && HealthConnectHeartRate.readPermission in granted) scope.launch {
            try {
                selectedRunHeartRate = importHealthForRun(context, database, target)
                healthStatus = if (selectedRunHeartRate.isEmpty()) "No heart-rate samples found for this run." else "Imported ${selectedRunHeartRate.size} heart-rate samples locally."
            } catch (error: Exception) { healthStatus = error.message ?: "Could not read Health Connect data." }
        } else healthStatus = "Health Connect permission was not granted."
    }

    LaunchedEffect(database) {
        launch { database.runDao().observeAll().collectLatest { runs = it } }
        launch { database.runDao().observeGoals().collectLatest { goals = it } }
        launch { database.runDao().observeChallenges(System.currentTimeMillis()).collectLatest { challenges = it } }
        launch { database.runDao().observeRoutes().collectLatest { routes = it } }
        OfflineMapPacks.list(context) { mapPacks = it }
    }
    LaunchedEffect(runs) {
        if (runs.isEmpty()) records = PersonalRecords()
        else {
            val perRun = withContext(Dispatchers.IO) {
                runs.map { run -> run.distanceMeters to RunAnalysis.kilometerSplits(database.runDao().pointsForRun(run.id)) }
            }
            records = personalRecords(perRun)
        }
    }
    LaunchedEffect(tracking, paused) {
        while (tracking && !paused) {
            kotlinx.coroutines.delay(1_000)
            durationSeconds += 1
        }
    }
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) {
                durationSeconds = intent?.getLongExtra(RunTrackingService.EXTRA_DURATION, durationSeconds) ?: durationSeconds
                distanceMeters = intent?.getDoubleExtra(RunTrackingService.EXTRA_DISTANCE, distanceMeters) ?: distanceMeters
                paused = intent?.getBooleanExtra(RunTrackingService.EXTRA_PAUSED, paused) ?: paused
                autoPaused = intent?.getBooleanExtra(RunTrackingService.EXTRA_AUTO_PAUSED, autoPaused) ?: autoPaused
                heartRateBpm = intent?.getIntExtra(RunTrackingService.EXTRA_HEART_RATE, heartRateBpm) ?: heartRateBpm
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, android.content.IntentFilter(RunTrackingService.ACTION_STATUS), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Stride Local", style = MaterialTheme.typography.headlineMedium)
                Text("Your runs stay on this phone. No account or social feed.", style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Auto-pause")
                            Switch(checked = autoPauseSetting, enabled = !tracking, onCheckedChange = { enabled ->
                                autoPauseSetting = enabled
                                context.getSharedPreferences(RunTrackingService.PREFERENCES, android.content.Context.MODE_PRIVATE).edit()
                                    .putBoolean(RunTrackingService.PREF_AUTO_PAUSE, enabled).apply()
                            })
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Distance and lap beeps")
                            Switch(checked = audioCueSetting, enabled = !tracking, onCheckedChange = { enabled ->
                                audioCueSetting = enabled
                                context.getSharedPreferences(RunTrackingService.PREFERENCES, android.content.Context.MODE_PRIVATE).edit()
                                    .putBoolean(RunTrackingService.PREF_AUDIO_CUES, enabled).apply()
                            })
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Heart rate", style = MaterialTheme.typography.titleMedium)
                        Text(if (tracking && heartRateBpm > 0) "$heartRateBpm bpm" else selectedSensorName?.let { "Selected: $it" } ?: "Optional Bluetooth chest strap")
                        Button(onClick = {
                            sensorScanResults = emptyList()
                            if (Build.VERSION.SDK_INT >= 31) sensorPermissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                            else sensorPermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                        }, enabled = !tracking && !sensorScanning) { Text(if (sensorScanning) "Scanning…" else "Find sensor") }
                        Text(sensorScanStatus, style = MaterialTheme.typography.bodySmall)
                        sensorScanResults.forEach { sensor ->
                            TextButton(onClick = {
                                selectedSensorAddress = sensor.address
                                selectedSensorName = sensor.name
                                context.getSharedPreferences(RunTrackingService.PREFERENCES, android.content.Context.MODE_PRIVATE).edit()
                                    .putString(RunTrackingService.PREF_HEART_RATE_ADDRESS, sensor.address)
                                    .putString(RunTrackingService.PREF_HEART_RATE_NAME, sensor.name).apply()
                                sensorScanStatus = "${sensor.name} selected. It will connect when your next run starts."
                            }) { Text("Use ${sensor.name}") }
                        }
                        if (selectedSensorAddress != null && !tracking) TextButton(onClick = {
                            selectedSensorAddress = null
                            selectedSensorName = null
                            context.getSharedPreferences(RunTrackingService.PREFERENCES, android.content.Context.MODE_PRIVATE).edit()
                                .remove(RunTrackingService.PREF_HEART_RATE_ADDRESS).remove(RunTrackingService.PREF_HEART_RATE_NAME).apply()
                            sensorScanStatus = "Sensor selection cleared."
                        }) { Text("Clear selected sensor") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Run", style = MaterialTheme.typography.titleLarge)
                        Text(formatDistance(distanceMeters), style = MaterialTheme.typography.displaySmall)
                        Text("${formatDuration(durationSeconds)}   ·   ${formatPace(distanceMeters, durationSeconds)} /km")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!tracking) Button(onClick = {
                                if (permissionReady && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    startRun(context)
                                    tracking = true; paused = false; durationSeconds = 0; distanceMeters = 0.0
                                }
                                else permissions.launch(buildList {
                                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                                }.toTypedArray())
                            }, enabled = !mapDownloadRunning) { Text(if (mapDownloadRunning) "Map download in progress" else "Start run") }
                            else {
                                OutlinedButton(onClick = {
                                    paused = !paused
                                    context.startService(Intent(context, RunTrackingService::class.java).setAction(if (paused) RunTrackingService.ACTION_PAUSE else RunTrackingService.ACTION_RESUME))
                                }, enabled = !autoPaused) { Text(if (autoPaused) "Auto-paused" else if (paused) "Resume" else "Pause") }
                                OutlinedButton(onClick = { context.startService(Intent(context, RunTrackingService::class.java).setAction(RunTrackingService.ACTION_LAP)) }) { Text("Lap") }
                                Button(onClick = {
                                    context.startService(Intent(context, RunTrackingService::class.java).setAction(RunTrackingService.ACTION_FINISH))
                                    tracking = false; paused = false
                                }) { Text("Finish") }
                            }
                        }
                        Text("GPS runs while the screen is off using a visible phone notification.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text("Run history", style = MaterialTheme.typography.titleLarge) }
            if (runs.isEmpty()) item { Text("Completed runs will appear here.") }
            items(runs, key = { it.id }) { run ->
                Card(Modifier.fillMaxWidth().then(if (mapDownloadRunning) Modifier else Modifier.clickable {
                    selectedRun = run
                    scope.launch {
                        selectedRunPoints = withContext(Dispatchers.IO) { database.runDao().pointsForRun(run.id) }
                        selectedRunLaps = withContext(Dispatchers.IO) { database.runDao().lapsForRun(run.id) }
                        selectedRunHeartRate = withContext(Dispatchers.IO) { database.runDao().heartRateForRun(run.id) }
                    }
                })) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(formatDistance(run.distanceMeters), style = MaterialTheme.typography.titleMedium)
                        Text("${formatDuration(run.durationSeconds)} · ${formatPace(run.distanceMeters, run.durationSeconds)} /km · +${run.elevationGainMeters.toInt()} m")
                        Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(run.startedAtEpochMs)), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            exportRunId = run.id
                            exportRun.launch("stride-run-${run.startedAtEpochMs}.gpx")
                        }) { Text("Save GPX file") }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Training", style = MaterialTheme.typography.titleLarge)
                    Row {
                        TextButton(onClick = { showGoalDialog = true }) { Text("Add goal") }
                        TextButton(onClick = { showChallengeDialog = true }) { Text("Challenge") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Personal records", style = MaterialTheme.typography.titleMedium)
                        Text("Longest run: ${formatDistance(records.longestRunMeters)}")
                        records.fastestKilometerSeconds?.let { Text("Fastest kilometer: ${formatDuration(it.toLong())}") }
                        records.fastestFiveKilometersSeconds?.let { Text("Fastest 5 km: ${formatDuration(it.toLong())}") }
                    }
                }
            }
            items(goals, key = { "goal-${it.id}" }) { goal ->
                val start = TrainingProgress.startOfPeriod(goal.period)
                val progress = TrainingProgress.distanceSince(runs, start)
                val fraction = (progress / goal.targetValue).toFloat().coerceIn(0f, 1f)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(goal.title, style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text("${formatDistance(progress)} of ${formatDistance(goal.targetValue)} this ${goal.period}")
                    }
                }
            }
            items(challenges, key = { "challenge-${it.id}" }) { challenge ->
                val progress = TrainingProgress.challengeDistance(runs, challenge.startsAtEpochMs, challenge.endsAtEpochMs)
                val fraction = (progress / challenge.targetMeters).toFloat().coerceIn(0f, 1f)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(challenge.title, style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text("${formatDistance(progress)} of ${formatDistance(challenge.targetMeters)} · ends ${java.text.DateFormat.getDateInstance().format(java.util.Date(challenge.endsAtEpochMs))}")
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Saved routes", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { importRoute.launch(arrayOf("application/gpx+xml", "application/gpx", "application/xml", "text/xml", "*/*")) }) { Text("Import GPX") }
                }
                routeImportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (routes.isEmpty()) item { Text("Import a GPX route to keep it on this phone.", style = MaterialTheme.typography.bodySmall) }
            items(routes, key = { "route-${it.id}" }) { route ->
                Card(Modifier.fillMaxWidth().then(if (mapDownloadRunning) Modifier else Modifier.clickable {
                    selectedRoute = route
                    scope.launch {
                        val (routePoints, attempts) = withContext(Dispatchers.IO) {
                            val dao = database.runDao()
                            val routePoints = dao.pointsForRoute(route.id)
                            val attempts = runs.mapNotNull { run -> SegmentComparison.match(routePoints, run, dao.pointsForRun(run.id)) }
                                .sortedBy { it.durationSeconds }
                            routePoints to attempts
                        }
                        selectedRoutePoints = routePoints
                        segmentAttempts = attempts
                    }
                })) {
                    Column(Modifier.padding(16.dp)) {
                        Text(route.name, style = MaterialTheme.typography.titleMedium)
                        Text(formatDistance(route.distanceMeters))
                    }
                }
            }
            item {
                Text("Offline maps", style = MaterialTheme.typography.titleLarge)
                Text("Run tracking never loads map tiles. Download a map pack only when you choose to.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { showMapConsent = true }, enabled = !tracking && !mapDownloadRunning) {
                    Text(if (mapDownloadRunning) "Map download in progress" else "Download offline map around me")
                }
                Text(mapDownloadStatus, style = MaterialTheme.typography.bodySmall)
            }
            items(mapPacks, key = { "map-${it.id}" }) { pack ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(pack.name, style = MaterialTheme.typography.titleSmall)
                            Text("${if (pack.complete) "Ready offline" else "Incomplete"} · ${"%.1f".format(pack.downloadedBytes / 1_000_000.0)} MB")
                        }
                        TextButton(onClick = { OfflineMapPacks.delete(context, pack.id) { OfflineMapPacks.list(context) { mapPacks = it } } }) { Text("Delete") }
                    }
                }
            }
            item {
                Text("Still planned: turn-by-turn route guidance, elevation charts, training calendar, more goal types, and interrupted-run recovery.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showGoalDialog) {
        DistanceTargetDialog(title = "New distance goal", buttonText = "Save goal", onDismiss = { showGoalDialog = false }) { title, km ->
            scope.launch { database.runDao().insertGoal(TrainingGoalEntity(title = title, metric = "distance_m", targetValue = km * 1000.0,
                period = "week", createdAtEpochMs = System.currentTimeMillis())) }
            showGoalDialog = false
        }
    }
    if (showChallengeDialog) {
        DistanceTargetDialog(title = "New 30-day challenge", buttonText = "Start challenge", onDismiss = { showChallengeDialog = false }) { title, km ->
            val now = System.currentTimeMillis()
            scope.launch { database.runDao().insertChallenge(LocalChallengeEntity(title = title, targetMeters = km * 1000.0,
                startsAtEpochMs = now, endsAtEpochMs = now + 30L * 24 * 60 * 60 * 1000)) }
            showChallengeDialog = false
        }
    }
    if (showMapConsent) {
        AlertDialog(
            onDismissRequest = { showMapConsent = false },
            title = { Text("Download an offline map?") },
            text = { Text("Stride Local will contact OpenFreeMap directly to download an offline map centered on your current GPS location. Its rectangular bounds extend about 100 km north, south, east, and west; the corners extend farther. The provider will receive the requested area and your network address. Your runs are not sent. The map is stored on this phone; run tracking makes no map requests. The download is limited to the provider's 3,000-tile or 50 MB cap, so map detail may be reduced.") },
            confirmButton = {
                TextButton(onClick = {
                    showMapConsent = false
                    mapDownloadRunning = true
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        requestCurrentGpsLocation(context) { location ->
                            if (location == null) { mapDownloadStatus = "GPS could not find your location. Move outdoors and try again."; mapDownloadRunning = false }
                            else OfflineMapPacks.downloadAround(context, location.latitude, location.longitude,
                                onStatus = { mapDownloadStatus = it },
                                onComplete = { _, message -> mapDownloadRunning = false; mapDownloadStatus = message; OfflineMapPacks.list(context) { mapPacks = it } })
                        }
                    } else mapLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showMapConsent = false }) { Text("Cancel") } },
        )
    }
    selectedRun?.let { run ->
        AlertDialog(
            onDismissRequest = { selectedRun = null },
            title = { Text("Run details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${formatDistance(run.distanceMeters)} · ${formatDuration(run.durationSeconds)}")
                    Text("Average ${formatPace(run.distanceMeters, run.durationSeconds)} /km · +${run.elevationGainMeters.toInt()} m")
                    if (selectedRunHeartRate.isNotEmpty()) {
                        val heartRateValues = selectedRunHeartRate.map { it.beatsPerMinute }
                        Text("Heart rate ${heartRateValues.average().toInt()} avg · ${heartRateValues.maxOrNull()} max bpm")
                    }
                    TextButton(onClick = {
                        if (!HealthConnectHeartRate.isAvailable(context)) {
                            healthStatus = "Health Connect is unavailable. Enable it in Android settings or install it on this phone."
                        } else scope.launch {
                            try {
                                val client = androidx.health.connect.client.HealthConnectClient.getOrCreate(context)
                                if (HealthConnectHeartRate.readPermission in client.permissionController.getGrantedPermissions()) {
                                    selectedRunHeartRate = importHealthForRun(context, database, run)
                                    healthStatus = if (selectedRunHeartRate.isEmpty()) "No heart-rate samples found for this run." else "Imported ${selectedRunHeartRate.size} heart-rate samples locally."
                                } else {
                                    pendingHealthRunId = run.id
                                    healthPermissionRequest.launch(setOf(HealthConnectHeartRate.readPermission))
                                }
                            } catch (error: Exception) { healthStatus = error.message ?: "Could not open Health Connect." }
                        }
                    }) { Text("Import from Health Connect") }
                    Text(healthStatus, style = MaterialTheme.typography.bodySmall)
                    RouteTrace(selectedRunPoints, Modifier.fillMaxWidth().height(110.dp))
                    val splits = RunAnalysis.kilometerSplits(selectedRunPoints)
                    if (splits.isNotEmpty()) {
                        Text("Pace by split", style = MaterialTheme.typography.titleSmall)
                        PaceChart(splits, Modifier.fillMaxWidth().height(90.dp))
                    }
                    if (selectedRunPoints.size > 1) OfflineRouteMap(
                        selectedRunPoints.map { MapPoint(it.latitude, it.longitude, it.sequence.toString()) },
                        Modifier.fillMaxWidth().height(190.dp),
                    )
                    Text("Kilometer splits", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.heightIn(max = 130.dp)) {
                        items(splits) { split ->
                            Text("${split.index}: ${formatDistance(split.distanceMeters)} · ${formatPace(split.distanceMeters, split.durationSeconds.toLong())} /km")
                        }
                    }
                    if (selectedRunLaps.isNotEmpty()) {
                        Text("Manual laps", style = MaterialTheme.typography.titleSmall)
                        selectedRunLaps.forEachIndexed { index, lap ->
                            Text("${index + 1}: ${formatDistance(lap.distanceMeters)} · ${formatDuration(lap.elapsedSeconds)}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedRun = null }) { Text("Close") } },
        )
    }
    selectedRoute?.let { route ->
        AlertDialog(
            onDismissRequest = { selectedRoute = null },
            title = { Text(route.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${formatDistance(route.distanceMeters)} · saved on this phone")
                    if (selectedRoutePoints.size > 1) OfflineRouteMap(
                        selectedRoutePoints.map { MapPoint(it.latitude, it.longitude, it.sequence.toString()) },
                        Modifier.fillMaxWidth().height(260.dp),
                    )
                    Text("Your past efforts", style = MaterialTheme.typography.titleSmall)
                    if (segmentAttempts.isEmpty()) Text("No matching completed runs yet.", style = MaterialTheme.typography.bodySmall)
                    else segmentAttempts.forEachIndexed { index, attempt ->
                        Text("${index + 1}. ${formatDuration(attempt.durationSeconds)} · ${java.text.DateFormat.getDateInstance().format(java.util.Date(attempt.run.startedAtEpochMs))}")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedRoute = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun PaceChart(splits: List<app.stridelocal.analysis.PaceSplit>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (splits.isEmpty()) return@Canvas
        val values = splits.map { it.paceSecondsPerKm.coerceAtLeast(1.0) }
        val low = values.minOrNull() ?: return@Canvas
        val high = values.maxOrNull() ?: return@Canvas
        val span = (high - low).takeIf { it > 0 } ?: 1.0
        val path = Path()
        values.forEachIndexed { index, pace ->
            val x = if (values.size == 1) size.width / 2f else index.toFloat() * size.width / (values.size - 1)
            val y = ((pace - low) / span).toFloat() * (size.height - 12.dp.toPx()) + 6.dp.toPx()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Color(0xFF177D68), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun RouteTrace(points: List<TrackPointEntity>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }; val maxLon = points.maxOf { it.longitude }
        val latSpan = (maxLat - minLat).toFloat().takeIf { it > 0f } ?: 1e-6f
        val lonSpan = (maxLon - minLon).toFloat().takeIf { it > 0f } ?: 1e-6f
        val scale = minOf((size.width - 24.dp.toPx()) / lonSpan, (size.height - 24.dp.toPx()) / latSpan)
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = 12.dp.toPx() + (point.longitude - minLon).toFloat() * scale
            val y = size.height - 12.dp.toPx() - (point.latitude - minLat).toFloat() * scale
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Color(0xFF177D68), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()))
    }
}

@Composable
private fun DistanceTargetDialog(title: String, buttonText: String, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kilometers by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = kilometers, onValueChange = { value -> kilometers = value.filter { it.isDigit() || it == '.' } },
                    label = { Text("Target distance (km)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { kilometers.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSave(name.ifBlank { title }, it) } }) { Text(buttonText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun startRun(context: android.content.Context) {
    OfflineMapPacks.pauseAll(context)
    ContextCompat.startForegroundService(context, Intent(context, RunTrackingService::class.java).setAction(RunTrackingService.ACTION_START))
}

private suspend fun importHealthForRun(
    context: android.content.Context,
    database: app.stridelocal.data.local.StrideDatabase,
    run: RunEntity,
): List<HeartRateSampleEntity> = withContext(Dispatchers.IO) {
    val dao = database.runDao()
    HealthConnectHeartRate.importForRun(context, dao, run)
    dao.heartRateForRun(run.id)
}

private fun requestCurrentGpsLocation(context: android.content.Context, callback: (Location?) -> Unit) {
    val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
    try {
        val last = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (last != null && System.currentTimeMillis() - last.time < 15 * 60 * 1000) {
            callback(last)
            return
        }
        var completed = false
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (completed) return
                completed = true
                manager.removeUpdates(this)
                callback(location)
            }
            override fun onProviderDisabled(provider: String) {
                if (completed) return
                completed = true
                manager.removeUpdates(this)
                callback(null)
            }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            if (!completed) { completed = true; manager.removeUpdates(listener); callback(null) }
        }, 30_000L)
    } catch (_: SecurityException) { callback(null) }
    catch (_: IllegalArgumentException) { callback(null) }
}

private fun formatDistance(meters: Double) = String.format(Locale.getDefault(), "%.2f km", meters / 1000.0)
private fun formatDuration(seconds: Long) = String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
private fun formatPace(meters: Double, seconds: Long): String {
    if (meters < 10 || seconds <= 0) return "--:--"
    val pace = (seconds * 1000.0 / meters).toLong()
    return String.format(Locale.getDefault(), "%d:%02d", pace / 60, pace % 60)
}
