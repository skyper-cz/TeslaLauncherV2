@file:Suppress("OPT_IN_USAGE", "DEPRECATION", "SpellCheckingInspection", "UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER")

package com.launchers.teslalauncherv2

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.launchers.teslalauncherv2.data.DTCManager
import com.launchers.teslalauncherv2.map.OfflineMapManager
import com.launchers.teslalauncherv2.obd.ObdDataManager
import com.launchers.teslalauncherv2.hardware.ReverseCameraScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI() // Start with immersive mode

        setContent {
            TeslaLauncherTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TeslaLayout() // Main layout entry point
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI() // Ensure immersive mode is active upon resume
    }

    // Configures the window to hide system bars and allow swipe to reveal
    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ObdDataManager.stop() // Clean up OBD resources
        android.os.Process.killProcess(android.os.Process.myPid()) // Force close application
        System.exit(0)
    }
}

// Defines the custom dark theme for the launcher
@Composable
fun TeslaLauncherTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(background = Color.Black, surface = Color.Black, onBackground = Color.White, onSurface = Color.White)
    MaterialTheme(colorScheme = darkColorScheme, content = content)
}

@Composable
fun TeslaLayout() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Observe car state (speed, RPM, etc.) from OBD manager
    val carStateFlow = ObdDataManager.carState
    val carStateSnapshot by carStateFlow.collectAsState()

    // Navigation state variables
    var navInstructionsList by remember { mutableStateOf<List<NavInstruction>>(emptyList()) }
    var currentInstructionIndex by remember { mutableIntStateOf(0) }
    var currentManeuverDistance by remember { mutableStateOf<Int?>(null) }

    // 🌟 HIGH-SPEED FIX: Tracks closest distance to the next node
    var lastMinDistance by remember { mutableStateOf(Double.MAX_VALUE) }

    // 🌟 NEW: Speed Limit Logic
    var speedLimitsList by remember { mutableStateOf<List<Int?>>(emptyList()) }
    var currentSpeedLimit by remember { mutableStateOf<Int?>(null) }

    val currentInstruction = navInstructionsList.getOrNull(currentInstructionIndex)

    var currentRouteDuration by remember { mutableStateOf<Int?>(null) }
    var routeGeoJson by rememberSaveable { mutableStateOf<String?>(null) }
    var currentSpeedGps by remember { mutableIntStateOf(0) }

    var batteryLevel by remember { mutableIntStateOf(getBatteryLevel(context)) }
    var currentGpsLocation by remember { mutableStateOf<Location?>(null) }

    // SharedPreferences for saving user settings
    val sharedPrefs = remember { context.getSharedPreferences("TeslaSettings", Context.MODE_PRIVATE) }
    var savedObdMacAddress by remember { mutableStateOf(sharedPrefs.getString("obd_mac", "00:10:CC:4F:36:03") ?: "") }
    var currentMapEngine by remember { mutableStateOf(sharedPrefs.getString("map_engine", "MAPBOX") ?: "MAPBOX") }
    var currentSearchEngine by remember { mutableStateOf(sharedPrefs.getString("search_engine", "MAPBOX") ?: "MAPBOX") }

    // 🌟 NEW: User setting to show/hide speed limits
    var showSpeedLimitSetting by remember { mutableStateOf(sharedPrefs.getBoolean("show_speed_limit", true)) }

    // UI state toggles
    var isNightPanel by rememberSaveable { mutableStateOf(false) }
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isAppDrawerOpen by rememberSaveable { mutableStateOf(false) }

    // Gear selection state
    var currentGear by rememberSaveable { mutableStateOf("P") }
    var lastManualShiftTime by remember { mutableLongStateOf(0L) }
    var is3dMapMode by remember { mutableStateOf(false) }
    var gpsStatus by remember { mutableStateOf<String?>("INIT") }
    var isLocationPermissionGranted by remember { mutableStateOf(false) }
    var isManualOverrideActive by remember { mutableStateOf(false) }

    // Use OBD speed if connected, otherwise fallback to GPS
    val effectiveSpeed = if (carStateSnapshot.isConnected) carStateSnapshot.speed else currentSpeedGps

    // 🌟 REFRESH SETTINGS when menu closes
    LaunchedEffect(isSettingsOpen) {
        showSpeedLimitSetting = sharedPrefs.getBoolean("show_speed_limit", true)
    }

    // Handle manual gear overrides to prevent rapid automatic switching
    LaunchedEffect(lastManualShiftTime) {
        if (lastManualShiftTime > 0) {
            isManualOverrideActive = true
            delay(8000)
            isManualOverrideActive = false
        }
    }

    // 🌟 SMART ETA COUNTDOWN: Decrement travel time only when moving
    LaunchedEffect(navInstructionsList, effectiveSpeed) {
        while (navInstructionsList.isNotEmpty() && currentRouteDuration != null) {
            delay(1000) // Tick every second
            // If moving (over 3 km/h), decrease the remaining time.
            if (effectiveSpeed > 3) {
                currentRouteDuration = (currentRouteDuration!! - 1).coerceAtLeast(0)
            }
        }
    }

    // Automatic gear and map mode adjustment based on speed
    LaunchedEffect(effectiveSpeed, currentGear, isManualOverrideActive) {
        if (currentGear == "D") is3dMapMode = true else if (currentGear == "P") is3dMapMode = false

        if (!isManualOverrideActive) {
            if (currentGear != "R") {
                if (effectiveSpeed > 5 && currentGear != "D") currentGear = "D"
                if (effectiveSpeed < 4 && currentGear != "P") currentGear = "P"
            }
        }
    }

    // Function to manually shift gears, respecting camera permissions for reverse
    fun manualShiftTo(gear: String) {
        lastManualShiftTime = System.currentTimeMillis()
        if (gear == "R") {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                currentGear = "R"
            } else { currentGear = gear }
        } else currentGear = gear
    }

    // Permission launchers
    val locationPermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions(), onResult = { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) { isLocationPermissionGranted = true; gpsStatus = "READY" } else { gpsStatus = "NO PERM" }
    })

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions(), onResult = {
        if (savedObdMacAddress.isNotBlank()) ObdDataManager.connect(context, savedObdMacAddress)
    })

    // Attempt OBD connection when MAC address is available
    LaunchedEffect(savedObdMacAddress) {
        if (savedObdMacAddress.isNotBlank()) {
            val hasPerm = if (Build.VERSION.SDK_INT >= 31) ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED else true
            if (hasPerm) {
                ObdDataManager.stop()
                delay(500)
                ObdDataManager.connect(context, savedObdMacAddress)
            }
        }
    }

    // Initial setup: request permissions, prefetch apps, and check offline maps
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) isLocationPermissionGranted = true
        else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

        if (Build.VERSION.SDK_INT >= 31) {
            bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            if (savedObdMacAddress.isNotBlank()) ObdDataManager.connect(context, savedObdMacAddress)
        }

        launch(Dispatchers.IO) { AppManager.prefetchApps(context) }

        // Cleanup expired offline map routes
        launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("offline_maps_status", Context.MODE_PRIVATE)
            val routes = prefs.getStringSet("saved_routes", mutableSetOf()) ?: mutableSetOf()
            val validRoutes = mutableSetOf<String>()
            var changed = false
            val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
            val now = System.currentTimeMillis()

            for (routeData in routes) {
                val parts = routeData.split("|")
                if (parts.size >= 2) {
                    val id = parts[0]
                    val timestamp = parts[1].toLongOrNull() ?: 0L
                    if (now - timestamp > thirtyDaysInMillis) {
                        try { OfflineMapManager.deleteRegion(id) } catch (e: Exception) { }
                        changed = true
                    } else {
                        validRoutes.add(routeData)
                    }
                }
            }
            if (changed) { prefs.edit().putStringSet("saved_routes", validRoutes).apply() }
        }

        // Continuously update battery level
        while (true) { batteryLevel = getBatteryLevel(context); delay(60000) }
    }

    // Manage LocationListener using DisposableEffect
    DisposableEffect(isLocationPermissionGranted, currentInstruction) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                gpsStatus = null
                currentGpsLocation = location
                if (location.hasSpeed()) currentSpeedGps = (location.speed * 3.6f).toInt()

                val instruction = currentInstruction
                if (instruction?.maneuverPoint != null) {
                    val target = Location("T").apply {
                        latitude = instruction.maneuverPoint.latitude()
                        longitude = instruction.maneuverPoint.longitude()
                    }
                    val dist = location.distanceTo(target).toInt()

                    // 🌟 FIXED HIGH-SPEED LOGIC: Node is considered passed if within 30m,
                    // or if it was within 150m and the distance is now increasing (overshoot).
                    if (dist < 30 || (dist > lastMinDistance && lastMinDistance < 150)) {
                        if (currentInstructionIndex < navInstructionsList.size - 1) {
                            currentInstructionIndex++
                            lastMinDistance = Double.MAX_VALUE // Reset for the next node

                            // 🌟 UPDATE SPEED LIMIT for new segment
                            currentSpeedLimit = speedLimitsList.getOrNull(currentInstructionIndex)

                        } else {
                            // Route Finished
                            navInstructionsList = emptyList()
                            routeGeoJson = null
                            currentManeuverDistance = null
                            currentRouteDuration = null
                            speedLimitsList = emptyList()
                            currentSpeedLimit = null
                            lastMinDistance = Double.MAX_VALUE
                            Toast.makeText(context, "You have arrived!", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        if (dist < lastMinDistance) {
                            lastMinDistance = dist.toDouble()
                        }
                        currentManeuverDistance = dist
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
            } catch (e: Exception) {
                gpsStatus = "ERR GPS"
            }
        }

        onDispose {
            locationManager.removeUpdates(listener)
        }
    }

    // 🌟 MAIN UI LAYOUT
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // 🌟 1. LAYOUT CONTENT
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel in Landscape
                Column(modifier = Modifier.fillMaxWidth(0.32f).fillMaxHeight()) {
                    InstrumentClusterWrapper(
                        modifier = Modifier.weight(0.60f),
                        isLandscape = isLandscape,
                        carStateFlow = carStateFlow, gpsStatus = gpsStatus, batteryLevel = batteryLevel,
                        instruction = currentInstruction, currentNavDistance = currentManeuverDistance,
                        gpsSpeed = currentSpeedGps, routeDuration = currentRouteDuration,
                        speedLimit = currentSpeedLimit, // 🌟 Passing the limit
                        showSpeedLimit = showSpeedLimitSetting, // 🌟 Passing user preference
                        onCancelRoute = { navInstructionsList = emptyList(); currentRouteDuration = null; routeGeoJson = null; speedLimitsList = emptyList(); currentSpeedLimit = null }
                    )
                    HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
                    Dock(
                        modifier = Modifier.weight(0.40f),
                        isLandscape = true,
                        isNightPanel = isNightPanel, onNightPanelToggle = { isNightPanel = !isNightPanel }, onOpenSettings = { isSettingsOpen = true }, onOpenApps = { isAppDrawerOpen = true }
                    )
                }

                // Gear Selector
                GearSelector(
                    currentGear = currentGear,
                    onGearSelected = { gear -> manualShiftTo(gear) },
                    modifier = Modifier.width(60.dp).fillMaxHeight()
                )

                // Map View or Reverse Camera
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (currentGear != "R") {
                        when (currentMapEngine) {
                            "MAPBOX" -> Viewport(
                                modifier = Modifier.fillMaxSize(), isNightPanel = isNightPanel, is3dModeExternal = is3dMapMode,
                                searchEngine = currentSearchEngine, currentLocation = currentGpsLocation, routeGeoJson = routeGeoJson,
                                onRouteGeoJsonUpdated = { routeGeoJson = it },
                                onInstructionUpdated = { list -> navInstructionsList = list; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE },
                                onRouteDurationUpdated = { currentRouteDuration = it },
                                // 🌟 NEW: Speed limits from NetworkManager
                                onSpeedLimitsUpdated = { limits -> speedLimitsList = limits; currentSpeedLimit = limits.firstOrNull() }
                            )
                            "GOOGLE" -> GoogleViewport(
                                modifier = Modifier.fillMaxSize(), isNightPanel = isNightPanel, is3dModeExternal = is3dMapMode,
                                searchEngine = currentSearchEngine, currentLocation = currentGpsLocation, routeGeoJson = routeGeoJson,
                                onRouteGeoJsonUpdated = { routeGeoJson = it },
                                onInstructionUpdated = { list -> navInstructionsList = list; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE },
                                onRouteDurationUpdated = { currentRouteDuration = it },
                                // 🌟 NEW: Speed limits from NetworkManager (Google usually returns empty)
                                onSpeedLimitsUpdated = { limits -> speedLimitsList = limits; currentSpeedLimit = limits.firstOrNull() }
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                            ReverseCameraScreen()
                            Text("REAR VIEW", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        } else {
            // Portrait Layout
            Column(modifier = Modifier.fillMaxSize()) {
                InstrumentClusterWrapper(
                    modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                    isLandscape = isLandscape,
                    carStateFlow = carStateFlow, gpsStatus = gpsStatus, batteryLevel = batteryLevel,
                    instruction = currentInstruction, currentNavDistance = currentManeuverDistance,
                    gpsSpeed = currentSpeedGps, routeDuration = currentRouteDuration,
                    speedLimit = currentSpeedLimit,
                    showSpeedLimit = showSpeedLimitSetting,
                    onCancelRoute = { navInstructionsList = emptyList(); currentRouteDuration = null; routeGeoJson = null; speedLimitsList = emptyList(); currentSpeedLimit = null }
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (currentGear != "R") {
                        when (currentMapEngine) {
                            "MAPBOX" -> Viewport(
                                modifier = Modifier.fillMaxSize(), isNightPanel = isNightPanel, is3dModeExternal = is3dMapMode,
                                searchEngine = currentSearchEngine, currentLocation = currentGpsLocation, routeGeoJson = routeGeoJson,
                                onRouteGeoJsonUpdated = { routeGeoJson = it },
                                onInstructionUpdated = { list -> navInstructionsList = list; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE },
                                onRouteDurationUpdated = { currentRouteDuration = it },
                                onSpeedLimitsUpdated = { limits -> speedLimitsList = limits; currentSpeedLimit = limits.firstOrNull() }
                            )
                            "GOOGLE" -> GoogleViewport(
                                modifier = Modifier.fillMaxSize(), isNightPanel = isNightPanel, is3dModeExternal = is3dMapMode,
                                searchEngine = currentSearchEngine, currentLocation = currentGpsLocation, routeGeoJson = routeGeoJson,
                                onRouteGeoJsonUpdated = { routeGeoJson = it },
                                onInstructionUpdated = { list -> navInstructionsList = list; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE },
                                onRouteDurationUpdated = { currentRouteDuration = it },
                                onSpeedLimitsUpdated = { limits -> speedLimitsList = limits; currentSpeedLimit = limits.firstOrNull() }
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                            ReverseCameraScreen()
                            Text("REAR VIEW", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp))
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
                    )
                }

                Dock(
                    modifier = Modifier.height(110.dp),
                    isLandscape = false,
                    isNightPanel = isNightPanel, onNightPanelToggle = { isNightPanel = !isNightPanel }, onOpenSettings = { isSettingsOpen = true }, onOpenApps = { isAppDrawerOpen = true }
                )
            }
        }

        // 🌟 2. GLOBAL OVERLAYS (Errors, Settings, Night Panel)

        // INTELLIGENT ERROR ALERT (Always on top)
        if (!carStateSnapshot.error.isNullOrEmpty()) {
            TeslaErrorAlert(errorCode = carStateSnapshot.error) {
                // Logic to dismiss/clear error could go here
            }
        }

        if (isSettingsOpen) {
            BackHandler { isSettingsOpen = false }
            SettingsScreen(
                onClose = { isSettingsOpen = false },
                currentMapEngine = currentMapEngine, onMapEngineChange = { newEngine -> currentMapEngine = newEngine; sharedPrefs.edit().putString("map_engine", newEngine).apply() },
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

// Wrapper component to pass necessary state to the InstrumentCluster
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
    speedLimit: Int?, // Retained for parameter passing
    showSpeedLimit: Boolean, // New parameter
    onCancelRoute: () -> Unit
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
        speedLimit = speedLimit, // Passed parameter
        showSpeedLimit = showSpeedLimit, // Passed parameter
        onCancelRoute = onCancelRoute)
}

// Helper function to fetch system battery level
fun getBatteryLevel(context: Context): Int {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
}