package com.launchers.teslalauncherv2

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border // <--- TENTO IMPORT CHYBĚL
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import java.util.Locale

// --- POMOCNÁ FUNKCE: Formátování vzdálenosti ---
fun formatDistance(meters: Int): String {
    return if (meters >= 1000) {
        String.format(Locale.US, "%.1f km", meters / 1000f)
    } else {
        "$meters m"
    }
}

// --- POMOCNÁ FUNKCE: Ovládání médií ---
fun sendMediaKeyEvent(context: Context, keyCode: Int) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
    val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
    audioManager.dispatchMediaKeyEvent(eventDown)
    audioManager.dispatchMediaKeyEvent(eventUp)
}

// --- 1. INSTRUMENT CLUSTER ---
@Composable
fun InstrumentCluster(
    modifier: Modifier = Modifier,
    instruction: NavInstruction? = null,
    speed: Int = 0,
    battery: Int = 80,
    isDemoMode: Boolean = false,
    gpsStatus: String? = null,
    onDemoToggle: () -> Unit = {}
) {
    val animatedSpeed by animateIntAsState(targetValue = speed, animationSpec = tween(500), label = "Speed")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // LEVÁ ČÁST (Navigace nebo GPS Warning)
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            Column {
                // --- VAROVÁNÍ GPS ---
                if (gpsStatus != null && !isDemoMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFF330000), shape = RoundedCornerShape(8.dp)) // Tmavě červené pozadí
                            .border(1.dp, Color.Red, RoundedCornerShape(8.dp)) // Červený rámeček
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.SatelliteAlt, "No GPS", tint = Color.Red, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = gpsStatus,
                            color = Color.Red,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Navigace
                if (instruction != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when {
                            instruction.modifier?.contains("left") == true -> Icons.AutoMirrored.Filled.ArrowBack
                            instruction.modifier?.contains("right") == true -> Icons.AutoMirrored.Filled.ArrowForward
                            instruction.modifier?.contains("uturn") == true -> Icons.Default.Refresh
                            else -> Icons.Default.ArrowUpward
                        }
                        Icon(icon, null, tint = Color.Cyan, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = formatDistance(instruction.distance),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(instruction.text, color = Color.LightGray, fontSize = 16.sp, maxLines = 2, modifier = Modifier.widthIn(max = 200.dp))
                } else if (gpsStatus == null) {
                    // Hodiny
                    Text("12:00", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                }
            }
        }

        // STŘED (Tachometr)
        val alignmentBias by animateFloatAsState(targetValue = if (instruction != null || gpsStatus != null) 0.5f else 0.0f, animationSpec = tween(1000), label = "Shift")
        Column(
            modifier = Modifier
                .align(BiasAlignment(alignmentBias, 0f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDemoToggle() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$animatedSpeed", fontSize = 90.sp, fontWeight = FontWeight.Bold, color = if (isDemoMode) Color.Yellow else Color.White)
            Text("KM/H", fontSize = 18.sp, color = Color.Gray)
        }

        // PRAVÁ ČÁST (Baterie)
        Row(modifier = Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            val batteryColor = if (battery > 20) Color.Green else Color.Red
            Text("$battery%", fontSize = 24.sp, color = batteryColor, modifier = Modifier.padding(end = 6.dp))
            Icon(if (battery > 20) Icons.Default.BatteryFull else Icons.Default.BatteryAlert, "Battery", tint = batteryColor, modifier = Modifier.size(32.dp))
        }
    }
}

// --- 2. NIGHT PANEL SCREEN (Zenový režim) ---
@Composable
fun NightPanelScreen(
    speed: Int,
    instruction: NavInstruction?,
    onExit: () -> Unit
) {
    val animatedSpeed by animateIntAsState(targetValue = speed, animationSpec = tween(500), label = "Speed")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onExit() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (instruction != null) {
                val isNear = instruction.distance < 2000

                if (isNear) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        val icon = when {
                            instruction.modifier?.contains("left") == true -> Icons.AutoMirrored.Filled.ArrowBack
                            instruction.modifier?.contains("right") == true -> Icons.AutoMirrored.Filled.ArrowForward
                            instruction.modifier?.contains("uturn") == true -> Icons.Default.Refresh
                            else -> Icons.Default.ArrowUpward
                        }
                        Icon(icon, null, tint = Color.Cyan, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.width(24.dp))
                        Text(
                            text = formatDistance(instruction.distance),
                            color = Color.Cyan,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "Next turn in ${formatDistance(instruction.distance)}",
                        color = Color.Gray,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 48.dp)
                    )
                }
            }

            Text(
                text = "$animatedSpeed",
                color = Color(0xFFE0E0E0),
                fontSize = 140.sp,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "km/h",
                color = Color.DarkGray,
                fontSize = 24.sp
            )
        }

        Text(
            text = "Tap screen to wake",
            color = Color(0xFF333333),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}


// --- 3. WIDE MUSIC PLAYER ---
@Composable
fun WideMusicPlayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxHeight()
                .background(Color.DarkGray, shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Bluetooth Audio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
            Text("Tap to play", color = Color.Gray, fontSize = 14.sp, maxLines = 1)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = { sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipPrevious, "Prev", tint = Color.White, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { isPlaying = !isPlaying; sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, modifier = Modifier.size(64.dp)) {
                Icon(if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, "Play", tint = Color.Cyan, modifier = Modifier.fillMaxSize())
            }
            IconButton(onClick = { sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }
}

// --- 4. DOCK ---
@Composable
fun Dock(
    modifier: Modifier = Modifier,
    isNightPanel: Boolean = false,
    onNightPanelToggle: () -> Unit = {}
) {
    var showAppsMenu by remember { mutableStateOf(false) }
    val dockAlpha by animateFloatAsState(targetValue = if (isNightPanel) 0.5f else 1.0f, label = "DimDock")

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
        Box(modifier = Modifier.weight(1f)) { WideMusicPlayer(modifier = Modifier.fillMaxWidth()) }
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { showAppsMenu = !showAppsMenu }.padding(8.dp)
            ) {
                Icon(Icons.Default.Apps, "Apps", tint = if (showAppsMenu) Color.Cyan else Color.White, modifier = Modifier.size(48.dp))
                Text("Apps", color = Color.Gray, fontSize = 12.sp)
            }
            if (showAppsMenu) {
                Popup(alignment = Alignment.TopCenter, onDismissRequest = { showAppsMenu = false }, offset = androidx.compose.ui.unit.IntOffset(0, -450)) {
                    Column(
                        modifier = Modifier.width(220.dp).background(Color(0xFF222222), shape = RoundedCornerShape(16.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("CONTROLS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        AppMenuItem(Icons.Default.NightsStay, "Night Panel", isNightPanel) { onNightPanelToggle(); showAppsMenu = false }
                        HorizontalDivider(color = Color.Gray, thickness = 0.5.dp)
                        AppMenuItem(Icons.Default.DirectionsCar, "Vehicle Settings")
                        AppMenuItem(Icons.Default.Map, "Saved Places")
                        AppMenuItem(Icons.Default.Settings, "System Settings")
                        Button(onClick = { showAppsMenu = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black), modifier = Modifier.fillMaxWidth()) { Text("CLOSE", color = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
fun AppMenuItem(icon: ImageVector, label: String, isActive: Boolean = false, onClick: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp)) {
        Icon(icon, null, tint = if (isActive) Color.Cyan else Color.White, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = if (isActive) Color.Cyan else Color.White, fontSize = 16.sp)
    }
}

// --- 5. GEAR SELECTOR (Tesla Slider Style) ---
@Composable
fun GearSelector(
    currentGear: String,
    onGearSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight(0.6f)
            .background(
                color = Color(0xFF222222).copy(alpha = 0.9f),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
            )
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        val gears = listOf("P", "R", "N", "D")

        gears.forEach { gear ->
            val isSelected = gear == currentGear

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = if (isSelected) Color(0xFF3E3E3E) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onGearSelected(gear) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = gear,
                    fontSize = if (isSelected) 28.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                        when (gear) {
                            "P" -> Color.Red
                            "R" -> Color.White
                            "N" -> Color.White
                            "D" -> Color(0xFF00FF00)
                            else -> Color.White
                        }
                    } else Color.Gray
                )
            }
        }
    }
}