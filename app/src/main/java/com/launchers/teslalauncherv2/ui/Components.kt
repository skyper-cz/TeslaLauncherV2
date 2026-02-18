package com.launchers.teslalauncherv2.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.core.app.NotificationManagerCompat
import com.launchers.teslalauncherv2.data.CarState
import com.launchers.teslalauncherv2.data.NavInstruction
import com.launchers.teslalauncherv2.media.MediaManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// HELPER FUNCTIONS

// Formats meters into a readable string (m or km)
fun formatDistance(meters: Int): String {
    return if (meters >= 1000) String.format(Locale.US, "%.1f km", meters / 1000f) else "$meters m"
}

// Dispatches media key events to the system (Play/Pause, Next, Prev)
fun sendMediaKeyEvent(context: Context, keyCode: Int) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
}

// Calculates a bounding box geometry around a given route for offline downloading
fun getRouteBoundingBox(routeGeoJson: String, bufferDegrees: Double = 0.05): com.mapbox.geojson.Geometry? {
    try {
        val lineString = com.mapbox.geojson.FeatureCollection.fromJson(routeGeoJson).features()?.firstOrNull()?.geometry() as? com.mapbox.geojson.LineString
        val coords = lineString?.coordinates() ?: return null
        if (coords.isEmpty()) return null

        var minLat = coords[0].latitude()
        var maxLat = coords[0].latitude()
        var minLng = coords[0].longitude()
        var maxLng = coords[0].longitude()

        for (p in coords) {
            if (p.latitude() < minLat) minLat = p.latitude()
            if (p.latitude() > maxLat) maxLat = p.latitude()
            if (p.longitude() < minLng) minLng = p.longitude()
            if (p.longitude() > maxLng) maxLng = p.longitude()
        }

        return com.launchers.teslalauncherv2.data.createBBoxGeometry(
            minLng - bufferDegrees, minLat - bufferDegrees,
            maxLng + bufferDegrees, maxLat + bufferDegrees
        )
    } catch (e: Exception) { return null }
}


// UI COMPONENTS

// 🌟 NEW: Renders the speed limit sign (supports numeric and "Unlimited" German style)
@Composable
fun SpeedLimitSign(limit: Int, modifier: Modifier = Modifier) {
    // If limit is -1 (or effectively 0/very high), it implies no limit (German Autobahn)
    if (limit <= 0 || limit > 150) {
        // German "End of all restrictions" sign (Zeichen 282)
        Box(
            modifier = modifier
                .background(Color.White, CircleShape)
                .border(2.dp, Color.Black, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 3.dp.toPx()
                // Draw 3 diagonal black lines
                for (i in 1..3) {
                    val offset = i * 0.15f
                    drawLine(
                        color = Color.Black,
                        start = Offset(size.width * (0.1f + offset), size.height * 0.8f),
                        end = Offset(size.width * (0.5f + offset), size.height * 0.2f),
                        strokeWidth = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }
    } else {
        // Standard Euro-style speed limit (Red circle, white bg, black text)
        Box(
            modifier = modifier
                .background(Color.White, CircleShape)
                .border(4.dp, Color.Red, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$limit",
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// Main dashboard panel displaying speed, RPM, and navigation
@Composable
fun InstrumentCluster(
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    carState: CarState,
    gpsStatus: String?,
    batteryLevel: Int,
    instruction: NavInstruction?,
    currentNavDistance: Int? = null,
    routeDuration: Int? = null,
    speedLimit: Int? = null,
    showSpeedLimit: Boolean = true, // 🌟 NEW: User preference toggle
    onCancelRoute: () -> Unit = {}
) {
    // Determine if the vehicle is speeding to color the text red
    val isSpeeding = speedLimit != null && speedLimit > 0 && carState.speed > speedLimit + 5 // +5 tolerance
    val speedColor = if (isSpeeding) Color(0xFFFF4444) else Color.White

    Column(modifier = modifier.fillMaxWidth().background(Color.Black).padding(16.dp)) {
        TopStatusBar(gpsStatus, carState.isConnected, carState.isDemoMode, batteryLevel, carState.error)

        if (isLandscape) {
            // Landscape layout: Nav top, Speed/RPM bottom
            Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                if (instruction != null) {
                    NavigationDisplay(instruction, currentNavDistance ?: instruction.distance, routeDuration, onCancelRoute)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // 🌟 UPDATED: Display dynamic speed limit sign
                    if (showSpeedLimit && speedLimit != null) {
                        SpeedLimitSign(limit = speedLimit, modifier = Modifier.size(42.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // 🌟 SPEEDOMETER FIX: maxLines and fixed line height prevent layout jumps
                    Text(
                        text = "${carState.speed}",
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Bold,
                        color = speedColor,
                        letterSpacing = (-2).sp,
                        maxLines = 1,          // Never wrap
                        softWrap = false,      // Disable soft wrap
                        lineHeight = 80.sp     // Fix line height to stop numbers jumping
                    )
                    Text(text = "KM/H", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                RpmBar(rpm = carState.rpm)
            }
        } else {
            // Portrait layout: Nav/RPM left, Speed right
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    if (instruction != null) {
                        NavigationDisplay(instruction, currentNavDistance ?: instruction.distance, routeDuration, onCancelRoute)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    RpmBar(rpm = carState.rpm)
                }
                Column(modifier = Modifier.weight(0.6f), horizontalAlignment = Alignment.End) {
                    if (carState.coolantTemp > 0) Text(text = "${carState.coolantTemp}°C", color = Color.Gray, fontSize = 14.sp)

                    // 🌟 ALIGNMENT FIX FOR PORTRAIT MODE
                    Row(
                        verticalAlignment = Alignment.Bottom, // Align to text baseline
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (showSpeedLimit && speedLimit != null) {
                            // Align sign vertically with the speed text
                            Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                                SpeedLimitSign(limit = speedLimit, modifier = Modifier.size(36.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Text(
                            text = "${carState.speed}",
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Bold,
                            color = speedColor,
                            letterSpacing = (-2).sp,
                            maxLines = 1,
                            softWrap = false,
                            lineHeight = 80.sp,
                            modifier = Modifier.alignByBaseline() // Critical for aligning digits
                        )
                    }
                    Text(text = "KM/H", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ... (Rest of UI components: TopStatusBar, NavigationDisplay, TeslaSearchBar, RpmBar, WideMusicPlayer, Dock, AppMenuItem, GearSelector) ...
@Composable
fun TopStatusBar(gpsStatus: String?, isConnected: Boolean, isDemoMode: Boolean, batteryLevel: Int, carError: String?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (gpsStatus != null) {
                Text(text = gpsStatus, color = Color.White, modifier = Modifier.background(Color.DarkGray, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (isConnected) Icon(Icons.Default.BluetoothConnected, "OBD", tint = Color.Green, modifier = Modifier.size(20.dp))
            else if (isDemoMode) Text("DEMO", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)

            if (carError != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(modifier = Modifier.background(Color(0xFF440000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ERR: $carError", color = Color.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "$batteryLevel%", color = if (batteryLevel < 20) Color.Red else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(if (batteryLevel > 20) Icons.Default.BatteryFull else Icons.Default.BatteryAlert, "Bat", tint = if (batteryLevel < 20) Color.Red else Color.Green, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun NavigationDisplay(instruction: NavInstruction, currentDistance: Int, routeDurationSeconds: Int? = null, onCancelRoute: () -> Unit) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when {
                    instruction.modifier?.contains("left") == true -> Icons.AutoMirrored.Filled.ArrowBack
                    instruction.modifier?.contains("right") == true -> Icons.AutoMirrored.Filled.ArrowForward
                    else -> Icons.Default.Navigation
                }
                Icon(icon, "Nav", tint = Color.Cyan, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = formatDistance(currentDistance), color = Color.Cyan, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
            IconButton(onClick = onCancelRoute, modifier = Modifier.background(Color(0xFF330000), RoundedCornerShape(50)).size(32.dp)) {
                Icon(Icons.Default.Close, "Cancel", tint = Color.Red, modifier = Modifier.size(16.dp))
            }
        }
        Text(text = instruction.text, color = Color.White, fontSize = 16.sp, maxLines = 2, fontWeight = FontWeight.Medium, overflow = TextOverflow.Ellipsis)

        if (routeDurationSeconds != null && routeDurationSeconds > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.background(Color(0xFF1E1E1E), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val etaCalendar = Calendar.getInstance().apply { add(Calendar.SECOND, routeDurationSeconds) }
                val etaFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val durationText = if (routeDurationSeconds / 3600 > 0) "${routeDurationSeconds / 3600}h ${(routeDurationSeconds / 60) % 60}m" else "${(routeDurationSeconds / 60) % 60} min"

                Icon(Icons.Default.Schedule, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = etaFormat.format(etaCalendar.time), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "·", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = durationText, color = Color.Green, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
fun TeslaSearchBar(query: String, onQueryChange: (String) -> Unit, onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Surface(modifier = modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp), color = Color(0xFF2A2A2A), shadowElevation = 4.dp) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = if (isFocused) Color.White else Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty() && !isFocused) Text("Where to go?", color = Color.Gray, fontSize = 16.sp)
                BasicTextField(value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { isFocused = it.isFocused }, textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch(query); focusManager.clearFocus() }), singleLine = true, cursorBrush = SolidColor(Color.White))
            }
            AnimatedVisibility(visible = isFocused || query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = { if (query.isNotEmpty()) onQueryChange("") else focusManager.clearFocus() }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun RpmBar(rpm: Int) {
    if (rpm > 0) {
        val isRedline = rpm >= 5500
        val progress = (rpm / 7000f).coerceIn(0f, 1f)
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                drawRect(color = Color.DarkGray, size = size)
                val barWidth = size.width * progress
                val barColor = if (isRedline) Color.Red else Color.White
                drawRect(color = barColor, topLeft = Offset(0f, 0f), size = size.copy(width = barWidth))
            }
            Text(text = "$rpm RPM", color = if (isRedline) Color.Red else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WideMusicPlayer(modifier: Modifier = Modifier, isLandscape: Boolean = false) {
    val context = LocalContext.current
    val trackInfo by MediaManager.currentTrack.collectAsState()

    var marqueeTrigger by remember { mutableIntStateOf(0) }
    var hasPermission by remember { mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) }
    var showArtist by remember { mutableStateOf(false) }

    LaunchedEffect(showArtist) { if (showArtist) { delay(4000); showArtist = false } }

    val checkPermissionAndNavigate = {
        hasPermission = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        if (!hasPermission) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    Row(
        modifier = modifier.fillMaxHeight().background(Color(0xFF1A1A1A), shape = RoundedCornerShape(12.dp)).padding(if (isLandscape) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageSize = if (isLandscape) 48.dp else 72.dp

        Box(
            modifier = Modifier.size(imageSize).background(Color.DarkGray, shape = RoundedCornerShape(8.dp)).clickable { if (!hasPermission) checkPermissionAndNavigate() else marqueeTrigger++ },
            contentAlignment = Alignment.Center
        ) {
            if (trackInfo.albumArt != null) {
                Image(bitmap = trackInfo.albumArt!!.asImageBitmap(), contentDescription = "Album", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.MusicNote, null, tint = Color.Gray, modifier = Modifier.size(if (isLandscape) 24.dp else 32.dp))
            }
        }

        Spacer(modifier = Modifier.width(if (isLandscape) 8.dp else 12.dp))

        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            val displayTitle = if (hasPermission) trackInfo.title else "Klikni pro povolení"
            val displayArtist = if (hasPermission) trackInfo.artist else "Čtení notifikací"

            Box(modifier = Modifier.fillMaxWidth().clickable { if (hasPermission) showArtist = !showArtist else checkPermissionAndNavigate() }.padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                Crossfade(targetState = showArtist, label = "ArtistToggle") { isShowingArtist ->
                    key(marqueeTrigger) {
                        if (isShowingArtist) {
                            Text(text = displayArtist, color = Color.Cyan, fontSize = if (isLandscape) 12.sp else 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 300))
                        } else {
                            Text(text = displayTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (isLandscape) 14.sp else 16.sp, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 800))
                        }
                    }
                }
            }

            val playSize = if (isLandscape) 36.dp else 48.dp
            val skipSize = if (isLandscape) 28.dp else 36.dp
            val iconSize = if (isLandscape) 24.dp else 28.dp

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, modifier = Modifier.size(skipSize)) {
                    Icon(Icons.Default.SkipPrevious, "Prev", tint = Color.White, modifier = Modifier.size(iconSize))
                }
                IconButton(onClick = { sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, modifier = Modifier.size(playSize)) {
                    Icon(if (trackInfo.isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, "Play", tint = Color.Cyan, modifier = Modifier.fillMaxSize())
                }
                IconButton(onClick = { sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT) }, modifier = Modifier.size(skipSize)) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(iconSize))
                }
            }
        }
    }
}

@Composable
fun Dock(
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    isNightPanel: Boolean = false,
    onNightPanelToggle: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenApps: () -> Unit = {}
) {
    var showAppsMenu by remember { mutableStateOf(false) }
    val dockAlpha by animateFloatAsState(targetValue = if (isNightPanel) 0.5f else 1.0f, label = "DimDock")

    if (isLandscape) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(Color.Black)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .alpha(dockAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                WideMusicPlayer(modifier = Modifier.fillMaxSize(), isLandscape = true)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showAppsMenu = !showAppsMenu }.padding(8.dp)) {
                    Icon(Icons.Default.Menu, "Menu", tint = if (showAppsMenu) Color.Cyan else Color.White, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Menu", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (showAppsMenu) {
                    Popup(alignment = Alignment.TopEnd, onDismissRequest = { showAppsMenu = false }, offset = IntOffset(20, -320)) {
                        Column(modifier = Modifier.width(220.dp).background(Color(0xFF222222), shape = RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("SYSTEM CONTROLS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            AppMenuItem(Icons.Default.Apps, "All Apps", false) { onOpenApps(); showAppsMenu = false }
                            AppMenuItem(Icons.Default.Settings, "Settings", false) { onOpenSettings(); showAppsMenu = false }
                            AppMenuItem(Icons.Default.NightsStay, "Night Panel", isNightPanel) { onNightPanelToggle(); showAppsMenu = false }
                            Button(onClick = { showAppsMenu = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black), modifier = Modifier.fillMaxWidth()) { Text("CLOSE", color = Color.White) }
                        }
                    }
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(Color.Black)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(dockAlpha),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                WideMusicPlayer(modifier = Modifier.fillMaxWidth(), isLandscape = false)
            }

            Box {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showAppsMenu = !showAppsMenu }.padding(8.dp)) {
                    Icon(Icons.Default.Menu, "Menu", tint = if (showAppsMenu) Color.Cyan else Color.White, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Menu", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (showAppsMenu) {
                    Popup(alignment = Alignment.TopEnd, onDismissRequest = { showAppsMenu = false }, offset = IntOffset(0, -320)) {
                        Column(modifier = Modifier.width(220.dp).background(Color(0xFF222222), shape = RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("SYSTEM CONTROLS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            AppMenuItem(Icons.Default.Apps, "All Apps", false) { onOpenApps(); showAppsMenu = false }
                            AppMenuItem(Icons.Default.Settings, "Settings", false) { onOpenSettings(); showAppsMenu = false }
                            AppMenuItem(Icons.Default.NightsStay, "Night Panel", isNightPanel) { onNightPanelToggle(); showAppsMenu = false }
                            Button(onClick = { showAppsMenu = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black), modifier = Modifier.fillMaxWidth()) { Text("CLOSE", color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppMenuItem(icon: ImageVector, label: String, isActive: Boolean = false, onClick: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp)) {
        Icon(icon, null, tint = if (isActive) Color.Cyan else Color.White, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, color = if (isActive) Color.Cyan else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GearSelector(currentGear: String, onGearSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(color = Color(0xFF1E1E1E).copy(alpha = 0.9f)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val isP = currentGear == "P"
        Box(modifier = Modifier.size(48.dp).background(color = if (isP) Color.Red.copy(alpha = 0.8f) else Color(0xFF333333), shape = RoundedCornerShape(12.dp)).clickable { onGearSelected("P") }, contentAlignment = Alignment.Center) {
            Text("P", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF111111), shape = RoundedCornerShape(12.dp)).pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { }, onVerticalDrag = { change, dragAmount -> if (dragAmount < -10) { onGearSelected("R") } else if (dragAmount > 10) { onGearSelected("D") }; change.consume() }) }, contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp)) {
                Text("R", fontSize = if (currentGear == "R") 24.sp else 20.sp, fontWeight = FontWeight.Bold, color = if (currentGear == "R") Color.White else Color.Gray)
                Icon(Icons.Default.UnfoldMore, null, tint = Color.DarkGray, modifier = Modifier.size(24.dp))
                Text("D", fontSize = if (currentGear == "D") 24.sp else 20.sp, fontWeight = FontWeight.Bold, color = if (currentGear == "D") Color(0xFF00FF00) else Color.Gray)
            }
        }
    }
}