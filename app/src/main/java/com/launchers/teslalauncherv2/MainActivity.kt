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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

// IMPORTY TVÝCH BALÍČKŮ
import com.launchers.teslalauncherv2.GUI.Dock
import com.launchers.teslalauncherv2.GUI.GearSelector
import com.launchers.teslalauncherv2.GUI.InstrumentCluster
import com.launchers.teslalauncherv2.GUI.NightPanelScreen
import com.launchers.teslalauncherv2.GUI.ReverseCameraScreen
import com.launchers.teslalauncherv2.data.CarState
import com.launchers.teslalauncherv2.data.NavInstruction
import com.launchers.teslalauncherv2.data.ObdDataManager
import com.launchers.teslalauncherv2.data.SearchSuggestion
import com.launchers.teslalauncherv2.data.fetchRouteManual
import com.launchers.teslalauncherv2.data.fetchSuggestions

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
        // DŮLEŽITÉ: Zabijeme proces pro čistý restart (uvolní Bluetooth socket)
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

    var currentInstruction by remember { mutableStateOf<NavInstruction?>(null) }
    var currentSpeedGps by remember { mutableIntStateOf(0) }
    var batteryLevel by remember { mutableIntStateOf(getBatteryLevel(context)) }
    var isNightPanel by rememberSaveable { mutableStateOf(false) }
    var currentGear by rememberSaveable { mutableStateOf("P") }
    var gpsStatus by remember { mutableStateOf<String?>("INIT...") }

    // OPRÁVNĚNÍ
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) gpsStatus = "STARTING..." else gpsStatus = "NO PERMISSION"
        }
    )
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> if (isGranted) currentGear = "R" }
    )
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { ObdDataManager.connect(context, OBD_MAC_ADDRESS) }
    )

    fun tryShiftTo(gear: String) {
        if (gear == "R") {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) currentGear = "R"
            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else currentGear = gear
    }

    // START
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 31) {
            bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            ObdDataManager.connect(context, OBD_MAC_ADDRESS)
        }
        while (true) { batteryLevel = getBatteryLevel(context); delay(60000) }
    }

    // GPS
    LaunchedEffect(gpsStatus) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            gpsStatus = "WAITING..."
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
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
                    override fun onProviderDisabled(p: String) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                }
                if (locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)
                if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener)
            } catch (e: Exception) { gpsStatus = "GPS ERR"; e.printStackTrace() }
        }
    }

    val finalSpeed = if (carStateFlow.collectAsState().value.isConnected) carStateFlow.collectAsState().value.speed else currentSpeedGps

    // --- LAYOUT ---
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. HLAVNÍ VRSTVA (UI)
        Column(modifier = Modifier.fillMaxSize()) {

            // A. Budíky (Izolované)
            InstrumentClusterWrapper(
                modifier = Modifier.weight(0.25f),
                carStateFlow = carStateFlow,
                gpsStatus = gpsStatus,
                batteryLevel = batteryLevel,
                instruction = currentInstruction,
                gpsSpeed = currentSpeedGps
            )

            // B. Střední část (MAPA + KAMERA)
            Box(modifier = Modifier.weight(0.60f).fillMaxWidth().background(Color.Black)) {
                // 1. MAPA (Je zde TRVALE, aby se neresetovala)
                Viewport(
                    modifier = Modifier.fillMaxSize(),
                    isNightPanel = isNightPanel,
                    onInstructionUpdated = { newInstruction -> currentInstruction = newInstruction }
                )

                // 2. KAMERA (Překryv) - Zobrazí se PŘES mapu
                if (currentGear == "R") {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        ReverseCameraScreen()
                        Text(
                            text = "REAR VIEW",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp)
                        )
                    }
                }

                // 3. Volič převodovky
                GearSelector(
                    currentGear = currentGear,
                    onGearSelected = { gear -> tryShiftTo(gear) },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            // C. Dock
            Dock(
                modifier = Modifier.weight(0.15f),
                isNightPanel = isNightPanel,
                onNightPanelToggle = { isNightPanel = !isNightPanel }
            )
        }

        // 2. NIGHT PANEL PŘEKRYV
        if (isNightPanel) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                NightPanelScreen(
                    speed = finalSpeed,
                    instruction = currentInstruction,
                    onExit = { isNightPanel = false }
                )
            }
        }
    }
}

// --- POMOCNÉ FUNKCE (Wrapper, Viewport, Map...) ---

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

    InstrumentCluster(
        modifier = modifier,
        carState = carState.copy(speed = displaySpeed),
        gpsStatus = gpsStatus,
        batteryLevel = batteryLevel,
        instruction = instruction
    )
}

@Composable
fun Viewport(modifier: Modifier = Modifier, isNightPanel: Boolean = false, onInstructionUpdated: (NavInstruction?) -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val mapViewportState = rememberMapViewportState { setCameraOptions { center(Point.fromLngLat(14.4378, 50.0755)); zoom(11.0); pitch(0.0) } }
    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var routeGeoJson by rememberSaveable { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    var is3dMode by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(is3dMode) {
        val currentCamera = mapViewportState.cameraState
        val targetPitch = if (is3dMode) 60.0 else 0.0
        val targetZoom = if (is3dMode && (currentCamera?.zoom ?: 0.0) < 15.0) 16.0 else (currentCamera?.zoom ?: 11.0)
        mapViewportState.flyTo(CameraOptions.Builder().center(currentCamera?.center).zoom(targetZoom).pitch(targetPitch).bearing(currentCamera?.bearing).build(), MapAnimationOptions.Builder().duration(1500).build())
    }
    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) { delay(500); fetchSuggestions(searchQuery, context) { results -> suggestions = results } } else suggestions = emptyList()
    }
    fun performSearchAndRoute(query: String) {
        focusManager.clearFocus()
        if (suggestions.isNotEmpty() || query.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val targetPoint = if (suggestions.isNotEmpty()) suggestions.first().point else {
                    var foundPoint: Point? = null
                    val latch = java.util.concurrent.CountDownLatch(1)
                    fetchSuggestions(query, context) { results -> if (results.isNotEmpty()) foundPoint = results.first().point; latch.countDown() }
                    latch.await()
                    foundPoint
                }
                if (targetPoint != null) {
                    val currentPoint = mapViewportState.cameraState?.center ?: Point.fromLngLat(14.4378, 50.0755)
                    fetchRouteManual(currentPoint, targetPoint, context) { newGeoJson, instruction ->
                        scope.launch(Dispatchers.Main) { routeGeoJson = newGeoJson; onInstructionUpdated(instruction); isSearchVisible = false; is3dMode = true; Toast.makeText(context, "Výpočet trasy...", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        }
    }
    Box(modifier = modifier.fillMaxWidth().background(Color.DarkGray)) {
        TeslaMap(modifier = Modifier.fillMaxSize(), mapViewportState = mapViewportState, routeGeoJson = routeGeoJson, is3dMode = is3dMode)
        val uiAlpha by animateFloatAsState(targetValue = if (isNightPanel) 0f else 1f)
        if (uiAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().alpha(uiAlpha)) {
                FloatingActionButton(onClick = { is3dMode = !is3dMode }, modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 80.dp), containerColor = if (is3dMode) Color.Cyan else Color.Black, contentColor = if (is3dMode) Color.Black else Color.White) { Text(text = if (is3dMode) "3D" else "2D", fontWeight = FontWeight.Bold) }
                if (isSearchVisible) {
                    Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).fillMaxWidth(0.6f)) {
                        TextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Where to go?", color = Color.Gray) }, singleLine = true, modifier = Modifier.fillMaxWidth().background(Color.Black, shape = RoundedCornerShape(8.dp)), colors = TextFieldDefaults.colors(focusedContainerColor = Color.Black, unfocusedContainerColor = Color.Black, focusedTextColor = Color.White, unfocusedTextColor = Color.White), keyboardActions = KeyboardActions(onDone = { performSearchAndRoute(searchQuery) }), keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done), trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = ""; suggestions = emptyList() }) { Icon(Icons.Default.Close, "Clear", tint = Color.Gray) } })
                        if (suggestions.isNotEmpty()) {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).background(Color.Black.copy(alpha = 0.9f), shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))) { items(suggestions) { place -> Row(modifier = Modifier.fillMaxWidth().clickable { searchQuery = place.name; performSearchAndRoute(place.name) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, tint = Color.Cyan, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(12.dp)); Text(place.name, color = Color.White, fontSize = 14.sp) }; HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp) } }
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
fun fetchRouteFromPoints(origin: Point, destination: Point, context: Context, onRouteFound: (String) -> Unit) {
    fetchRouteManual(origin, destination, context) { json, _ -> onRouteFound(json) }
}