package com.launchers.teslalauncherv2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState // Pro animaci posunu
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
// --- SPRÁVNÉ IMPORTY PRO AUTO-MIRRORED IKONY ---
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment // Pro posouvání tachometru
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

// --- Mapbox Maps SDK ---
import com.mapbox.geojson.Point
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
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

// --- Expressions ---
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.eq
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.get
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.literal

// --- Datové třídy ---
data class SearchSuggestion(val name: String, val point: Point)

data class NavInstruction(
    val text: String,
    val distance: Int,
    val modifier: String?
)

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
    // STATE HOISTING: Stav instrukce je zde
    var currentInstruction by remember { mutableStateOf<NavInstruction?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Horní panel (Instrument Cluster)
        InstrumentCluster(
            modifier = Modifier.weight(0.25f),
            instruction = currentInstruction
        )

        // Mapa (Viewport)
        Viewport(
            modifier = Modifier.weight(0.60f),
            onInstructionUpdated = { newInstruction ->
                currentInstruction = newInstruction
            }
        )

        // Spodní panel (Dock)
        Dock(modifier = Modifier.weight(0.15f))
    }
}

@Composable
fun InstrumentCluster(
    modifier: Modifier = Modifier,
    instruction: NavInstruction? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // --- 1. LEVÁ ČÁST (Navigace nebo Čas) ---
        Box(
            modifier = Modifier.align(Alignment.CenterStart),
            contentAlignment = Alignment.CenterStart
        ) {
            if (instruction != null) {
                // Mód Navigace: Šipka, Vzdálenost a Text ulice
                Column(horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when {
                            // Používáme AutoMirrored verze (správně pro nové SDK)
                            instruction.modifier?.contains("left") == true -> Icons.AutoMirrored.Filled.ArrowBack
                            instruction.modifier?.contains("right") == true -> Icons.AutoMirrored.Filled.ArrowForward
                            instruction.modifier?.contains("uturn") == true -> Icons.Default.Refresh
                            else -> Icons.Default.ArrowUpward
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.Cyan,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${instruction.distance} m",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    // Dolní řádek: Název ulice (pod šipkou)
                    Text(
                        text = instruction.text,
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        modifier = Modifier.widthIn(max = 200.dp) // Omezíme šířku
                    )
                }
            } else {
                // Standby Mód: Hodiny
                Text(
                    text = "12:00",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )
            }
        }

        // --- 2. STŘED (Tachometr) ---
        // Animace: Když je navigace, posuneme tachometr doprava (Bias 0.5), jinak střed (0.0)
        val alignmentBias by animateFloatAsState(
            targetValue = if (instruction != null) 0.5f else 0.0f,
            animationSpec = tween(1000),
            label = "SpeedometerShift"
        )

        Column(
            // Použijeme BiasAlignment pro plynulý posun
            modifier = Modifier.align(BiasAlignment(alignmentBias, 0f)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "0",
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "KM/H",
                fontSize = 16.sp,
                color = Color.Gray
            )
        }

        // --- 3. PRAVÁ ČÁST (Baterie) ---
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "80%",
                fontSize = 20.sp,
                color = Color.Green,
                modifier = Modifier.padding(end = 4.dp)
            )
            Icon(
                imageVector = Icons.Default.BatteryFull,
                contentDescription = "Battery",
                tint = Color.Green
            )
        }

        // Ikonka auta dole (jen když se nenaviguje, aby to nebylo přeplácané)
        if (instruction == null) {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = "Car Status",
                tint = Color.DarkGray,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(32.dp)
            )
        }
    }
}

@Composable
fun Viewport(
    modifier: Modifier = Modifier,
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
    var routeGeoJson by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    var is3dMode by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(is3dMode) {
        val currentCamera = mapViewportState.cameraState
        val targetPitch = if (is3dMode) 60.0 else 0.0
        val targetZoom = if (is3dMode && (currentCamera?.zoom ?: 0.0) < 15.0) 16.0 else currentCamera?.zoom ?: 11.0

        mapViewportState.flyTo(
            CameraOptions.Builder()
                .center(currentCamera?.center)
                .zoom(targetZoom)
                .pitch(targetPitch)
                .bearing(currentCamera?.bearing)
                .build(),
            MapAnimationOptions.Builder().duration(1500).build()
        )
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            delay(500)
            fetchSuggestions(searchQuery, context) { results -> suggestions = results }
        } else {
            suggestions = emptyList()
        }
    }

    Box(modifier = modifier.fillMaxWidth().background(Color.DarkGray)) {
        TeslaMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            routeGeoJson = routeGeoJson,
            is3dMode = is3dMode
        )

        // 3D Button
        FloatingActionButton(
            onClick = { is3dMode = !is3dMode },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 80.dp),
            containerColor = if (is3dMode) Color.Cyan else Color.Black,
            contentColor = if (is3dMode) Color.Black else Color.White
        ) {
            Text(text = if (is3dMode) "3D" else "2D", fontWeight = FontWeight.Bold)
        }

        // Search UI
        if (isSearchVisible) {
            Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).fillMaxWidth(0.6f)) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Where to go?", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().background(Color.Black, shape = RoundedCornerShape(8.dp)),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Black, unfocusedContainerColor = Color.Black, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = ""; suggestions = emptyList() }) { Icon(Icons.Default.Close, "Clear", tint = Color.Gray) } }
                )

                if (suggestions.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).background(Color.Black.copy(alpha = 0.9f), shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))) {
                        items(suggestions) { place ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    searchQuery = place.name
                                    suggestions = emptyList()
                                    focusManager.clearFocus()

                                    val currentPoint = mapViewportState.cameraState?.center ?: Point.fromLngLat(14.4378, 50.0755)
                                    scope.launch(Dispatchers.IO) {
                                        fetchRouteManual(currentPoint, place.point, context) { newGeoJson, instruction ->
                                            scope.launch(Dispatchers.Main) {
                                                routeGeoJson = newGeoJson
                                                onInstructionUpdated(instruction) // Poslat instrukci nahoru
                                                isSearchVisible = false
                                                is3dMode = true
                                            }
                                        }
                                    }
                                }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, null, tint = Color.Cyan, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(place.name, color = Color.White, fontSize = 14.sp)
                            }
                            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                isSearchVisible = !isSearchVisible
                if (!isSearchVisible) { suggestions = emptyList(); searchQuery = "" }
            },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))
        ) {
            Icon(Icons.Default.Search, "Search", tint = Color.White)
            Spacer(modifier = Modifier.size(8.dp))
            Text(if (isSearchVisible) "CLOSE" else "SEARCH", color = Color.White)
        }

        FloatingActionButton(
            onClick = {
                mapViewportState.transitionToFollowPuckState(
                    followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                        .bearing(FollowPuckViewportStateBearing.Constant(0.0)).pitch(0.0).zoom(14.0).build(),
                )
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            containerColor = Color.Black,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.MyLocation, "Locate Me")
        }
    }
}

@Composable
fun TeslaMap(
    modifier: Modifier = Modifier,
    mapViewportState: com.mapbox.maps.extension.compose.animation.viewport.MapViewportState,
    routeGeoJson: String? = null,
    is3dMode: Boolean = false
) {
    val context = LocalContext.current
    var locationPermissionGranted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        locationPermissionGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) || permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    MapboxMap(modifier = Modifier.fillMaxSize(), mapViewportState = mapViewportState) {
        MapEffect(locationPermissionGranted) { mapView ->
            mapView.mapboxMap.loadStyleUri(Style.DARK) { style ->
                val sourceId = "route-source"
                val layerId = "route-layer"
                if (!style.styleSourceExists(sourceId)) style.addSource(geoJsonSource(sourceId) { data("") })
                if (!style.styleLayerExists(layerId)) {
                    style.addLayer(lineLayer(layerId, sourceId) {
                        lineColor("#00B0FF"); lineWidth(6.0); lineCap(LineCap.ROUND); lineJoin(LineJoin.ROUND)
                    })
                }
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

        MapEffect(routeGeoJson) { mapView ->
            if (routeGeoJson != null) {
                mapView.mapboxMap.getStyle { style ->
                    if (style.styleSourceExists("route-source")) {
                        val source = style.getSource("route-source") as? GeoJsonSource
                        source?.data(routeGeoJson)
                    }
                }
            }
        }
    }
}

fun fetchSuggestions(query: String, context: Context, onResult: (List<SearchSuggestion>) -> Unit) {
    val accessToken = context.getString(R.string.mapbox_access_token)
    val client = OkHttpClient()
    val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/${query}.json?access_token=$accessToken&limit=5&autocomplete=true"
    val request = Request.Builder().url(url).build()

    client.newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) { e.printStackTrace() }
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            response.body?.string()?.let { jsonString ->
                try {
                    val json = JSONObject(jsonString)
                    val features = json.optJSONArray("features")
                    val results = mutableListOf<SearchSuggestion>()
                    if (features != null) {
                        for (i in 0 until features.length()) {
                            val feature = features.getJSONObject(i)
                            val name = feature.getString("place_name")
                            val center = feature.getJSONArray("center")
                            val point = Point.fromLngLat(center.getDouble(0), center.getDouble(1))
                            results.add(SearchSuggestion(name, point))
                        }
                    }
                    onResult(results)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    })
}

fun fetchRouteManual(origin: Point, destination: Point, context: Context, onRouteFound: (String, NavInstruction?) -> Unit) {
    val accessToken = context.getString(R.string.mapbox_access_token)
    val client = OkHttpClient()
    val url = "https://api.mapbox.com/directions/v5/mapbox/driving-traffic/" +
            "${origin.longitude()},${origin.latitude()};${destination.longitude()},${destination.latitude()}" +
            "?geometries=geojson&steps=true&overview=full&access_token=$accessToken"
    val request = Request.Builder().url(url).build()

    client.newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) { e.printStackTrace() }
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            response.body?.string()?.let { jsonString ->
                try {
                    val json = JSONObject(jsonString)
                    val routes = json.optJSONArray("routes")
                    if (routes != null && routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val geometry = route.getJSONObject("geometry")
                        val featureString = """{ "type": "Feature", "properties": {}, "geometry": $geometry }"""

                        var navInstruction: NavInstruction? = null
                        val legs = route.getJSONArray("legs")
                        if (legs.length() > 0) {
                            val steps = legs.getJSONObject(0).getJSONArray("steps")
                            if (steps.length() > 1) {
                                val step = steps.getJSONObject(1)
                                val maneuver = step.getJSONObject("maneuver")
                                val text = maneuver.getString("instruction")
                                val distance = step.getDouble("distance").toInt()
                                val modifier = if (maneuver.has("modifier")) maneuver.getString("modifier") else null
                                navInstruction = NavInstruction(text, distance, modifier)
                            } else if (steps.length() > 0) {
                                val step = steps.getJSONObject(0)
                                val maneuver = step.getJSONObject("maneuver")
                                val text = maneuver.getString("instruction")
                                val distance = step.getDouble("distance").toInt()
                                navInstruction = NavInstruction(text, distance, null)
                            }
                        }
                        onRouteFound(featureString, navInstruction)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    })
}

@Composable
fun Dock(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 16.dp).background(Color(0xFF1A1A1A), shape = RoundedCornerShape(12.dp)).padding(16.dp), contentAlignment = Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, "Music", tint = Color.Gray, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.size(16.dp))
                Column { Text("No Music Playing", color = Color.White, fontWeight = FontWeight.Bold); Text("Tap to connect", color = Color.Gray, fontSize = 12.sp) }
            }
        }
        Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            DockIcon(Icons.Default.DirectionsCar, "Car"); DockIcon(Icons.Default.Apps, "Apps"); DockIcon(Icons.Default.Settings, "Settings")
        }
    }
}

@Composable
fun DockIcon(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(40.dp))
        Text(text = label, color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Preview(showBackground = true, device = "spec:width=1080px,height=2340px,dpi=440")
@Composable
fun TeslaLayoutPreview() { TeslaLauncherTheme { TeslaLayout() } }