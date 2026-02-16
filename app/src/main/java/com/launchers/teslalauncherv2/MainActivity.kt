@file:Suppress("OPT_IN_USAGE", "DEPRECATION", "SpellCheckingInspection", "UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER")

package com.launchers.teslalauncherv2

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

// IMPORTY PROJEKTU
import com.launchers.teslalauncherv2.ui.Dock
import com.launchers.teslalauncherv2.ui.GearSelector
import com.launchers.teslalauncherv2.ui.InstrumentCluster
import com.launchers.teslalauncherv2.ui.NightPanelScreen
import com.launchers.teslalauncherv2.hardware.ReverseCameraScreen
import com.launchers.teslalauncherv2.ui.SettingsScreen
import com.launchers.teslalauncherv2.ui.TeslaSearchBar
import com.launchers.teslalauncherv2.data.CarState
import com.launchers.teslalauncherv2.data.NavInstruction
import com.launchers.teslalauncherv2.obd.ObdDataManager
import com.launchers.teslalauncherv2.data.SearchSuggestion
import com.launchers.teslalauncherv2.map.NetworkManager

// MAPBOX IMPORTY
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.FillExtrusionLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.eq
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.get
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.literal
import com.mapbox.maps.plugin.PuckBearing

// GOOGLE MAPS IMPORTY
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Polyline


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
    val carStateFlow = ObdDataManager.carState
    val carStateSnapshot by carStateFlow.collectAsState()

    var currentInstruction by remember { mutableStateOf<NavInstruction?>(null) }
    var currentRouteDuration by remember { mutableStateOf<Int?>(null) }

    // 🌟 ZMĚNA 1: TRASA JE ULOŽENÁ ZDE NAHOŘE (Nezmizí při couvací kameře)
    var routeGeoJson by rememberSaveable { mutableStateOf<String?>(null) }

    var currentSpeedGps by remember { mutableIntStateOf(0) }
    var batteryLevel by remember { mutableIntStateOf(getBatteryLevel(context)) }
    var currentGpsLocation by remember { mutableStateOf<Location?>(null) }

    val sharedPrefs = remember { context.getSharedPreferences("TeslaSettings", Context.MODE_PRIVATE) }
    var savedObdMacAddress by remember {
        mutableStateOf(sharedPrefs.getString("obd_mac", "00:10:CC:4F:36:03") ?: "00:10:CC:4F:36:03")
    }

    var currentMapEngine by remember {
        mutableStateOf(sharedPrefs.getString("map_engine", "MAPBOX") ?: "MAPBOX")
    }
    var currentSearchEngine by remember {
        mutableStateOf(sharedPrefs.getString("search_engine", "MAPBOX") ?: "MAPBOX")
    }

    var isNightPanel by rememberSaveable { mutableStateOf(false) }
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isAppDrawerOpen by rememberSaveable { mutableStateOf(false) }

    var currentGear by rememberSaveable { mutableStateOf("P") }
    var lastManualShiftTime by remember { mutableLongStateOf(0L) }
    var is3dMapMode by remember { mutableStateOf(false) }
    var gpsStatus by remember { mutableStateOf<String?>("INIT") }
    var isLocationPermissionGranted by remember { mutableStateOf(false) }
    var isManualOverrideActive by remember { mutableStateOf(false) }

    LaunchedEffect(lastManualShiftTime) {
        if (lastManualShiftTime > 0) {
            isManualOverrideActive = true
            delay(8000)
            isManualOverrideActive = false
        }
    }

    LaunchedEffect(carStateSnapshot.speed, currentGear, isManualOverrideActive) {
        if (currentGear == "D") is3dMapMode = true else if (currentGear == "P") is3dMapMode = false
        if (!isManualOverrideActive) {
            if (carStateSnapshot.speed > 5 && currentGear != "D" && currentGear != "R") currentGear = "D"
            if (carStateSnapshot.speed < 4 && currentGear != "P") currentGear = "P"
        }
    }

    fun manualShiftTo(gear: String) {
        lastManualShiftTime = System.currentTimeMillis()
        if (gear == "R") {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                currentGear = "R"
            } else {
                currentGear = gear
            }
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

        launch(Dispatchers.IO) { com.launchers.teslalauncherv2.data.AppManager.prefetchApps(context) }

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
                        try { com.launchers.teslalauncherv2.map.OfflineMapManager.deleteRegion(id) } catch (e: Exception) { }
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

    LaunchedEffect(isLocationPermissionGranted) {
        if (isLocationPermissionGranted) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                gpsStatus = "SEARCHING..."
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        gpsStatus = null
                        currentGpsLocation = location
                        if (location.hasSpeed()) currentSpeedGps = (location.speed * 3.6f).toInt()
                        val instruction = currentInstruction
                        if (instruction?.maneuverPoint != null) {
                            val target = Location("T"); target.latitude = instruction.maneuverPoint.latitude(); target.longitude = instruction.maneuverPoint.longitude()
                            if (location.distanceTo(target) < 30) {
                                currentInstruction = null
                                currentRouteDuration = null
                                routeGeoJson = null // V cíli smažeme i čáru
                            } else {
                                currentInstruction = instruction.copy(distance = location.distanceTo(target).toInt())
                            }
                        }
                    }
                    override fun onProviderDisabled(p: String) { gpsStatus = "GPS OFF" }
                    override fun onProviderEnabled(p: String) { gpsStatus = "SEARCHING..." }
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                }
                if (locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)
                if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener)
            } catch (e: Exception) { gpsStatus = "ERR"; e.printStackTrace() }
        }
    }

    val finalSpeed = if (carStateSnapshot.isConnected) carStateSnapshot.speed else currentSpeedGps

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            InstrumentClusterWrapper(
                modifier = Modifier.weight(0.25f),
                carStateFlow = carStateFlow,
                gpsStatus = gpsStatus,
                batteryLevel = batteryLevel,
                instruction = currentInstruction,
                gpsSpeed = currentSpeedGps,
                routeDuration = currentRouteDuration,
                onCancelRoute = {
                    currentInstruction = null
                    currentRouteDuration = null
                    routeGeoJson = null // 🌟 ZRUŠENÍ TRASY SMAŽE I ČÁRU
                }
            )

            Box(modifier = Modifier.weight(0.60f).fillMaxWidth().background(Color.Black)) {

                if (currentGear != "R") {
                    when (currentMapEngine) {
                        "MAPBOX" -> {
                            Viewport(
                                modifier = Modifier.fillMaxSize(),
                                isNightPanel = isNightPanel,
                                is3dModeExternal = is3dMapMode,
                                searchEngine = currentSearchEngine,
                                currentLocation = currentGpsLocation,
                                instruction = currentInstruction,
                                routeGeoJson = routeGeoJson, // 🌟 PŘEDÁVÁME
                                onRouteGeoJsonUpdated = { routeGeoJson = it }, // 🌟 PŘEDÁVÁME
                                onInstructionUpdated = { currentInstruction = it },
                                onRouteDurationUpdated = { currentRouteDuration = it }
                            )
                        }
                        "GOOGLE" -> {
                            if (isGmsAvailable(context)) {
                                GoogleViewport(
                                    modifier = Modifier.fillMaxSize(),
                                    isNightPanel = isNightPanel,
                                    is3dModeExternal = is3dMapMode,
                                    searchEngine = currentSearchEngine,
                                    currentLocation = currentGpsLocation,
                                    instruction = currentInstruction,
                                    routeGeoJson = routeGeoJson, // 🌟 PŘEDÁVÁME
                                    onRouteGeoJsonUpdated = { routeGeoJson = it }, // 🌟 PŘEDÁVÁME
                                    onInstructionUpdated = { currentInstruction = it },
                                    onRouteDurationUpdated = { currentRouteDuration = it }
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF222222)), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.ErrorOutline, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("GMS nedostupné", color = Color.White)
                                        Button(onClick = { currentMapEngine = "MAPBOX" }) { Text("Zpět na Mapbox") }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        ReverseCameraScreen()
                        Text("REAR VIEW", color = Color.White, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                                .background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp))
                    }
                }

                GearSelector(currentGear = currentGear, onGearSelected = { gear -> manualShiftTo(gear) }, modifier = Modifier.align(Alignment.CenterEnd))
            }

            Dock(
                modifier = Modifier.weight(0.15f),
                isNightPanel = isNightPanel,
                onNightPanelToggle = { isNightPanel = !isNightPanel },
                onOpenSettings = { isSettingsOpen = true },
                onOpenApps = { isAppDrawerOpen = true }
            )
        }

        if (isSettingsOpen) {
            BackHandler { isSettingsOpen = false }
            SettingsScreen(
                onClose = { isSettingsOpen = false },
                currentMapEngine = currentMapEngine,
                onMapEngineChange = { newEngine ->
                    currentMapEngine = newEngine
                    sharedPrefs.edit().putString("map_engine", newEngine).apply()
                },
                currentSearchEngine = currentSearchEngine,
                onSearchEngineChange = { newEngine ->
                    currentSearchEngine = newEngine
                    sharedPrefs.edit().putString("search_engine", newEngine).apply()
                },
                currentLocation = currentGpsLocation,
                currentObdMac = savedObdMacAddress,
                onObdMacChange = { newMac ->
                    savedObdMacAddress = newMac
                    sharedPrefs.edit().putString("obd_mac", newMac).apply()
                },
                currentRouteGeoJson = routeGeoJson // 🌟 PŘEDÁVÁME TRASU DO NASTAVENÍ PRO STAŽENÍ
            )
        }

        if (isAppDrawerOpen) {
            BackHandler { isAppDrawerOpen = false }
            com.launchers.teslalauncherv2.ui.AppDrawerScreen(onClose = { isAppDrawerOpen = false })
        }

        if (isNightPanel) {
            BackHandler { isNightPanel = false }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                NightPanelScreen(speed = finalSpeed, rpm = carStateSnapshot.rpm, error = carStateSnapshot.error, instruction = currentInstruction, onExit = { isNightPanel = false })
            }
        }
    }
}

@Composable
fun InstrumentClusterWrapper(
    modifier: Modifier,
    carStateFlow: StateFlow<CarState>,
    gpsStatus: String?,
    batteryLevel: Int,
    instruction: NavInstruction?,
    gpsSpeed: Int,
    routeDuration: Int?,
    onCancelRoute: () -> Unit
) {
    val carState by carStateFlow.collectAsState()
    val displaySpeed = if (carState.isConnected) carState.speed else gpsSpeed
    InstrumentCluster(modifier = modifier, carState = carState.copy(speed = displaySpeed), gpsStatus = gpsStatus, batteryLevel = batteryLevel, instruction = instruction, routeDuration = routeDuration, onCancelRoute = onCancelRoute)
}

// ==========================================
// MAPBOX VIEWPORT
// ==========================================
@Composable
fun Viewport(
    modifier: Modifier = Modifier,
    isNightPanel: Boolean = false,
    is3dModeExternal: Boolean = false,
    searchEngine: String,
    currentLocation: Location?,
    instruction: NavInstruction?,
    routeGeoJson: String?,
    onRouteGeoJsonUpdated: (String?) -> Unit,
    onInstructionUpdated: (NavInstruction?) -> Unit,
    onRouteDurationUpdated: (Int?) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val mapViewportState = rememberMapViewportState { setCameraOptions { center(Point.fromLngLat(14.4378, 50.0755)); zoom(11.0); pitch(0.0) } }

    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val googleApiKey = remember { try { val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA); ai.metaData.getString("com.google.android.geo.API_KEY") ?: "" } catch (e: Exception) { "" } }

    LaunchedEffect(instruction) {
        if (instruction == null) {
            onRouteGeoJsonUpdated(null)
        }
    }

    LaunchedEffect(is3dModeExternal) {
        if (is3dModeExternal) mapViewportState.transitionToFollowPuckState(FollowPuckViewportStateOptions.Builder().pitch(60.0).zoom(16.0).bearing(FollowPuckViewportStateBearing.SyncWithLocationPuck).build())
        else mapViewportState.transitionToFollowPuckState(FollowPuckViewportStateOptions.Builder().pitch(0.0).zoom(13.0).bearing(FollowPuckViewportStateBearing.Constant(0.0)).build())
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            delay(300)
            if (searchEngine == "GOOGLE") NetworkManager.fetchGoogleSuggestions(searchQuery, googleApiKey) { results -> suggestions = results }
            else NetworkManager.fetchSuggestions(searchQuery, context) { results -> suggestions = results }
        } else suggestions = emptyList()
    }

    fun performSearchAndRoute(destinationPoint: Point) {
        focusManager.clearFocus()
        suggestions = emptyList()
        searchQuery = ""

        val tPitch = if (is3dModeExternal) 60.0 else 0.0
        val tZoom = if (is3dModeExternal) 16.0 else 13.0
        val tBear = if (is3dModeExternal) FollowPuckViewportStateBearing.SyncWithLocationPuck else FollowPuckViewportStateBearing.Constant(0.0)
        mapViewportState.transitionToFollowPuckState(FollowPuckViewportStateOptions.Builder().bearing(tBear).pitch(tPitch).zoom(tZoom).build())

        val currentPoint = if (currentLocation != null) Point.fromLngLat(currentLocation.longitude, currentLocation.latitude) else (mapViewportState.cameraState?.center ?: Point.fromLngLat(14.4378, 50.0755))

        if (searchEngine == "GOOGLE") {
            NetworkManager.fetchGoogleRoute(currentPoint, destinationPoint, googleApiKey) { geo, instr, dur ->
                scope.launch(Dispatchers.Main) { onRouteGeoJsonUpdated(geo); onInstructionUpdated(instr); onRouteDurationUpdated(dur) }
            }
        } else {
            NetworkManager.fetchRouteManual(currentPoint, destinationPoint, context) { geo, instr, dur ->
                scope.launch(Dispatchers.Main) { onRouteGeoJsonUpdated(geo); onInstructionUpdated(instr); onRouteDurationUpdated(dur) }
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth().background(Color.DarkGray)) {
        TeslaMap(modifier = Modifier.fillMaxSize(), mapViewportState = mapViewportState, routeGeoJson = routeGeoJson, is3dMode = is3dModeExternal)
        val uiAlpha by animateFloatAsState(targetValue = if (isNightPanel) 0f else 1f, label = "UI Fade")
        if (uiAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().alpha(uiAlpha)) {
                Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).fillMaxWidth(0.6f)) {
                    TeslaSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, onSearch = { if (suggestions.isNotEmpty()) performSearchAndRoute(suggestions.first().point) })
                    if (suggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).background(Color.Black.copy(alpha = 0.95f), shape = RoundedCornerShape(16.dp))) {
                            items(suggestions) { place ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { performSearchAndRoute(place.point) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, tint = Color.Cyan, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(12.dp)); Text(place.name, color = Color.White, fontSize = 16.sp)
                                }
                                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                            }
                        }
                    }
                }

                FloatingActionButton(onClick = {
                    val tPitch = if (is3dModeExternal) 60.0 else 0.0
                    val tZoom = if (is3dModeExternal) 16.0 else 13.0
                    val tBear = if (is3dModeExternal) FollowPuckViewportStateBearing.SyncWithLocationPuck else FollowPuckViewportStateBearing.Constant(0.0)
                    mapViewportState.transitionToFollowPuckState(FollowPuckViewportStateOptions.Builder().bearing(tBear).pitch(tPitch).zoom(tZoom).build())
                }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), containerColor = Color.Black, contentColor = Color.White) { Icon(Icons.Default.MyLocation, "Locate Me") }
            }
        }
    }
}

// ==========================================
// GOOGLE MAPS VIEWPORT
// ==========================================
@Composable
fun GoogleViewport(
    modifier: Modifier = Modifier,
    isNightPanel: Boolean = false,
    is3dModeExternal: Boolean = false,
    searchEngine: String,
    currentLocation: Location?,
    instruction: NavInstruction?,
    routeGeoJson: String?,
    onRouteGeoJsonUpdated: (String?) -> Unit,
    onInstructionUpdated: (NavInstruction?) -> Unit,
    onRouteDurationUpdated: (Int?) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val googleApiKey = remember { try { val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA); ai.metaData.getString("com.google.android.geo.API_KEY") ?: "" } catch (e: Exception) { "" } }
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(50.0755, 14.4378), 13f) }

    LaunchedEffect(instruction) {
        if (instruction == null) {
            onRouteGeoJsonUpdated(null)
        }
    }

    LaunchedEffect(currentLocation, is3dModeExternal) {
        if (is3dModeExternal && currentLocation != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(currentLocation.latitude, currentLocation.longitude))
                        .zoom(16f)
                        .tilt(60f)
                        .bearing(currentLocation.bearing)
                        .build()
                ), 1500
            )
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            delay(300)
            if (searchEngine == "GOOGLE") NetworkManager.fetchGoogleSuggestions(searchQuery, googleApiKey) { results -> suggestions = results }
            else NetworkManager.fetchSuggestions(searchQuery, context) { results -> suggestions = results }
        } else suggestions = emptyList()
    }

    fun performSearchAndRoute(destinationPoint: Point) {
        focusManager.clearFocus()
        suggestions = emptyList()
        searchQuery = ""

        scope.launch {
            val tTilt = if (is3dModeExternal) 60f else 0f
            val tZoom = if (is3dModeExternal) 16f else 13f
            val tBear = if (is3dModeExternal && currentLocation != null) currentLocation.bearing else 0f
            val targetLatLng = if (currentLocation != null) LatLng(currentLocation.latitude, currentLocation.longitude) else cameraPositionState.position.target

            cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(targetLatLng).zoom(tZoom).tilt(tTilt).bearing(tBear).build()
            ), 1000)
        }

        val currentPoint = if (currentLocation != null) Point.fromLngLat(currentLocation.longitude, currentLocation.latitude) else Point.fromLngLat(cameraPositionState.position.target.longitude, cameraPositionState.position.target.latitude)

        if (searchEngine == "GOOGLE") {
            NetworkManager.fetchGoogleRoute(currentPoint, destinationPoint, googleApiKey) { geo, instr, dur ->
                scope.launch(Dispatchers.Main) { onRouteGeoJsonUpdated(geo); onInstructionUpdated(instr); onRouteDurationUpdated(dur) }
            }
        } else {
            NetworkManager.fetchRouteManual(currentPoint, destinationPoint, context) { geo, instr, dur ->
                scope.launch(Dispatchers.Main) { onRouteGeoJsonUpdated(geo); onInstructionUpdated(instr); onRouteDurationUpdated(dur) }
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth().background(Color.DarkGray)) {
        GoogleMapDisplay(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraPositionState, routeGeoJson = routeGeoJson, is3dMode = is3dModeExternal)
        val uiAlpha by animateFloatAsState(targetValue = if (isNightPanel) 0f else 1f, label = "UI Fade")
        if (uiAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().alpha(uiAlpha)) {
                Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).fillMaxWidth(0.6f)) {
                    TeslaSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, onSearch = { if (suggestions.isNotEmpty()) performSearchAndRoute(suggestions.first().point) })
                    if (suggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).background(Color.Black.copy(alpha = 0.95f), shape = RoundedCornerShape(16.dp))) {
                            items(suggestions) { place ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { performSearchAndRoute(place.point) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, tint = Color.Cyan, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(12.dp)); Text(place.name, color = Color.White, fontSize = 16.sp)
                                }
                                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                            }
                        }
                    }
                }
                FloatingActionButton(onClick = {
                    scope.launch {
                        val tTilt = if (is3dModeExternal) 60f else 0f
                        val tZoom = if (is3dModeExternal) 16f else 13f
                        val tBear = if (is3dModeExternal && currentLocation != null) currentLocation.bearing else 0f
                        if (currentLocation != null) cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(currentLocation.latitude, currentLocation.longitude)).zoom(tZoom).tilt(tTilt).bearing(tBear).build()), 1000)
                        else cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(cameraPositionState.position.target).zoom(tZoom).tilt(tTilt).bearing(tBear).build()), 1000)
                    }
                }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), containerColor = Color.Black, contentColor = Color.White) { Icon(Icons.Default.MyLocation, "Locate Me") }
            }
        }
    }
}

@Composable
fun TeslaMap(modifier: Modifier = Modifier, mapViewportState: com.mapbox.maps.extension.compose.animation.viewport.MapViewportState, routeGeoJson: String? = null, is3dMode: Boolean = false) {
    val context = LocalContext.current
    var locationPermissionGranted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) locationPermissionGranted = true }
    MapboxMap(modifier = Modifier.fillMaxSize(), mapViewportState = mapViewportState) {
        MapEffect(locationPermissionGranted) { mapView ->
            mapView.mapboxMap.loadStyleUri(Style.TRAFFIC_NIGHT) { style ->
                val sourceId = "route-source"; val layerId = "route-layer"
                if (!style.styleSourceExists(sourceId)) style.addSource(geoJsonSource(sourceId) { data("") })
                if (!style.styleLayerExists(layerId)) {
                    style.addLayer(lineLayer(layerId, sourceId) {
                        lineColor("#00B0FF")
                        lineWidth(8.0)
                        lineOpacity(0.65)
                        lineCap(LineCap.ROUND)
                        lineJoin(LineJoin.ROUND)
                    })
                }
                if (!style.styleLayerExists("3d-buildings")) { val bLayer = FillExtrusionLayer("3d-buildings", "composite"); bLayer.sourceLayer("building"); bLayer.filter(eq(get("extrude"), literal("true"))); bLayer.minZoom(15.0); bLayer.fillExtrusionColor(Color.DarkGray.toArgb()); bLayer.fillExtrusionOpacity(0.8); bLayer.fillExtrusionHeight(get("height")); bLayer.fillExtrusionBase(get("min_height")); style.addLayer(bLayer) }
            }
            if (locationPermissionGranted) {
                mapView.location.updateSettings {
                    enabled = true
                    pulsingEnabled = true
                    puckBearingEnabled = true
                    puckBearing = PuckBearing.COURSE
                }
            }
        }
        MapEffect(routeGeoJson) { mapView -> if (routeGeoJson != null) mapView.mapboxMap.getStyle { style -> if (style.styleSourceExists("route-source")) (style.getSource("route-source") as? GeoJsonSource)?.data(routeGeoJson) } else mapView.mapboxMap.getStyle { style -> if (style.styleSourceExists("route-source")) (style.getSource("route-source") as? GeoJsonSource)?.data("") } }
    }
}

@Composable
fun GoogleMapDisplay(modifier: Modifier = Modifier, cameraPositionState: com.google.maps.android.compose.CameraPositionState, routeGeoJson: String? = null, is3dMode: Boolean = false) {
    val uiSettings = remember { MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false) }
    val mapProperties = remember(is3dMode) { MapProperties(isMyLocationEnabled = true, isTrafficEnabled = true, maxZoomPreference = 20f, minZoomPreference = 5f) }
    val routePoints = remember(routeGeoJson) {
        val points = mutableListOf<LatLng>()
        if (routeGeoJson != null) {
            try {
                val lineString = com.mapbox.geojson.FeatureCollection.fromJson(routeGeoJson).features()?.firstOrNull()?.geometry() as? com.mapbox.geojson.LineString
                lineString?.coordinates()?.forEach { points.add(LatLng(it.latitude(), it.longitude())) }
            } catch (e: Exception) { e.printStackTrace() }
        }
        points
    }
    GoogleMap(modifier = modifier, cameraPositionState = cameraPositionState, properties = mapProperties, uiSettings = uiSettings) {
        if (routePoints.isNotEmpty()) Polyline(points = routePoints, color = Color(0xAA00B0FF), width = 24f, geodesic = true)
    }
}

fun isGmsAvailable(context: Context): Boolean {
    val availability = GoogleApiAvailability.getInstance()
    return availability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}

fun getBatteryLevel(context: Context): Int {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
}