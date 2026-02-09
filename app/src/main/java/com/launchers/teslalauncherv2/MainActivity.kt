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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// --- HTTP Klient (Místo rozbité knihovny) ---
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

// --- Mapbox Maps SDK ---
import com.mapbox.geojson.Point
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.style.layers.addLayer
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TeslaLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TeslaLayout()
                }
            }
        }
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
    Column(modifier = Modifier.fillMaxSize()) {
        InstrumentCluster(modifier = Modifier.weight(0.25f))
        Viewport(modifier = Modifier.weight(0.60f))
        Dock(modifier = Modifier.weight(0.15f))
    }
}

@Composable
fun InstrumentCluster(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("0", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = Color.Cyan)
            Text("KM/H", fontSize = 20.sp, color = Color.Gray)
        }
        Text("12:00", modifier = Modifier.align(Alignment.TopStart), fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color.White)
        Row(modifier = Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            Text("80%", fontSize = 20.sp, color = Color.Green, modifier = Modifier.padding(end = 4.dp))
            Icon(imageVector = Icons.Default.BatteryFull, contentDescription = "Battery", tint = Color.Green)
        }
        Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = "Car Status", tint = Color.Gray, modifier = Modifier.align(Alignment.BottomCenter).size(48.dp))
    }
}

@Composable
fun Viewport(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(14.4378, 50.0755)) // Praha
            zoom(11.0)
            pitch(0.0)
        }
    }

    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var routeGeoJson by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxWidth().background(Color.DarkGray)) {
        TeslaMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            routeGeoJson = routeGeoJson
        )

        if (isSearchVisible) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Navigate to...", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .fillMaxWidth(0.6f)
                    .background(Color.Black, shape = RoundedCornerShape(8.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Black,
                    unfocusedContainerColor = Color.Black,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        val cameraState = mapViewportState.cameraState
                        val currentPoint = cameraState.center ?: Point.fromLngLat(14.4378, 50.0755)

                        scope.launch(Dispatchers.IO) {
                            // VOLÁME NOVOU, BEZPEČNOU FUNKCI
                            fetchRouteManual(searchQuery, currentPoint, context) { newGeoJson ->
                                scope.launch(Dispatchers.Main) {
                                    routeGeoJson = newGeoJson
                                    isSearchVisible = false
                                }
                            }
                        }
                    }
                ),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Go)
            )
        }

        Button(
            onClick = { isSearchVisible = !isSearchVisible },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))
        ) {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
            Spacer(modifier = Modifier.size(8.dp))
            Text(if (isSearchVisible) "CLOSE" else "SEARCH", color = Color.White)
        }

        FloatingActionButton(
            onClick = {
                mapViewportState.transitionToFollowPuckState(
                    followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                        .bearing(FollowPuckViewportStateBearing.Constant(0.0))
                        .pitch(0.0)
                        .zoom(14.0)
                        .build(),
                )
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            containerColor = Color.Black,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Locate Me")
        }
    }
}

@Composable
fun TeslaMap(
    modifier: Modifier = Modifier,
    mapViewportState: com.mapbox.maps.extension.compose.animation.viewport.MapViewportState,
    routeGeoJson: String? = null
) {
    val context = LocalContext.current
    var locationPermissionGranted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    MapboxMap(
        modifier = Modifier.fillMaxSize(),
        mapViewportState = mapViewportState
    ) {
        MapEffect(locationPermissionGranted) { mapView ->
            mapView.mapboxMap.loadStyleUri(Style.DARK) { style ->
                val sourceId = "route-source"
                val layerId = "route-layer"
                if (!style.styleSourceExists(sourceId)) {
                    style.addSource(geoJsonSource(sourceId) { data("") })
                }
                if (!style.styleLayerExists(layerId)) {
                    style.addLayer(
                        lineLayer(layerId, sourceId) {
                            lineColor("#00B0FF")
                            lineWidth(6.0)
                            lineCap(LineCap.ROUND)
                            lineJoin(LineJoin.ROUND)
                        }
                    )
                }
            }

            if (locationPermissionGranted) {
                mapView.location.updateSettings {
                    enabled = true
                    pulsingEnabled = true
                }
            }
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

// ---------------------------------------------------------
// TOTO JE TA "ZÁCHRANNÁ" FUNKCE
// Nepoužívá knihovnu 'mapbox-services', takže nehrozí konflikt verzí.
// Prostě si stáhne data jako text a pošle je do mapy.
// ---------------------------------------------------------
fun fetchRouteManual(query: String, currentLoc: Point, context: Context, onRouteFound: (String) -> Unit) {
    val accessToken = context.getString(R.string.mapbox_access_token)
    val client = OkHttpClient()

    // 1. Geocoding (Najdi město)
    val geoUrl = "https://api.mapbox.com/geocoding/v5/mapbox.places/${query}.json?access_token=$accessToken&limit=1"
    val geoRequest = Request.Builder().url(geoUrl).build()

    client.newCall(geoRequest).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) {
            e.printStackTrace()
        }

        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            response.body?.string()?.let { jsonString ->
                try {
                    val json = JSONObject(jsonString)
                    val features = json.optJSONArray("features")
                    if (features != null && features.length() > 0) {
                        val feature = features.getJSONObject(0)
                        val center = feature.getJSONArray("center")
                        val destLon = center.getDouble(0)
                        val destLat = center.getDouble(1)

                        // 2. Navigace (Vypočítat trasu)
                        val directionsUrl = "https://api.mapbox.com/directions/v5/mapbox/driving-traffic/" +
                                "${currentLoc.longitude()},${currentLoc.latitude()};$destLon,$destLat" +
                                "?geometries=geojson&overview=full&access_token=$accessToken"

                        val dirRequest = Request.Builder().url(directionsUrl).build()

                        client.newCall(dirRequest).enqueue(object : okhttp3.Callback {
                            override fun onFailure(call: okhttp3.Call, e: IOException) {
                                e.printStackTrace()
                            }

                            override fun onResponse(call: okhttp3.Call, dirResponse: okhttp3.Response) {
                                dirResponse.body?.string()?.let { dirJsonString ->
                                    try {
                                        val dirJson = JSONObject(dirJsonString)
                                        val routes = dirJson.optJSONArray("routes")
                                        if (routes != null && routes.length() > 0) {
                                            val geometry = routes.getJSONObject(0).getJSONObject("geometry")

                                            // Vytvoříme GeoJSON Feature ručně
                                            val featureString = """
                                                {
                                                  "type": "Feature",
                                                  "properties": {},
                                                  "geometry": $geometry
                                                }
                                            """.trimIndent()

                                            onRouteFound(featureString)
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        })
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    })
}

@Composable
fun Dock(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 16.dp).background(Color(0xFF1A1A1A), shape = RoundedCornerShape(12.dp)).padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, "Music", tint = Color.Gray, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.size(16.dp))
                Column {
                    Text("No Music Playing", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Tap to connect", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
        Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            DockIcon(Icons.Default.DirectionsCar, "Car")
            DockIcon(Icons.Default.Apps, "Apps")
            DockIcon(Icons.Default.Settings, "Settings")
        }
    }
}

@Composable
fun DockIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(40.dp))
        Text(text = label, color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Preview(showBackground = true, device = "spec:width=1080px,height=2340px,dpi=440")
@Composable
fun TeslaLayoutPreview() {
    TeslaLauncherTheme { TeslaLayout() }
}