package com.launchers.teslalauncherv2.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launchers.teslalauncherv2.data.AppManager
import com.launchers.teslalauncherv2.data.MapContinent
import com.launchers.teslalauncherv2.data.MapCountry
import com.launchers.teslalauncherv2.data.NavInstruction
import com.launchers.teslalauncherv2.data.OfflineRegionsDatabase
import com.launchers.teslalauncherv2.data.createBoundingBoxAround
import com.launchers.teslalauncherv2.map.OfflineMapManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

// Full-screen overlay displaying all installed applications
@Composable
fun AppDrawerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val allApps = remember { AppManager.getApps() }
    var searchQuery by remember { mutableStateOf("") }

    // Filter apps based on user search input
    val displayedApps = if (searchQuery.isBlank()) {
        allApps
    } else {
        allApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)).clickable(enabled = false) {}) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 48.dp, start = 24.dp, end = 24.dp)) {

            // Header with title and close button
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("APPLICATIONS", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose, modifier = Modifier.background(Color(0xFF333333), RoundedCornerShape(50))) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search bar for filtering apps
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps...", color = Color.Gray) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray) }
                    }
                },
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Cyan,
                    unfocusedBorderColor = Color.DarkGray
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Grid of app icons
            if (allApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.Cyan)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(displayedApps) { app ->
                        Column(
                            modifier = Modifier.padding(16.dp).clickable {
                                try { context.startActivity(app.intent) } catch (e: Exception) { Toast.makeText(context, "Cannot launch app", Toast.LENGTH_SHORT).show() }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = app.label, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// Minimalist dark screen for night driving, showing only essential info
@Composable
fun NightPanelScreen(speed: Int, rpm: Int, error: String?, instruction: NavInstruction?, currentNavDistance: Int?, onExit: () -> Unit) {
    // Smoothly animate speed changes
    val animatedSpeed by animateIntAsState(targetValue = speed, animationSpec = tween(500), label = "Speed")

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            // Display upcoming navigation maneuver if active
            if (instruction != null) {
                val dist = currentNavDistance ?: instruction.distance
                if (dist > 2000) {
                    Text(text = String.format(Locale.US, "%.1f km", dist / 1000f), color = Color(0xFF444444), fontSize = 24.sp, fontWeight = FontWeight.Medium)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when { instruction.modifier?.contains("left") == true -> Icons.AutoMirrored.Filled.ArrowBack; instruction.modifier?.contains("right") == true -> Icons.AutoMirrored.Filled.ArrowForward; else -> Icons.Default.Navigation }
                        Icon(icon, null, tint = Color(0xFF008888), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = "$dist m", color = Color(0xFF00AAAA), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Giant speedometer
            Text(text = "$animatedSpeed", color = Color(0xFF888888), fontSize = 140.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-5).sp)
            Text("km/h", color = Color(0xFF333333), fontSize = 20.sp)

            // Show RPM only if revving high
            if (rpm > 3500) { Spacer(modifier = Modifier.height(24.dp)); Text(text = "$rpm RPM", color = Color(0xFFBB0000), fontSize = 32.sp, fontWeight = FontWeight.Bold) }

            // Display engine fault code if detected
            if (!error.isNullOrEmpty()) { Spacer(modifier = Modifier.height(24.dp)); Text(text = "⚠ $error", color = Color(0xFFAAAA00), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFF222200), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) }
        }
        // Tap area to exit night mode
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.15f).background(Color.Transparent).clickable { onExit() }, contentAlignment = Alignment.Center) { Text("TAP TO WAKE", color = Color(0xFF111111), fontSize = 10.sp, modifier = Modifier.padding(bottom = 16.dp)) }
    }
}

// Global settings menu for map engines, offline maps, and OBD config
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    currentMapEngine: String,
    onMapEngineChange: (String) -> Unit,
    currentSearchEngine: String,
    onSearchEngineChange: (String) -> Unit,
    currentLocation: android.location.Location?,
    currentObdMac: String,
    onObdMacChange: (String) -> Unit,
    currentRouteGeoJson: String? = null
) {
    val context = LocalContext.current

    // Navigation state for offline map selection menus
    var downloadMenuLevel by remember { mutableIntStateOf(0) }
    var selectedContinent by remember { mutableStateOf<MapContinent?>(null) }
    var selectedCountry by remember { mutableStateOf<MapCountry?>(null) }

    // Download state variables
    var downloadingRegionId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableIntStateOf(0) }

    // Geocode current GPS coords to city/area name
    var currentLocationName by remember { mutableStateOf("Searching GPS location...") }
    var tempObdMac by remember { mutableStateOf(currentObdMac) }

    val prefs = remember { context.getSharedPreferences("offline_maps_status", Context.MODE_PRIVATE) }
    var downloadedRegions by remember { mutableStateOf(prefs.getStringSet("downloaded_ids", emptySet()) ?: emptySet()) }
    var savedRoutes by remember { mutableStateOf(prefs.getStringSet("saved_routes", emptySet()) ?: emptySet()) }

    LaunchedEffect(currentLocation) {
        if (currentLocation != null) {
            withContext(Dispatchers.IO) {
                try {
                    val geocoder = android.location.Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(currentLocation.latitude, currentLocation.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        currentLocationName = addresses[0].locality ?: addresses[0].adminArea ?: "Current Location"
                    }
                } catch (e: Exception) {
                    currentLocationName = "GPS Available (Offline)"
                }
            }
        } else {
            currentLocationName = "Waiting for GPS..."
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.9f).background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp)).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with dynamic back button based on menu level
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (downloadMenuLevel > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { downloadMenuLevel-- }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.Cyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (downloadMenuLevel) {
                                1 -> "SELECT CONTINENT"
                                2 -> selectedContinent?.name?.uppercase() ?: ""
                                3 -> selectedCountry?.name?.uppercase() ?: ""
                                else -> ""
                            },
                            color = Color.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text("SOFTWARE & MAP SETTINGS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.Gray) }
            }
            HorizontalDivider(color = Color.DarkGray)

            // Main Settings Menu
            if (downloadMenuLevel == 0) {

                val currentSavedRouteEntry = savedRoutes.firstOrNull { it.startsWith("active_route|") }
                val isRouteDownloaded = currentSavedRouteEntry != null

                // Option to download the currently active route for offline use
                if (currentRouteGeoJson != null || isRouteDownloaded) {
                    var isDownloadingRoute by remember { mutableStateOf(false) }
                    var routeDownloadProgress by remember { mutableIntStateOf(0) }

                    Row(modifier = Modifier.fillMaxWidth().background(if(isRouteDownloaded) Color(0xFF003300) else if(isDownloadingRoute) Color(0xFF003333) else Color(0xFF112233), RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (isRouteDownloaded && currentRouteGeoJson == null) "📍 Saved Route (Previous)" else "📍 Current Route", color = Color.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                            if (isDownloadingRoute) {
                                LinearProgressIndicator(
                                    progress = { routeDownloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    color = Color.Cyan, trackColor = Color.DarkGray
                                )
                            } else if (isRouteDownloaded) {
                                Text("Installed ✓ (Auto-delete in 30 days)", color = Color.Green, fontSize = 14.sp)
                            } else {
                                Text("Save Temporarily (30 days)", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        if (isDownloadingRoute) {
                            Text("$routeDownloadProgress %", color = Color.White, fontWeight = FontWeight.Bold)
                        } else if (isRouteDownloaded) {
                            IconButton(
                                onClick = {
                                    try { OfflineMapManager.deleteRegion("active_route") } catch (e: Exception) {}
                                    val newSet = savedRoutes.toMutableSet().apply { remove(currentSavedRouteEntry) }
                                    prefs.edit().putStringSet("saved_routes", newSet).apply()
                                    savedRoutes = newSet
                                    Toast.makeText(context, "Route deleted", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.background(Color(0xFF550000), RoundedCornerShape(50))
                            ) { Icon(Icons.Default.Delete, "Delete Route", tint = Color.White) }
                        } else if (currentRouteGeoJson != null) {
                            IconButton(
                                onClick = {
                                    val routeGeometry = getRouteBoundingBox(currentRouteGeoJson)
                                    if (routeGeometry != null) {
                                        isDownloadingRoute = true
                                        routeDownloadProgress = 0
                                        Toast.makeText(context, "Downloading route map...", Toast.LENGTH_SHORT).show()

                                        OfflineMapManager.downloadRegion(
                                            context = context,
                                            regionId = "active_route",
                                            geometry = routeGeometry,
                                            onProgress = { routeDownloadProgress = it },
                                            onComplete = {
                                                isDownloadingRoute = false
                                                val rPrefs = context.getSharedPreferences("offline_maps_status", Context.MODE_PRIVATE)
                                                val routes = rPrefs.getStringSet("saved_routes", mutableSetOf()) ?: mutableSetOf()
                                                val newSet = routes.toMutableSet().apply {
                                                    removeIf { it.startsWith("active_route|") }
                                                    add("active_route|${System.currentTimeMillis()}")
                                                }
                                                rPrefs.edit().putStringSet("saved_routes", newSet).apply()
                                                savedRoutes = newSet
                                                Toast.makeText(context, "Route saved offline!", Toast.LENGTH_LONG).show()
                                            },
                                            onError = {
                                                isDownloadingRoute = false
                                                Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.background(Color(0xFF005555), RoundedCornerShape(50))
                            ) { Icon(Icons.Default.Download, "Download Route", tint = Color.White) }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.DarkGray)
                }

                // OBD Configuration
                Column {
                    Text("OBD2 Bluetooth Adapter (MAC Address)", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = tempObdMac,
                            onValueChange = { tempObdMac = it.uppercase() },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            singleLine = true,
                            label = { Text("e.g. 00:10:CC:4F:36:03", color = Color.DarkGray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Cyan, unfocusedBorderColor = Color.DarkGray,
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                onObdMacChange(tempObdMac)
                                Toast.makeText(context, "Saved. Connecting...", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005555)),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("SAVE & CONNECT", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                HorizontalDivider(color = Color.DarkGray)

                // Map Engine Selection
                Column {
                    Text("Map Engine (Visuals)", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onMapEngineChange("MAPBOX") }, colors = ButtonDefaults.buttonColors(containerColor = if (currentMapEngine == "MAPBOX") Color.White else Color(0xFF333333), contentColor = if (currentMapEngine == "MAPBOX") Color.Black else Color.White), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Map, null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("MAPBOX (Hybrid)", fontWeight = FontWeight.Bold) }
                        Button(onClick = { onMapEngineChange("GOOGLE") }, colors = ButtonDefaults.buttonColors(containerColor = if (currentMapEngine == "GOOGLE") Color.White else Color(0xFF333333), contentColor = if (currentMapEngine == "GOOGLE") Color.Black else Color.White), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Map, null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("GOOGLE MAPS (Online)", fontWeight = FontWeight.Bold) }
                    }
                }
                HorizontalDivider(color = Color.DarkGray)

                // 🌟 NEW SECTION: Driving Assistance (Speed Limits)
                Column {
                    Text("Driving Assistance", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    var isSpeedLimitEnabled by remember { mutableStateOf(context.getSharedPreferences("TeslaSettings", Context.MODE_PRIVATE).getBoolean("show_speed_limit", true)) }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            isSpeedLimitEnabled = !isSpeedLimitEnabled
                            context.getSharedPreferences("TeslaSettings", Context.MODE_PRIVATE).edit().putBoolean("show_speed_limit", isSpeedLimitEnabled).apply()
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, null, tint = Color.White) // Make sure to import Icons.Default.Speed or similar
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Speed Limit Assist", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Show speed limits on dashboard", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                        Switch(
                            checked = isSpeedLimitEnabled,
                            onCheckedChange = {
                                isSpeedLimitEnabled = it
                                context.getSharedPreferences("TeslaSettings", Context.MODE_PRIVATE).edit().putBoolean("show_speed_limit", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Cyan, checkedTrackColor = Color(0xFF004444))
                        )
                    }
                }
                HorizontalDivider(color = Color.DarkGray)

                // Smart Offline Area Download
                Column {
                    Text("Offline Regions", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (downloadingRegionId != null && downloadingRegionId != "auto_region") {
                        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF003333), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Downloading Region...", color = Color.Cyan, fontWeight = FontWeight.Bold)
                                LinearProgressIndicator(progress = { downloadProgress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Color.Cyan, trackColor = Color.DarkGray)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("$downloadProgress %", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    val isAutoDownloaded = downloadedRegions.contains("auto_region")
                    val isAutoDownloading = downloadingRegionId == "auto_region"

                    Row(modifier = Modifier.fillMaxWidth().background(if (isAutoDownloaded) Color(0xFF003300) else Color(0xFF112233), RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("📍 Smart Region: $currentLocationName", color = Color.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(if (isAutoDownloaded) "Installed ✓" else "Auto-radius ~100 km", color = if (isAutoDownloaded) Color.Green else Color.Gray, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        if (isAutoDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Cyan, strokeWidth = 2.dp)
                        } else if (isAutoDownloaded) {
                            IconButton(
                                onClick = {
                                    OfflineMapManager.deleteRegion("auto_region")
                                    val newSet = downloadedRegions.toMutableSet().apply { remove("auto_region") }
                                    prefs.edit().putStringSet("downloaded_ids", newSet).apply()
                                    downloadedRegions = newSet
                                    Toast.makeText(context, "Region deleted", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.background(Color(0xFF550000), RoundedCornerShape(50))
                            ) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
                        } else if (currentLocation != null) {
                            IconButton(
                                onClick = {
                                    downloadingRegionId = "auto_region"
                                    downloadProgress = 0
                                    Toast.makeText(context, "Starting: Surroundings of $currentLocationName", Toast.LENGTH_SHORT).show()
                                    val geo = createBoundingBoxAround(currentLocation.latitude, currentLocation.longitude, 50.0)
                                    OfflineMapManager.downloadRegion(
                                        context = context, regionId = "auto_region", geometry = geo,
                                        onProgress = { downloadProgress = it },
                                        onComplete = {
                                            downloadingRegionId = null
                                            val newSet = downloadedRegions.toMutableSet().apply { add("auto_region") }
                                            prefs.edit().putStringSet("downloaded_ids", newSet).apply()
                                            downloadedRegions = newSet
                                            Toast.makeText(context, "Done!", Toast.LENGTH_LONG).show()
                                        },
                                        onError = { downloadingRegionId = null; Toast.makeText(context, "Error: $it", Toast.LENGTH_LONG).show() }
                                    )
                                },
                                modifier = Modifier.background(Color(0xFF005555), RoundedCornerShape(50))
                            ) { Icon(Icons.Default.CloudDownload, "Download", tint = Color.White) }
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Gray, strokeWidth = 2.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { downloadMenuLevel = 1 }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Language, null, tint = Color.White); Spacer(modifier = Modifier.width(8.dp)); Text("BROWSE REGIONS MANUALLY", color = Color.White)
                    }
                }
            }
            // Browse offline map regions - Continent Level
            else if (downloadMenuLevel == 1) {
                OfflineRegionsDatabase.continents.forEach { continent ->
                    Button(onClick = { selectedContinent = continent; downloadMenuLevel = 2 }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)), modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(12.dp)) {
                        Text(continent.name, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.Gray)
                    }
                }
            }
            // Browse offline map regions - Country Level
            else if (downloadMenuLevel == 2) {
                selectedContinent?.countries?.forEach { country ->
                    Button(onClick = { selectedCountry = country; downloadMenuLevel = 3 }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)), modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(12.dp)) {
                        Text(country.name, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.Gray)
                    }
                }
            }
            // Browse offline map regions - Specific Area Level
            else if (downloadMenuLevel == 3) {
                selectedCountry?.regions?.forEach { region ->

                    val isDownloaded = downloadedRegions.contains(region.id)
                    val isThisDownloading = downloadingRegionId == region.id

                    Row(modifier = Modifier.fillMaxWidth().background(if (isDownloaded) Color(0xFF003300) else Color(0xFF2A2A2A), RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(region.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(if (isDownloaded) "Installed ✓" else region.sizeMb, color = if (isDownloaded) Color.Green else Color.Gray, fontSize = 14.sp)
                        }

                        if (isThisDownloading) {
                            CircularProgressIndicator(color = Color.Cyan, modifier = Modifier.size(24.dp))
                        } else if (isDownloaded) {
                            IconButton(
                                onClick = {
                                    OfflineMapManager.deleteRegion(region.id)
                                    val newSet = downloadedRegions.toMutableSet().apply { remove(region.id) }
                                    prefs.edit().putStringSet("downloaded_ids", newSet).apply()
                                    downloadedRegions = newSet
                                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.background(Color(0xFF550000), RoundedCornerShape(50))
                            ) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
                        } else {
                            IconButton(
                                onClick = {
                                    downloadingRegionId = region.id
                                    downloadProgress = 0
                                    OfflineMapManager.downloadRegion(
                                        context = context, regionId = region.id, geometry = region.geometry,
                                        onProgress = { downloadProgress = it },
                                        onComplete = {
                                            downloadingRegionId = null
                                            val newSet = downloadedRegions.toMutableSet().apply { add(region.id) }
                                            prefs.edit().putStringSet("downloaded_ids", newSet).apply()
                                            downloadedRegions = newSet
                                            Toast.makeText(context, "Downloaded and Ready!", Toast.LENGTH_LONG).show()
                                        },
                                        onError = { downloadingRegionId = null; Toast.makeText(context, "Error: $it", Toast.LENGTH_LONG).show() }
                                    )
                                },
                                modifier = Modifier.background(Color(0xFF444444), RoundedCornerShape(50))
                            ) { Icon(Icons.Default.CloudDownload, "Download", tint = Color.White) }
                        }
                    }
                }
            }
        }
    }
}