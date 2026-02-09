package com.launchers.teslalauncherv2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
// --- TENTO IMPORT CHYBĚL ---
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
import kotlin.random.Random

// Mapbox Imports
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
}

@Composable
fun TeslaLauncherTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(background = Color.Black, surface = Color.Black, onBackground = Color.White, onSurface = Color.White)
    MaterialTheme(colorScheme = darkColorScheme, content = content)
}

@Composable
fun TeslaLayout() {
    val context = LocalContext.current
    var currentInstruction by remember { mutableStateOf<NavInstruction?>(null) }
    var currentSpeed by remember { mutableIntStateOf(0) }
    var batteryLevel by remember { mutableIntStateOf(getBatteryLevel(context)) }
    var isDemoMode by remember { mutableStateOf(false) }
    var isNightPanel by rememberSaveable { mutableStateOf(false) }
    var currentGear by rememberSaveable { mutableStateOf("P") }

    // --- OPRAVA PÁDU: Permission Launcher ---
    // Tento objekt slouží k vyžádání povolení
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                // Povoleno! Můžeme přeřadit na R
                currentGear = "R"
                Toast.makeText(context, "Camera permission granted. Select R again.", Toast.LENGTH_SHORT).show()
            } else {
                // Zamítnuto
                Toast.makeText(context, "Camera permission needed for Reverse", Toast.LENGTH_LONG).show()
            }
        }
    )

    // Funkce pro bezpečné přeřazení
    fun tryShiftTo(gear: String) {
        if (gear == "R") {
            // Kontrola povolení PŘED přepnutím
            val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                currentGear = "R"
            } else {
                // Nemáme povolení -> Vyžádat
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            // P, N, D je bezpečné
            currentGear = gear
        }
    }

    LaunchedEffect(Unit) {
        while (true) { batteryLevel = getBatteryLevel(context); delay(60000) }
    }

    // Logika dema (pokud chcete)
    LaunchedEffect(currentGear) {
        if (currentGear == "P") currentSpeed = 0
    }

    if (isNightPanel) {
        NightPanelScreen(
            speed = currentSpeed,
            instruction = currentInstruction,
            onExit = { isNightPanel = false }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            InstrumentCluster(
                modifier = Modifier.weight(0.25f),
                instruction = currentInstruction,
                speed = currentSpeed,
                battery = batteryLevel,
                isDemoMode = isDemoMode,
                onDemoToggle = { isDemoMode = !isDemoMode }
            )

            // --- HLAVNÍ PLOCHA ---
            Box(modifier = Modifier.weight(0.60f).fillMaxWidth().background(Color.Black)) {

                // 1. OBSAH (Mapa nebo Kamera)
                if (currentGear == "R") {
                    ReverseCameraScreen()
                    // Overlay nápis
                    Text(
                        "REAR VIEW", color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp)
                    )
                } else {
                    Viewport(
                        modifier = Modifier.fillMaxSize(),
                        isNightPanel = false,
                        onInstructionUpdated = { newInstruction -> currentInstruction = newInstruction }
                    )
                }

                // 2. ŘADIČKA (NOVĚ VPRAVO)
                GearSelector(
                    currentGear = currentGear,
                    onGearSelected = { gear -> tryShiftTo(gear) }, // Voláme bezpečnou funkci
                    modifier = Modifier
                        .align(Alignment.CenterEnd) // Zarovnáno DOPRAVA
                    // Žádný padding, chceme to přilepené ke kraji, vypadá to moderněji
                )
            }

            Dock(
                modifier = Modifier.weight(0.15f),
                isNightPanel = false,
                onNightPanelToggle = { isNightPanel = !isNightPanel }
            )
        }
    }
}

@Composable
fun Viewport(
    modifier: Modifier = Modifier,
    isNightPanel: Boolean = false,
    onInstructionUpdated: (NavInstruction?) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(14.4378, 50.0755))
            zoom(11.0)
            pitch(0.0)
        }
    }

    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Ukládáme trasu, aby přežila přepnutí do Night Panelu
    var routeGeoJson by rememberSaveable { mutableStateOf<String?>(null) }

    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }

    // Ukládáme 3D mód, aby přežil přepnutí
    var is3dMode by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Animace kamery pro 3D
    LaunchedEffect(is3dMode) {
        val currentCamera = mapViewportState.cameraState
        val targetPitch = if (is3dMode) 60.0 else 0.0
        val targetZoom = if (is3dMode && (currentCamera?.zoom ?: 0.0) < 15.0) 16.0 else (currentCamera?.zoom ?: 11.0)

        mapViewportState.flyTo(
            CameraOptions.Builder()
                .center(currentCamera?.center) // Zachovat střed
                .zoom(targetZoom)
                .pitch(targetPitch)
                .bearing(currentCamera?.bearing)
                .build(),
            MapAnimationOptions.Builder().duration(1500).build()
        )
    }

    // Našeptávač
    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            delay(500)
            fetchSuggestions(searchQuery, context) { results -> suggestions = results }
        } else {
            suggestions = emptyList()
        }
    }

    // Funkce pro hledání
    fun performSearchAndRoute(query: String) {
        focusManager.clearFocus()
        if (suggestions.isNotEmpty() || query.isNotEmpty()) {
            val queryText = if (suggestions.isNotEmpty()) suggestions.first().name else query

            // Zkusíme najít souřadnice (buď z návrhu nebo prvního výsledku)
            val searchJob = scope.launch(Dispatchers.IO) {
                val targetPoint = if (suggestions.isNotEmpty()) {
                    suggestions.first().point
                } else {
                    // Pokud uživatel jen napsal text a dal enter, musíme to geokódovat
                    var foundPoint: Point? = null
                    val latch = java.util.concurrent.CountDownLatch(1)
                    fetchSuggestions(query, context) { results ->
                        if (results.isNotEmpty()) foundPoint = results.first().point
                        latch.countDown()
                    }
                    latch.await()
                    foundPoint
                }

                if (targetPoint != null) {
                    val currentPoint = mapViewportState.cameraState?.center ?: Point.fromLngLat(14.4378, 50.0755)
                    fetchRouteManual(currentPoint, targetPoint, context) { newGeoJson, instruction ->
                        scope.launch(Dispatchers.Main) {
                            routeGeoJson = newGeoJson // Uložíme do rememberSaveable
                            onInstructionUpdated(instruction)
                            isSearchVisible = false
                            is3dMode = true
                            Toast.makeText(context, "Calculating route...", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth().background(Color.DarkGray)) {
        // Mapa se při návratu z Night Panelu překreslí, ale routeGeoJson se načte ze stavu
        TeslaMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            routeGeoJson = routeGeoJson,
            is3dMode = is3dMode
        )

        // UI Prvky (Stejné jako předtím)
        val uiAlpha by animateFloatAsState(targetValue = if (isNightPanel) 0f else 1f)
        if (uiAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().alpha(uiAlpha)) {

                FloatingActionButton(
                    onClick = { is3dMode = !is3dMode },
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 80.dp),
                    containerColor = if (is3dMode) Color.Cyan else Color.Black,
                    contentColor = if (is3dMode) Color.Black else Color.White
                ) { Text(text = if (is3dMode) "3D" else "2D", fontWeight = FontWeight.Bold) }

                if (isSearchVisible) {
                    Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).fillMaxWidth(0.6f)) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Where to go?", color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().background(Color.Black, shape = RoundedCornerShape(8.dp)),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Black, unfocusedContainerColor = Color.Black, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            keyboardActions = KeyboardActions(onDone = { performSearchAndRoute(searchQuery) }),
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                            trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = ""; suggestions = emptyList() }) { Icon(Icons.Default.Close, "Clear", tint = Color.Gray) } }
                        )

                        if (suggestions.isNotEmpty()) {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).background(Color.Black.copy(alpha = 0.9f), shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))) {
                                items(suggestions) { place ->
                                    Row(modifier = Modifier.fillMaxWidth().clickable {
                                        searchQuery = place.name
                                        performSearchAndRoute(place.name) // Použijeme společnou logiku
                                    }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, null, tint = Color.Cyan, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(12.dp)); Text(place.name, color = Color.White, fontSize = 14.sp)
                                    }
                                    HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }

                Button(onClick = { isSearchVisible = !isSearchVisible; if (!isSearchVisible) { suggestions = emptyList(); searchQuery = "" } }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))) {
                    Icon(Icons.Default.Search, "Search", tint = Color.White); Spacer(modifier = Modifier.size(8.dp)); Text(if (isSearchVisible) "CLOSE" else "SEARCH", color = Color.White)
                }

                FloatingActionButton(onClick = { mapViewportState.transitionToFollowPuckState(FollowPuckViewportStateOptions.Builder().bearing(FollowPuckViewportStateBearing.Constant(0.0)).pitch(0.0).zoom(14.0).build()) }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), containerColor = Color.Black, contentColor = Color.White) {
                    Icon(Icons.Default.MyLocation, "Locate Me")
                }
            }
        }
    }
}

@Composable
fun TeslaMap(modifier: Modifier = Modifier, mapViewportState: com.mapbox.maps.extension.compose.animation.viewport.MapViewportState, routeGeoJson: String? = null, is3dMode: Boolean = false) {
    val context = LocalContext.current
    var locationPermissionGranted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) locationPermissionGranted = true }
    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { permissions -> locationPermissionGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) || permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) }
    LaunchedEffect(Unit) { if (!locationPermissionGranted) permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

    MapboxMap(modifier = Modifier.fillMaxSize(), mapViewportState = mapViewportState) {
        MapEffect(locationPermissionGranted) { mapView ->
            mapView.mapboxMap.loadStyleUri(Style.DARK) { style ->
                val sourceId = "route-source"; val layerId = "route-layer"
                if (!style.styleSourceExists(sourceId)) style.addSource(geoJsonSource(sourceId) { data("") })
                if (!style.styleLayerExists(layerId)) style.addLayer(lineLayer(layerId, sourceId) { lineColor("#00B0FF"); lineWidth(6.0); lineCap(LineCap.ROUND); lineJoin(LineJoin.ROUND) })
                if (!style.styleLayerExists("3d-buildings")) {
                    val buildingLayer = FillExtrusionLayer("3d-buildings", "composite")
                    buildingLayer.sourceLayer("building")
                    buildingLayer.filter(eq(get("extrude"), literal("true")))
                    buildingLayer.minZoom(15.0)
                    buildingLayer.fillExtrusionColor(Color.DarkGray.toArgb())
                    buildingLayer.fillExtrusionOpacity(0.8)
                    buildingLayer.fillExtrusionHeight(get("height"))
                    buildingLayer.fillExtrusionBase(get("min_height"))
                    style.addLayer(buildingLayer)
                }
            }
            if (locationPermissionGranted) mapView.location.updateSettings { enabled = true; pulsingEnabled = true }
        }
        MapEffect(routeGeoJson) { mapView -> if (routeGeoJson != null) mapView.mapboxMap.getStyle { style -> if (style.styleSourceExists("route-source")) (style.getSource("route-source") as? GeoJsonSource)?.data(routeGeoJson) } }
    }
}

fun getBatteryLevel(context: Context): Int {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
}

// Pro zpětnou kompatibilitu
fun fetchRouteFromPoints(origin: Point, destination: Point, context: Context, onRouteFound: (String) -> Unit) {
    fetchRouteManual(origin, destination, context) { json, _ -> onRouteFound(json) }
}

@Preview(showBackground = true, device = "spec:width=1080px,height=2340px,dpi=440")
@Composable
fun TeslaLayoutPreview() { TeslaLauncherTheme { TeslaLayout() } }