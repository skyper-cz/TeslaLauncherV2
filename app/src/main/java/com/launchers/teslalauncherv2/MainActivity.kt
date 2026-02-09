package com.launchers.teslalauncherv2

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission granted/rejected
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize MapLibre - Not needed for Mapbox v10+ (auto-init or different flow)
        // Mapbox.getInstance(this) is invalid in v10+, it reads token from manifest/resources

        // Request permissions
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        enableEdgeToEdge()
        setContent {
            TeslaLauncherTheme {
                // A surface container using the 'background' color from the theme
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

// --- Theme ---
@Composable
fun TeslaLauncherTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        background = Color.Black,
        surface = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}

// --- Main Layout ---
@Composable
fun TeslaLayout() {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top: Instrument Cluster (25%)
        InstrumentCluster(modifier = Modifier.weight(0.25f))

        // Middle: Viewport (60%)
        Viewport(modifier = Modifier.weight(0.60f))

        // Bottom: Dock (15%)
        Dock(modifier = Modifier.weight(0.15f))
    }
}

// --- Components ---

@Composable
fun InstrumentCluster(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Speedometer center
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "0",
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Cyan
            )
            Text(
                text = "KM/H",
                fontSize = 20.sp,
                color = Color.Gray
            )
        }

        // Top Left: Clock
        Text(
            text = "12:00",
            modifier = Modifier.align(Alignment.TopStart),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )

        // Top Right: Battery
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

        // Car Icon / Gear or other info can go here
         Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = "Car Status",
            tint = Color.Gray,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(48.dp)
        )
    }
}

@Composable
fun Viewport(modifier: Modifier = Modifier) {
    // Hoist map state to Viewport to allow FAB control
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(14.4378, 50.0755)) // Praha
            zoom(11.0)
            pitch(0.0)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.DarkGray)
    ) {
        // Map View
        TeslaMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState
        )

        // Top-Left: Search Button
        Button(
            onClick = { /* TODO */ },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))
            // Z-index handled by composition order, this comes after map
        ) {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
            Spacer(modifier = Modifier.size(8.dp))
            Text("SEARCH", color = Color.White)
        }

        // Top-Right: Locate Me FAB
        // Top-Right: Locate Me FAB
        FloatingActionButton(
            onClick = {
                // OPRAVA PRO VERZI 11:
                // Místo starého state používáme Options Builder
                mapViewportState.transitionToFollowPuckState(
                    followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                        .bearing(FollowPuckViewportStateBearing.Constant(0.0)) // Kamera se nebude otáčet, sever nahoře
                        // .bearing(FollowPuckViewportStateBearing.Course) // Odkomentujte, pokud chcete, aby se mapa točila podle jízdy
                        .pitch(0.0) // Pohled shora (nebo dejte 45.0 pro 3D)
                        .zoom(14.0) // Přiblížení na auto
                        .build(),
                )
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            containerColor = Color.Black,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Locate Me")
        }

        // Bottom-Right: Reverse Button (visual placeholder)
        Button(
            onClick = { /* TODO */ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))
        ) {
            Text("REVERSE", color = Color.White)
        }
    }
}

@Composable
fun TeslaMap(
    modifier: Modifier = Modifier,
    mapViewportState: com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
) {
    val context = LocalContext.current
    var locationPermissionGranted by remember { mutableStateOf(false) }

    // Check initial permission status
    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionGranted = true
        }
    }

    // 1. Permission Logic
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                      permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        locationPermissionGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
             permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Mapbox automatically handles lifecycle in the Composable
    MapboxMap(
        modifier = Modifier.fillMaxSize(),
        mapViewportState = mapViewportState
    ) {
        // Enable User Location (Puck) and Route Layer
        MapEffect(locationPermissionGranted) { mapView ->
            // Enable Location ONLY if granted
            if (locationPermissionGranted) {
                mapView.location.enabled = true
            }

            // Draw Route
            mapView.mapboxMap.getStyle { style ->
                val sourceId = "route-source"
                val layerId = "route-layer"

                // Add Source
                if (!style.styleSourceExists(sourceId)) {
                   val dummyGeoJson = """
                        {
                          "type": "Feature",
                          "properties": {},
                          "geometry": {
                            "type": "LineString",
                            "coordinates": [
                              [14.40, 50.07],
                              [14.42, 50.08],
                              [14.45, 50.08],
                              [14.48, 50.06]
                            ]
                          }
                        }
                    """.trimIndent()

                    style.addSource(
                        geoJsonSource(sourceId) {
                            data(dummyGeoJson)
                        }
                    )
                }

                // Add Layer
                if (!style.styleLayerExists(layerId)) {
                    style.addLayer(
                        lineLayer(layerId, sourceId) {
                            lineColor("#00B0FF") // Tesla/Neon Blue
                            lineWidth(6.0)
                            lineCap(LineCap.ROUND)
                            lineJoin(LineJoin.ROUND)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun Dock(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Music Control Placeholder
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 16.dp)
                .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Music",
                    tint = Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.size(16.dp))
                Column {
                    Text("No Music Playing", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Tap to connect", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }

        // Right: App Icons
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            DockIcon(Icons.Default.DirectionsCar, "Car")
            DockIcon(Icons.Default.Apps, "Apps")
            DockIcon(Icons.Default.Settings, "Settings")
        }
    }
}

@Composable
fun DockIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
        Text(text = label, color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Preview(showBackground = true, device = "spec:width=1080px,height=2340px,dpi=440")
@Composable
fun TeslaLayoutPreview() {
    TeslaLauncherTheme {
        TeslaLayout()
    }
}