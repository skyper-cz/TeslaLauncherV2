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
import com.launchers.teslalauncherv2.obd.ObdDataManager
import com.launchers.teslalauncherv2.hardware.ReverseCameraScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        setContent {
            TeslaLauncherTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TeslaLayout()
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
fun TeslaLayout() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val carStateFlow = ObdDataManager.carState
    val carStateSnapshot by carStateFlow.collectAsState()

    var navInstructionsList by remember { mutableStateOf<List<NavInstruction>>(emptyList()) }
    var currentInstructionIndex by remember { mutableIntStateOf(0) }
    var currentManeuverDistance by remember { mutableStateOf<Int?>(null) }
    var lastMinDistance by remember { mutableStateOf(Double.MAX_VALUE) }

    var speedLimitsList by remember { mutableStateOf<List<Int?>>(emptyList()) }
    var currentSpeedLimit by remember { mutableStateOf<Int?>(null) }

    // 🌟 NOVÉ: Uložení fyzických souřadnic trasy pro párování limitů
    var routeCoordinates by remember { mutableStateOf<List<Location>>(emptyList()) }

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

    var isNightPanel by rememberSaveable { mutableStateOf(false) }
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isAppDrawerOpen by rememberSaveable { mutableStateOf(false) }

    var currentGear by rememberSaveable { mutableStateOf("P") }
    var lastManualShiftTime by remember { mutableLongStateOf(0L) }
    var is3dMapMode by remember { mutableStateOf(false) }
    var gpsStatus by remember { mutableStateOf<String?>("INIT") }
    var isLocationPermissionGranted by remember { mutableStateOf(false) }
    var isManualOverrideActive by remember { mutableStateOf(false) }

    val effectiveSpeed = if (carStateSnapshot.isConnected) carStateSnapshot.speed else currentSpeedGps

    LaunchedEffect(isSettingsOpen) {
        showSpeedLimitSetting = sharedPrefs.getBoolean("show_speed_limit", true)
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

    LaunchedEffect(effectiveSpeed, currentGear, isManualOverrideActive) {
        if (currentGear == "D") is3dMapMode = true else if (currentGear == "P") is3dMapMode = false

        if (!isManualOverrideActive) {
            if (currentGear != "R") {
                if (effectiveSpeed > 5 && currentGear != "D") currentGear = "D"
                if (effectiveSpeed < 4 && currentGear != "P") currentGear = "P"
            }
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

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) isLocationPermissionGranted = true
        else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

        if (Build.VERSION.SDK_INT >= 31) {
            bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            if (savedObdMacAddress.isNotBlank()) ObdDataManager.connect(context, savedObdMacAddress)
        }

        launch(Dispatchers.IO) { AppManager.prefetchApps(context) }

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

        while (true) { batteryLevel = getBatteryLevel(context); delay(60000) }
    }

    DisposableEffect(isLocationPermissionGranted, currentInstruction, routeCoordinates) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                gpsStatus = null
                currentGpsLocation = location
                if (location.hasSpeed()) currentSpeedGps = (location.speed * 3.6f).toInt()

                // 🌟 NOVÁ LOGIKA PRO AKTUALIZACI RYCHLOSTI BĚHEM JÍZDY
                if (routeCoordinates.isNotEmpty() && speedLimitsList.isNotEmpty()) {
                    var minDistance = Float.MAX_VALUE
                    var closestIndex = 0

                    // Najdeme nejbližší bod na mapové čáře k aktuální GPS pozici
                    for (i in routeCoordinates.indices) {
                        val dist = location.distanceTo(routeCoordinates[i])
                        if (dist < minDistance) {
                            minDistance = dist
                            closestIndex = i
                        }
                    }

                    // Napárujeme ho na rychlostní limity
                    val limitIndex = closestIndex.coerceAtMost(speedLimitsList.size - 1)
                    currentSpeedLimit = speedLimitsList.getOrNull(limitIndex)
                }

                // Logika navigačních instrukcí
                val instruction = currentInstruction
                if (instruction?.maneuverPoint != null) {
                    val target = Location("T").apply {
                        latitude = instruction.maneuverPoint.latitude()
                        longitude = instruction.maneuverPoint.longitude()
                    }
                    val dist = location.distanceTo(target).toInt()

                    if (dist < 30 || (dist > lastMinDistance && lastMinDistance < 150)) {
                        if (currentInstructionIndex < navInstructionsList.size - 1) {
                            currentInstructionIndex++
                            lastMinDistance = Double.MAX_VALUE
                        } else {
                            navInstructionsList = emptyList()
                            routeGeoJson = null
                            routeCoordinates = emptyList()
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

    // 🌟 Zjednodušená akce na zrušení trasy
    val cancelRouteAction = {
        navInstructionsList = emptyList()
        currentRouteDuration = null
        routeGeoJson = null
        routeCoordinates = emptyList()
        speedLimitsList = emptyList()
        currentSpeedLimit = null
    }

    // 🌟 Metoda, která z GeoJSONu vytáhne fyzické body cesty (aby s nimi šlo párovat rychlost)
    val handleGeoJsonUpdate: (String?) -> Unit = { geo ->
        routeGeoJson = geo
        if (geo != null) {
            try {
                val coords = mutableListOf<Location>()
                val featureCollection = com.mapbox.geojson.FeatureCollection.fromJson(geo)
                val lineString = featureCollection.features()?.firstOrNull()?.geometry() as? com.mapbox.geojson.LineString
                lineString?.coordinates()?.forEach { pt ->
                    coords.add(Location("").apply { latitude = pt.latitude(); longitude = pt.longitude() })
                }
                routeCoordinates = coords
            } catch (e: Exception) {
                routeCoordinates = emptyList()
            }
        } else {
            routeCoordinates = emptyList()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxWidth(0.32f).fillMaxHeight()) {
                    InstrumentClusterWrapper(
                        modifier = Modifier.weight(0.60f),
                        isLandscape = isLandscape,
                        carStateFlow = carStateFlow, gpsStatus = gpsStatus, batteryLevel = batteryLevel,
                        instruction = currentInstruction, currentNavDistance = currentManeuverDistance,
                        gpsSpeed = currentSpeedGps, routeDuration = currentRouteDuration,
                        speedLimit = currentSpeedLimit, showSpeedLimit = showSpeedLimitSetting
                    )
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
                    if (currentGear != "R") {
                        when (currentMapEngine) {
                            "MAPBOX" -> Viewport(
                                modifier = Modifier.fillMaxSize(), isNightPanel = isNightPanel, is3dModeExternal = is3dMapMode,
                                searchEngine = currentSearchEngine, currentLocation = currentGpsLocation, routeGeoJson = routeGeoJson,
                                onRouteGeoJsonUpdated = handleGeoJsonUpdate,
                                onInstructionUpdated = { list -> navInstructionsList = list; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE },
                                onRouteDurationUpdated = { currentRouteDuration = it },
                                onSpeedLimitsUpdated = { limits -> speedLimitsList = limits },
                                onCancelRoute = cancelRouteAction
                            )
                            "GOOGLE" -> GoogleViewport(
                                modifier = Modifier.fillMaxSize(), isNightPanel = isNightPanel, is3dModeExternal = is3dMapMode,
                                searchEngine = currentSearchEngine, currentLocation = currentGpsLocation, routeGeoJson = routeGeoJson,
                                onRouteGeoJsonUpdated = handleGeoJsonUpdate,
                                onInstructionUpdated = { list -> navInstructionsList = list; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE },
                                onRouteDurationUpdated = { currentRouteDuration = it },
                                onSpeedLimitsUpdated = { limits -> speedLimitsList = limits },
                                onCancelRoute = cancelRouteAction
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
            Column(modifier = Modifier.fillMaxSize()) {
                InstrumentClusterWrapper(
                    modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                    isLandscape = isLandscape,
                    carStateFlow = carStateFlow, gpsStatus = gpsStatus, batteryLevel = batteryLevel,
                    instruction = currentInstruction, currentNavDistance = currentManeuverDistance,
                    gpsSpeed = currentSpeedGps, routeDuration = currentRouteDuration,
                    speedLimit = currentSpeedLimit, showSpeedLimit = showSpeedLimitSetting
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (currentGear != "R") {
                        when (currentMapEngine) {
                            "MAPBOX" -> Viewport(
                                modifier = Modifier.fillMaxSize(), isNightPanel = isNightPanel, is3dModeExternal = is3dMapMode,
                                searchEngine = currentSearchEngine, currentLocation = currentGpsLocation, routeGeoJson = routeGeoJson,
                                onRouteGeoJsonUpdated = handleGeoJsonUpdate,
                                onInstructionUpdated = { list -> navInstructionsList = list; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE },
                                onRouteDurationUpdated = { currentRouteDuration = it },
                                onSpeedLimitsUpdated = { limits -> speedLimitsList = limits },
                                onCancelRoute = cancelRouteAction
                            )
                            "GOOGLE" -> GoogleViewport(
                                modifier = Modifier.fillMaxSize(), isNightPanel = isNightPanel, is3dModeExternal = is3dMapMode,
                                searchEngine = currentSearchEngine, currentLocation = currentGpsLocation, routeGeoJson = routeGeoJson,
                                onRouteGeoJsonUpdated = handleGeoJsonUpdate,
                                onInstructionUpdated = { list -> navInstructionsList = list; currentInstructionIndex = 0; lastMinDistance = Double.MAX_VALUE },
                                onRouteDurationUpdated = { currentRouteDuration = it },
                                onSpeedLimitsUpdated = { limits -> speedLimitsList = limits },
                                onCancelRoute = cancelRouteAction
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

        if (!carStateSnapshot.error.isNullOrEmpty()) {
            TeslaErrorAlert(errorCode = carStateSnapshot.error) { }
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