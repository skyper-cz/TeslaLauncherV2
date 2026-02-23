@file:Suppress("OPT_IN_USAGE", "DEPRECATION", "SpellCheckingInspection", "UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER")

package com.launchers.teslalauncherv2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

// Custom UI components from other files
import com.launchers.teslalauncherv2.ui.*
import com.launchers.teslalauncherv2.data.CarState
import com.launchers.teslalauncherv2.data.NavInstruction
import com.launchers.teslalauncherv2.data.AppManager
import com.launchers.teslalauncherv2.map.OfflineMapManager
import com.launchers.teslalauncherv2.map.NetworkManager
import com.launchers.teslalauncherv2.map.MapLinkParser
import com.launchers.teslalauncherv2.obd.ObdDataManager
import com.launchers.teslalauncherv2.hardware.ReverseCameraScreen
import com.mapbox.geojson.Point

// Imported for AI features
import com.launchers.teslalauncherv2.ai.AiManager
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    var externalDestinationPoint: Point? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        checkIntentForExternalLocation(intent)

        setContent {
            TeslaLauncherTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TeslaLayout(this)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkIntentForExternalLocation(intent)
    }

    private fun checkIntentForExternalLocation(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
            coroutineScope.launch {
                val parsedPoint = MapLinkParser.parseIntentForDestination(intent.data)
                if (parsedPoint != null) {
                    externalDestinationPoint = parsedPoint
                    Toast.makeText(this@MainActivity, "Loading destination from shared link...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to read shared address", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ObdDataManager.stop()
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(0)
    }
}

@Composable
fun TeslaLauncherTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(background = Color.Black, surface = Color.Black, onBackground = Color.White, onSurface = Color.White)
    MaterialTheme(colorScheme = darkColorScheme, content = content)
}

@Composable
fun TeslaLayout(activity: MainActivity? = null) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()

    val googleApiKey = remember {
        try {
            val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            ai.metaData.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) { "" }
    }

    val carStateFlow = ObdDataManager.carState
    val carStateSnapshot by carStateFlow.collectAsState()

    var navInstructionsList by remember { mutableStateOf<List<NavInstruction>>(emptyList()) }
    var currentInstructionIndex by remember { mutableIntStateOf(0) }
    var currentManeuverDistance by remember { mutableStateOf<Int?>(null) }
    var lastMinDistance by remember { mutableStateOf(Double.MAX_VALUE) }
    var speedLimitsList by remember { mutableStateOf<List<Int?>>(emptyList()) }
    var currentSpeedLimit by remember { mutableStateOf<Int?>(null) }
    var routeCoordinates by remember { mutableStateOf<List<Location>>(emptyList()) }
    var currentDestination by remember { mutableStateOf<Point?>(null) }
    var isRerouting by remember { mutableStateOf(false) }

    val aiVisionState by AiManager.visionState.collectAsState()

    LaunchedEffect(activity?.externalDestinationPoint) {
        val extPoint = activity?.externalDestinationPoint
        if (extPoint != null) {
            currentDestination = extPoint
            routeCoordinates = emptyList()
            activity.externalDestinationPoint = null
        }
    }

    val currentInstruction = navInstructionsList.getOrNull(currentInstructionIndex)

    var currentRouteDuration by remember { mutableStateOf<Int?>(null) }
    var routeGeoJson by rememberSaveable { mutableStateOf<String?>(null) }
    var currentSpeedGps by remember { mutableIntStateOf(0) }
    var batteryLevel by remember { mutableIntStateOf(getBatteryLevel(context)) }
    var currentGpsLocation by remember { mutableStateOf<Location?>(null) }

    val sharedPrefs = remember { context.getSharedPreferences("TeslaSettings", Context.MODE_PRIVATE) }
    var savedObdMacAddress by remember { mutableStateOf(sharedPrefs.getString("obd_mac", "00:10:CC:4F:36:03") ?: "") }
    var currentMapEngine by remember { mutableStateOf(sharedPrefs.getString("map_engine", "MAPBOX") ?: "MAPBOX") }
    var currentSearchEngine by remember { mutableStateOf(sharedPrefs.getString("search_engine", "MAPBOX") ?: "MAPBOX") }
    var showSpeedLimitSetting by remember { mutableStateOf(sharedPrefs.getBoolean("show_speed_limit", true)) }
    var show3dBuildings by remember { mutableStateOf(sharedPrefs.getBoolean("show_3d_buildings", true)) }
    var autoShiftGear by remember { mutableStateOf(sharedPrefs.getBoolean("auto_shift_gear", true)) }
    var enableAiFeatures by remember { mutableStateOf(sharedPrefs.getBoolean("enable_ai", false)) }

    var isNightPanel by rememberSaveable { mutableStateOf(false) }
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isAppDrawerOpen by rememberSaveable { mutableStateOf(false) }
    var isErrorDrawerOpen by remember { mutableStateOf(false) }
    val activeErrors = remember { mutableStateListOf<String>() }
    var lastErrorTriggered by remember { mutableStateOf<String?>(null) }

    var currentGear by rememberSaveable { mutableStateOf("P") }
    var lastManualShiftTime by remember { mutableLongStateOf(0L) }
    var is3dMapMode by remember { mutableStateOf(false) }
    var gpsStatus by remember { mutableStateOf<String?>("INIT") }
    var isLocationPermissionGranted by remember { mutableStateOf(false) }
    var isManualOverrideActive by remember { mutableStateOf(false) }

    val effectiveSpeed = if (carStateSnapshot.isConnected) carStateSnapshot.speed else currentSpeedGps

    var activeAiSpeedLimit by remember { mutableStateOf<Int?>(null) }
    var lastHeading by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(aiVisionState.lastUpdate) {
        if (aiVisionState.detectedSpeedLimit != null) {
            activeAiSpeedLimit = aiVisionState.detectedSpeedLimit
        }
    }

    val effectiveSpeedLimit = activeAiSpeedLimit ?: currentSpeedLimit

    LaunchedEffect(currentGpsLocation) {
        val loc = currentGpsLocation
        if (loc != null && loc.hasBearing()) {
            val currentHeading = loc.bearing
            if (lastHeading != null) {
                var diff = Math.abs(currentHeading - lastHeading!!)
                if (diff > 180f) diff = 360f - diff

                if (diff > 40f && loc.speed > 1.5f) {
                    activeAiSpeedLimit = null
                }
            }
            lastHeading = currentHeading
        }
    }

    LaunchedEffect(effectiveSpeedLimit, activeAiSpeedLimit, currentSpeedLimit, aiVisionState.lastUpdate) {
        android.util.Log.d("SPEED_LIMIT_DEBUG",
            "Limit na displeji: $effectiveSpeedLimit " +
                    "| AI hlásí: $activeAiSpeedLimit " +
                    "| Mapa hlásí: $currentSpeedLimit " +
                    "| AI Update: ${aiVisionState.lastUpdate}"
        )
    }

    val handleGeoJsonUpdate: (String?) -> Unit = { geo ->
        routeGeoJson = geo
        if (geo != null) {
            try {
                val coords = mutableListOf<Location>()
                val featureCollection = com.mapbox.geojson.FeatureCollection.fromJson(geo)
                featureCollection.features()?.forEach { feature ->
                    val lineString = feature.geometry() as? com.mapbox.geojson.LineString
                    lineString?.coordinates()?.forEach { pt ->
                        if (coords.isEmpty() || coords.last().latitude != pt.latitude() || coords.last().longitude != pt.longitude()) {
                            coords.add(Location("").apply {
                                latitude = pt.latitude()
                                longitude = pt.longitude()
                            })
                        }
                    }
                }
                routeCoordinates = coords
            } catch (e: Exception) {
                routeCoordinates = emptyList()
            }
        } else {
            routeCoordinates = emptyList()
        }
    }

    val cancelRouteAction = {
        navInstructionsList = emptyList()
        currentRouteDuration = null
        routeGeoJson = null
        routeCoordinates = emptyList()
        speedLimitsList = emptyList()
        currentSpeedLimit = null
        currentDestination = null
    }

    LaunchedEffect(isSettingsOpen) {
        showSpeedLimitSetting = sharedPrefs.getBoolean("show_speed_limit", true)
        show3dBuildings = sharedPrefs.getBoolean("show_3d_buildings", true)
        autoShiftGear = sharedPrefs.getBoolean("auto_shift_gear", true)
        enableAiFeatures = sharedPrefs.getBoolean("enable_ai", false)
    }

    LaunchedEffect(lastManualShiftTime) {
        if (lastManualShiftTime > 0) {
            isManualOverrideActive = true
            delay(8000)
            isManualOverrideActive = false
        }
    }

    LaunchedEffect(navInstructionsList, effectiveSpeed) {
        while (navInstructionsList.isNotEmpty() && currentRouteDuration != null) {
            delay(1000)
            if (effectiveSpeed > 3) {
                currentRouteDuration = (currentRouteDuration!! - 1).coerceAtLeast(0)
            }
        }
    }

    LaunchedEffect(effectiveSpeed, currentGear, isManualOverrideActive, autoShiftGear) {
        if (currentGear == "D") is3dMapMode = true else if (currentGear == "P") is3dMapMode = false

        if (!isManualOverrideActive && autoShiftGear) {
            if (currentGear != "R") {
                if (effectiveSpeed > 5 && currentGear != "D") currentGear = "D"
                if (effectiveSpeed < 4 && currentGear != "P") currentGear = "P"
            }
        }
    }

    LaunchedEffect(carStateSnapshot.error) {
        if (!carStateSnapshot.error.isNullOrEmpty() && carStateSnapshot.error != lastErrorTriggered) {
            lastErrorTriggered = carStateSnapshot.error
            if (!activeErrors.contains(carStateSnapshot.error)) {
                activeErrors.add(carStateSnapshot.error!!)
            }
            try {
                val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
                toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                toneGen.release()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(enableAiFeatures) {
        if (enableAiFeatures) {
            AiManager.initialize(context)
        } else {
            AiManager.close()
        }
    }

    fun manualShiftTo(gear: String) {
        lastManualShiftTime = System.currentTimeMillis()
        if (gear == "R") {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                currentGear = "R"
            } else { currentGear = gear }
        } else currentGear = gear
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions(), onResult = { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) { isLocationPermissionGranted = true; gpsStatus = "READY" } else { gpsStatus = "NO PERM" }
    })

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions(), onResult = {
        if (savedObdMacAddress.isNotBlank()) ObdDataManager.connect(context, savedObdMacAddress)
    })

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) isLocationPermissionGranted = true
        else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

        if (Build.VERSION.SDK_INT >= 31) bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        else if (savedObdMacAddress.isNotBlank()) ObdDataManager.connect(context, savedObdMacAddress)

        launch(Dispatchers.IO) { AppManager.prefetchApps(context) }
        while (true) { batteryLevel = getBatteryLevel(context); delay(60000) }
    }

    DisposableEffect(isLocationPermissionGranted, currentInstruction, routeCoordinates, currentDestination, isRerouting) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                gpsStatus = null
                currentGpsLocation = location
                if (location.hasSpeed()) currentSpeedGps = (location.speed * 3.6f).toInt()

                scope.launch(Dispatchers.Default) {

                    if (routeCoordinates.isNotEmpty() && speedLimitsList.isNotEmpty()) {
                        var minDistance = Float.MAX_VALUE
                        var closestSegmentIndex = 0

                        for (i in 0 until routeCoordinates.size - 1) {
                            val a = routeCoordinates[i]
                            val b = routeCoordinates[i + 1]
                            val dist = pointToLineDistance(location, a, b)
                            if (dist < minDistance) { minDistance = dist; closestSegmentIndex = i }
                        }

                        val limitIndex = closestSegmentIndex.coerceAtMost(speedLimitsList.size - 1)
                        currentSpeedLimit = speedLimitsList.getOrNull(limitIndex)

                        if (minDistance > 50f && location.accuracy < 30f && currentDestination != null && !isRerouting) {
                            isRerouting = true

                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Rerouting...", Toast.LENGTH_SHORT).show()
                            }

                            val currentPoint = Point.fromLngLat(location.longitude, location.latitude)

                            if (currentMapEngine == "GOOGLE") {
                                NetworkManager.fetchGoogleRoute(currentPoint, currentDestination!!, googleApiKey) { geo, instr, dur, limits ->
                                    scope.launch(Dispatchers.Main) {
                                        if (geo != null) { handleGeoJsonUpdate(geo); navInstructionsList = instr; currentRouteDuration = dur; speedLimitsList = limits; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE }
                                        isRerouting = false
                                    }
                                }
                            } else {
                                NetworkManager.fetchRouteManual(currentPoint, currentDestination!!, context) { geo, instr, dur, limits ->
                                    scope.launch(Dispatchers.Main) {
                                        if (geo != null) { handleGeoJsonUpdate(geo); navInstructionsList = instr; currentRouteDuration = dur; speedLimitsList = limits; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE }
                                        isRerouting = false
                                    }
                                }
                            }
                        }
                    }

                    val instruction = currentInstruction
                    if (instruction?.maneuverPoint != null && !isRerouting) {
                        val target = Location("T").apply {
                            latitude = instruction.maneuverPoint.latitude()
                            longitude = instruction.maneuverPoint.longitude()
                        }
                        val dist = location.distanceTo(target).toInt()

                        withContext(Dispatchers.Main) {
                            if (dist < 30 || (dist > lastMinDistance && lastMinDistance < 150)) {
                                if (currentInstructionIndex < navInstructionsList.size - 1) {
                                    currentInstructionIndex++
                                    lastMinDistance = Double.MAX_VALUE
                                } else {
                                    cancelRouteAction()
                                    Toast.makeText(context, "You have arrived!", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                if (dist < lastMinDistance) lastMinDistance = dist.toDouble()
                                currentManeuverDistance = dist
                            }
                        }
                    }
                }
            }
            override fun onProviderDisabled(p: String) { gpsStatus = "GPS OFF" }
            override fun onProviderEnabled(p: String) { gpsStatus = "SEARCHING..." }
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        }

        if (isLocationPermissionGranted) {
            try {
                gpsStatus = "SEARCHING..."
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener)
            } catch (e: Exception) { gpsStatus = "ERR GPS" }
        }
        onDispose { locationManager.removeUpdates(listener) }
    }


    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {

                Column(modifier = Modifier.fillMaxWidth(0.32f).fillMaxHeight()) {

                    Box(modifier = Modifier.weight(0.60f).fillMaxWidth()) {
                        InstrumentClusterWrapper(
                            modifier = Modifier.fillMaxSize().clickable { isErrorDrawerOpen = !isErrorDrawerOpen },
                            isLandscape = isLandscape,
                            carStateFlow = carStateFlow, gpsStatus = gpsStatus, batteryLevel = batteryLevel,
                            instruction = currentInstruction, currentNavDistance = currentManeuverDistance,
                            gpsSpeed = currentSpeedGps, routeDuration = currentRouteDuration,
                            speedLimit = effectiveSpeedLimit,
                            showSpeedLimit = showSpeedLimitSetting
                        )
                        SmartErrorDrawer(
                            errors = activeErrors, isOpen = isErrorDrawerOpen,
                            onClose = { isErrorDrawerOpen = false },
                            onDismissError = { activeErrors.remove(it) }
                        )
                    }

                    HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
                    Dock(
                        modifier = Modifier.weight(0.40f),
                        isLandscape = true,
                        isNightPanel = isNightPanel, onNightPanelToggle = { isNightPanel = !isNightPanel }, onOpenSettings = { isSettingsOpen = true }, onOpenApps = { isAppDrawerOpen = true }
                    )
                }

                GearSelector(
                    currentGear = currentGear,
                    onGearSelected = { gear -> manualShiftTo(gear) },
                    modifier = Modifier.width(60.dp).fillMaxHeight()
                )

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {

                    val cameraAlpha = if (currentGear == "R") 1f else 0.01f
                    val cameraZIndex = if (currentGear == "R") 30f else 0f

                    Box(modifier = Modifier
                        .fillMaxSize()
                        .alpha(cameraAlpha)
                        .zIndex(cameraZIndex)
                    ) {
                        ReverseCameraScreen(isReverseGear = currentGear == "R")

                        if (currentGear == "R") {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                Text(
                                    text = "REAR VIEW",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(top = 16.dp)
                                        .background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().zIndex(10f)) {
                        when (currentMapEngine) {
                            "MAPBOX" -> Viewport(
                                modifier = Modifier.fillMaxSize(), isNightPanel = isNightPanel, is3dModeExternal = is3dMapMode,
                                currentNavDistance = currentManeuverDistance, show3dBuildings = show3dBuildings,
                                searchEngine = currentSearchEngine, currentLocation = currentGpsLocation, routeGeoJson = routeGeoJson,
                                onRouteGeoJsonUpdated = handleGeoJsonUpdate,
                                onInstructionUpdated = { list -> navInstructionsList = list; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE },
                                onRouteDurationUpdated = { currentRouteDuration = it }, onSpeedLimitsUpdated = { limits -> speedLimitsList = limits },
                                onDestinationSet = { dest -> currentDestination = dest }, onCancelRoute = cancelRouteAction
                            )
                            "GOOGLE" -> GoogleViewport(
                                modifier = Modifier.fillMaxSize(), isNightPanel = isNightPanel, is3dModeExternal = is3dMapMode,
                                currentNavDistance = currentManeuverDistance, searchEngine = currentSearchEngine, currentLocation = currentGpsLocation, routeGeoJson = routeGeoJson,
                                onRouteGeoJsonUpdated = handleGeoJsonUpdate,
                                onInstructionUpdated = { list -> navInstructionsList = list; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE },
                                onRouteDurationUpdated = { currentRouteDuration = it }, onSpeedLimitsUpdated = { limits -> speedLimitsList = limits },
                                onDestinationSet = { dest -> currentDestination = dest }, onCancelRoute = cancelRouteAction
                            )
                        }
                    }
                }
            }
        }
        else {
            Column(modifier = Modifier.fillMaxSize()) {

                Box(modifier = Modifier.wrapContentHeight().fillMaxWidth()) {
                    InstrumentClusterWrapper(
                        modifier = Modifier.fillMaxWidth().clickable { isErrorDrawerOpen = !isErrorDrawerOpen },
                        isLandscape = isLandscape,
                        carStateFlow = carStateFlow, gpsStatus = gpsStatus, batteryLevel = batteryLevel,
                        instruction = currentInstruction, currentNavDistance = currentManeuverDistance,
                        gpsSpeed = currentSpeedGps, routeDuration = currentRouteDuration,
                        speedLimit = effectiveSpeedLimit,
                        showSpeedLimit = showSpeedLimitSetting
                    )
                    SmartErrorDrawer(
                        errors = activeErrors, isOpen = isErrorDrawerOpen,
                        onClose = { isErrorDrawerOpen = false },
                        onDismissError = { activeErrors.remove(it) }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {

                    val cameraAlpha = if (currentGear == "R") 1f else 0.01f
                    val cameraZIndex = if (currentGear == "R") 30f else 0f

                    Box(modifier = Modifier
                        .fillMaxSize()
                        .alpha(cameraAlpha)
                        .zIndex(cameraZIndex)
                    ) {
                        ReverseCameraScreen(isReverseGear = currentGear == "R")

                        if (currentGear == "R") {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                Text(
                                    text = "REAR VIEW",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(top = 16.dp)
                                        .background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().zIndex(10f)) {
                        when (currentMapEngine) {
                            "MAPBOX" -> Viewport(
                                modifier = Modifier.fillMaxSize(), isNightPanel = isNightPanel, is3dModeExternal = is3dMapMode,
                                currentNavDistance = currentManeuverDistance, show3dBuildings = show3dBuildings,
                                searchEngine = currentSearchEngine, currentLocation = currentGpsLocation, routeGeoJson = routeGeoJson,
                                onRouteGeoJsonUpdated = handleGeoJsonUpdate,
                                onInstructionUpdated = { list -> navInstructionsList = list; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE },
                                onRouteDurationUpdated = { currentRouteDuration = it }, onSpeedLimitsUpdated = { limits -> speedLimitsList = limits },
                                onDestinationSet = { dest -> currentDestination = dest }, onCancelRoute = cancelRouteAction
                            )
                            "GOOGLE" -> GoogleViewport(
                                modifier = Modifier.fillMaxSize(), isNightPanel = isNightPanel, is3dModeExternal = is3dMapMode,
                                currentNavDistance = currentManeuverDistance, searchEngine = currentSearchEngine, currentLocation = currentGpsLocation, routeGeoJson = routeGeoJson,
                                onRouteGeoJsonUpdated = handleGeoJsonUpdate,
                                onInstructionUpdated = { list -> navInstructionsList = list; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE },
                                onRouteDurationUpdated = { currentRouteDuration = it }, onSpeedLimitsUpdated = { limits -> speedLimitsList = limits },
                                onDestinationSet = { dest -> currentDestination = dest }, onCancelRoute = cancelRouteAction
                            )
                        }
                    }

                    GearSelector(
                        currentGear = currentGear,
                        onGearSelected = { gear -> manualShiftTo(gear) },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(60.dp)
                            .fillMaxHeight(0.5f)
                            .background(Color(0xFF1E1E1E).copy(alpha = 0.9f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                            .zIndex(30f)
                    )
                }

                Dock(
                    modifier = Modifier.height(110.dp),
                    isLandscape = false,
                    isNightPanel = isNightPanel, onNightPanelToggle = { isNightPanel = !isNightPanel }, onOpenSettings = { isSettingsOpen = true }, onOpenApps = { isAppDrawerOpen = true }
                )
            }
        }

        if (isSettingsOpen) {
            BackHandler { isSettingsOpen = false }
            SettingsScreen(
                onClose = { isSettingsOpen = false },
                currentMapEngine = currentMapEngine, onMapEngineChange = { newEngine ->
                    currentMapEngine = newEngine;
                    sharedPrefs.edit().putString("map_engine", newEngine).apply();
                    cancelRouteAction()
                },
                currentSearchEngine = currentSearchEngine, onSearchEngineChange = { newEngine -> currentSearchEngine = newEngine; sharedPrefs.edit().putString("search_engine", newEngine).apply() },
                currentLocation = currentGpsLocation,
                currentObdMac = savedObdMacAddress, onObdMacChange = { newMac -> savedObdMacAddress = newMac; sharedPrefs.edit().putString("obd_mac", newMac).apply() },
                currentRouteGeoJson = routeGeoJson
            )
        }

        if (isAppDrawerOpen) {
            BackHandler { isAppDrawerOpen = false }
            AppDrawerScreen(onClose = { isAppDrawerOpen = false })
        }

        if (isNightPanel) {
            BackHandler { isNightPanel = false }
            NightPanelScreen(
                speed = effectiveSpeed,
                rpm = carStateSnapshot.rpm,
                error = carStateSnapshot.error,
                instruction = currentInstruction,
                currentNavDistance = currentManeuverDistance,
                onExit = { isNightPanel = false }
            )
        }
    }
}

@Composable
fun InstrumentClusterWrapper(
    modifier: Modifier,
    isLandscape: Boolean,
    carStateFlow: StateFlow<CarState>,
    gpsStatus: String?,
    batteryLevel: Int,
    instruction: NavInstruction?,
    currentNavDistance: Int?,
    gpsSpeed: Int,
    routeDuration: Int?,
    speedLimit: Int?,
    showSpeedLimit: Boolean
) {
    val carState by carStateFlow.collectAsState()
    val displaySpeed = if (carState.isConnected) carState.speed else gpsSpeed
    InstrumentCluster(
        modifier = modifier,
        isLandscape = isLandscape,
        carState = carState.copy(speed = displaySpeed),
        gpsStatus = gpsStatus,
        batteryLevel = batteryLevel,
        instruction = instruction,
        currentNavDistance = currentNavDistance,
        routeDuration = routeDuration,
        speedLimit = speedLimit,
        showSpeedLimit = showSpeedLimit
    )
}

fun getBatteryLevel(context: Context): Int {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
}

private fun pointToLineDistance(p: Location, a: Location, b: Location): Float {
    val xA = a.longitude
    val yA = a.latitude
    val xB = b.longitude
    val yB = b.latitude
    val xP = p.longitude
    val yP = p.latitude

    val dx = xB - xA
    val dy = yB - yA

    if (dx == 0.0 && dy == 0.0) {
        return p.distanceTo(a)
    }

    val t = ((xP - xA) * dx + (yP - yA) * dy) / (dx * dx + dy * dy)

    if (t <= 0.0) return p.distanceTo(a)
    if (t >= 1.0) return p.distanceTo(b)

    val closestLat = yA + t * dy
    val closestLon = xA + t * dx

    val closestPoint = Location("").apply {
        latitude = closestLat
        longitude = closestLon
    }

    return p.distanceTo(closestPoint)
}