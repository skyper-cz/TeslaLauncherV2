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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

// IMPORTY PROJEKTU
import com.launchers.teslalauncherv2.GUI.Dock
import com.launchers.teslalauncherv2.GUI.GearSelector
import com.launchers.teslalauncherv2.GUI.InstrumentCluster
import com.launchers.teslalauncherv2.GUI.NightPanelScreen
import com.launchers.teslalauncherv2.GUI.ReverseCameraScreen
import com.launchers.teslalauncherv2.data.CarState
import com.launchers.teslalauncherv2.data.NavInstruction
import com.launchers.teslalauncherv2.data.ObdDataManager
import com.launchers.teslalauncherv2.data.SearchSuggestion
import com.launchers.teslalauncherv2.data.NetworkManager

// MAPBOX IMPORTY
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
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
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.eq
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.get
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.literal

const val OBD_MAC_ADDRESS = "00:10:CC:4F:36:03"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TeslaLauncherTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TeslaLayout()
                }
            }
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
    val darkColorScheme = darkColorScheme(
        background = Color.Black,
        surface = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White
    )
    MaterialTheme(colorScheme = darkColorScheme, content = content)
}

@Composable
fun TeslaLayout() {
    val context = LocalContext.current

    // Data z OBD
    val carStateFlow = ObdDataManager.carState
    val carStateSnapshot by carStateFlow.collectAsState()

    // Stavy
    var currentInstruction by remember { mutableStateOf<NavInstruction?>(null) }
    var currentSpeedGps by remember { mutableIntStateOf(0) }
    var batteryLevel by remember { mutableIntStateOf(getBatteryLevel(context)) }
    var isNightPanel by rememberSaveable { mutableStateOf(false) }

    // Řazení a mapa
    var currentGear by rememberSaveable { mutableStateOf("P") }
    var lastManualShiftTime by remember { mutableLongStateOf(0L) }
    var is3dMapMode by remember { mutableStateOf(false) }
    var gpsStatus by remember { mutableStateOf<String?>("INIT") }
    var isLocationPermissionGranted by remember { mutableStateOf(false) }

    // --- LOGIKA AUTOMATICKÉHO ŘAZENÍ (FIXED) ---

    // Stav pro zámek automatiky
    var isManualOverrideActive by remember { mutableStateOf(false) }

    // Časovač: Sleduje 8s interval po manuálním zásahu
    LaunchedEffect(lastManualShiftTime) {
        if (lastManualShiftTime > 0) {
            isManualOverrideActive = true
            delay(8000)
            isManualOverrideActive = false
        }
    }

    // Výkonný blok: Řadí podle rychlosti a náklonu mapy
    LaunchedEffect(carStateSnapshot.speed, currentGear, isManualOverrideActive) {
        // Mapa reaguje vždy
        if (currentGear == "D") is3dMapMode = true
        else if (currentGear == "P") is3dMapMode = false

        // Automatika řadí jen když není zámek
        if (!isManualOverrideActive) {
            if (carStateSnapshot.speed > 5 && currentGear != "D" && currentGear != "R") {
                currentGear = "D"
            }
            if (carStateSnapshot.speed < 4 && currentGear != "P") {
                currentGear = "P"
            }
        }
    }

    fun manualShiftTo(gear: String) {
        lastManualShiftTime = System.currentTimeMillis()
        if (gear == "R") {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) currentGear = "R"
        } else {
            currentGear = gear
        }
    }

    // Oprávnění
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                isLocationPermissionGranted = true
                gpsStatus = "READY"
            } else {
                gpsStatus = "NO PERM"
            }
        }
    )
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { ObdDataManager.connect(context, OBD_MAC_ADDRESS) }
    )

    // Start
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            isLocationPermissionGranted = true
        } else {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        if (Build.VERSION.SDK_INT >= 31) bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        else ObdDataManager.connect(context, OBD_MAC_ADDRESS)

        while (true) { batteryLevel = getBatteryLevel(context); delay(60000) }
    }

    // GPS Logika
    LaunchedEffect(isLocationPermissionGranted) {
        if (isLocationPermissionGranted) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                gpsStatus = "SEARCHING..."
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        gpsStatus = null
                        if (location.hasSpeed()) currentSpeedGps = (location.speed * 3.6f).toInt()
                        val instruction = currentInstruction
                        if (instruction?.maneuverPoint != null) {
                            val target = Location("T"); target.latitude = instruction.maneuverPoint.latitude(); target.longitude = instruction.maneuverPoint.longitude()
                            if (location.distanceTo(target) < 30) currentInstruction = null
                            else currentInstruction = instruction.copy(distance = location.distanceTo(target).toInt())
                        }
                    }
                    override fun onProviderDisabled(p: String) { gpsStatus = "GPS OFF" }
                    override fun onProviderEnabled(p: String) { gpsStatus = "SEARCHING..." }
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                }
                if (locationManager.allProviders.contains(LocationManager.GPS_PROVIDER))
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)
                if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER))
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener)
            } catch (e: Exception) {
                gpsStatus = "ERR"
                e.printStackTrace()
            }
        }
    }

    val finalSpeed = if (carStateSnapshot.isConnected) carStateSnapshot.speed else currentSpeedGps

    // UI LAYOUT
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            InstrumentClusterWrapper(
                modifier = Modifier.weight(0.25f),
                carStateFlow = carStateFlow,
                gpsStatus = gpsStatus,
                batteryLevel = batteryLevel,
                instruction = currentInstruction,
                gpsSpeed = currentSpeedGps
            )

            Box(modifier = Modifier.weight(0.60f).fillMaxWidth().background(Color.Black)) {
                Viewport(
                    modifier = Modifier.fillMaxSize(),
                    isNightPanel = isNightPanel,
                    is3dModeExternal = is3dMapMode,
                    onInstructionUpdated = { newInstruction -> currentInstruction = newInstruction }
                )

                if (currentGear == "R") {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        ReverseCameraScreen()
                        Text("REAR VIEW", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp))
                    }
                }

                GearSelector(
                    currentGear = currentGear,
                    onGearSelected = { gear -> manualShiftTo(gear) },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            Dock(
                modifier = Modifier.weight(0.15f),
                isNightPanel = isNightPanel,
                onNightPanelToggle = { isNightPanel = !isNightPanel }
            )
        }

        if (isNightPanel) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                NightPanelScreen(
                    speed = finalSpeed,
                    rpm = carStateSnapshot.rpm,
                    error = carStateSnapshot.error,
                    instruction = currentInstruction,
                    onExit = { isNightPanel = false }
                )
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
    gpsSpeed: Int
) {
    val carState by carStateFlow.collectAsState()
    val displaySpeed = if (carState.isConnected) carState.speed else gpsSpeed
    InstrumentCluster(modifier = modifier, carState = carState.copy(speed = displaySpeed), gpsStatus = gpsStatus, batteryLevel = batteryLevel, instruction = instruction)
}

@Composable
fun Viewport(
    modifier: Modifier = Modifier,
    isNightPanel: Boolean = false,
    is3dModeExternal: Boolean = false,
    onInstructionUpdated: (NavInstruction?) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val mapViewportState = rememberMapViewportState { setCameraOptions { center(Point.fromLngLat(14.4378, 50.0755)); zoom(11.0); pitch(0.0) } }

    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var routeGeoJson by rememberSaveable { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(is3dModeExternal) {
        val targetPitch = if (is3dModeExternal) 60.0 else 0.0
        val targetZoom = if (is3dModeExternal) 16.0 else 13.0
        mapViewportState.flyTo(CameraOptions.Builder().zoom(targetZoom).pitch(targetPitch).build(), MapAnimationOptions.Builder().duration(1500).build())
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            delay(300)
            NetworkManager.fetchSuggestions(searchQuery, context) { results -> suggestions = results }
        } else { suggestions = emptyList() }
    }

    fun performSearchAndRoute(destinationPoint: Point) {
        focusManager.clearFocus()
        isSearchVisible = false
        suggestions = emptyList()
        searchQuery = ""
        val currentPoint = mapViewportState.cameraState?.center ?: Point.fromLngLat(14.4378, 50.0755)
        NetworkManager.fetchRouteManual(currentPoint, destinationPoint, context) { newGeoJson, instruction ->
            scope.launch(Dispatchers.Main) {
                routeGeoJson = newGeoJson
                onInstructionUpdated(instruction)
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth().background(Color.DarkGray)) {
        TeslaMap(modifier = Modifier.fillMaxSize(), mapViewportState = mapViewportState, routeGeoJson = routeGeoJson, is3dMode = is3dModeExternal)

        val uiAlpha by animateFloatAsState(targetValue = if (isNightPanel) 0f else 1f)
        if (uiAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().alpha(uiAlpha)) {
                if (isSearchVisible) {
                    Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).fillMaxWidth(0.6f)) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Where to go?", color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().background(Color.Black, shape = RoundedCornerShape(8.dp)),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Black, unfocusedContainerColor = Color.Black, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.Cyan),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                            trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = ""; suggestions = emptyList() }) { Icon(Icons.Default.Close, "Clear", tint = Color.Gray) } }
                        )
                        if (suggestions.isNotEmpty()) {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).background(Color.Black.copy(alpha = 0.95f), shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))) {
                                items(suggestions) { place ->
                                    Row(modifier = Modifier.fillMaxWidth().clickable { performSearchAndRoute(place.point) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, null, tint = Color.Cyan, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(12.dp)); Text(place.name, color = Color.White, fontSize = 16.sp)
                                    }
                                    HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
                Button(onClick = { isSearchVisible = !isSearchVisible; if (!isSearchVisible) { suggestions = emptyList(); searchQuery = "" } }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))) { Icon(Icons.Default.Search, "Search", tint = Color.White); Spacer(modifier = Modifier.size(8.dp)); Text(if (isSearchVisible) "CLOSE" else "SEARCH", color = Color.White) }
                FloatingActionButton(onClick = { mapViewportState.transitionToFollowPuckState(FollowPuckViewportStateOptions.Builder().bearing(FollowPuckViewportStateBearing.Constant(0.0)).pitch(0.0).zoom(14.0).build()) }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), containerColor = Color.Black, contentColor = Color.White) { Icon(Icons.Default.MyLocation, "Locate Me") }
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
            mapView.mapboxMap.loadStyleUri(Style.DARK) { style ->
                val sourceId = "route-source"; val layerId = "route-layer"
                if (!style.styleSourceExists(sourceId)) style.addSource(geoJsonSource(sourceId) { data("") })
                if (!style.styleLayerExists(layerId)) style.addLayer(lineLayer(layerId, sourceId) { lineColor("#00B0FF"); lineWidth(6.0); lineCap(LineCap.ROUND); lineJoin(LineJoin.ROUND) })
                if (!style.styleLayerExists("3d-buildings")) { val buildingLayer = FillExtrusionLayer("3d-buildings", "composite"); buildingLayer.sourceLayer("building"); buildingLayer.filter(eq(get("extrude"), literal("true"))); buildingLayer.minZoom(15.0); buildingLayer.fillExtrusionColor(Color.DarkGray.toArgb()); buildingLayer.fillExtrusionOpacity(0.8); buildingLayer.fillExtrusionHeight(get("height")); buildingLayer.fillExtrusionBase(get("min_height")); style.addLayer(buildingLayer) }
            }
            if (locationPermissionGranted) mapView.location.updateSettings { enabled = true; pulsingEnabled = true }
        }
        MapEffect(routeGeoJson) { mapView -> if (routeGeoJson != null) mapView.mapboxMap.getStyle { style -> if (style.styleSourceExists("route-source")) (style.getSource("route-source") as? GeoJsonSource)?.data(routeGeoJson) } }
    }
}

fun getBatteryLevel(context: Context): Int { val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager; return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }