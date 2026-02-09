package com.launchers.teslalauncherv2

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.locationcomponent.location

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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.DarkGray)
    ) {
        // Map View
        TeslaMap(modifier = Modifier.fillMaxSize())

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
fun TeslaMap(modifier: Modifier = Modifier) {
    // Mapbox automatically handles lifecycle in the Composable
    MapboxMap(
        modifier = modifier,
        mapViewportState = rememberMapViewportState {
            setCameraOptions {
                zoom(12.0)
                center(Point.fromLngLat(0.0, 0.0)) // Default to null island or user location
                pitch(0.0)
                bearing(0.0)
            }
        },
        style = {
            MapStyle(style = Style.DARK)
        }
    ) {
        // Enable User Location (Puck)
        MapEffect(Unit) { mapView ->
            mapView.location.updateSettings {
                enabled = true
                pulsingEnabled = true
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