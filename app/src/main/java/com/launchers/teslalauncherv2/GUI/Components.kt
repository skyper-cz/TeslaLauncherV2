package com.launchers.teslalauncherv2.GUI

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.launchers.teslalauncherv2.data.CarState
import com.launchers.teslalauncherv2.data.NavInstruction
import java.util.Locale

// --- POMOCNÉ FUNKCE ---
fun formatDistance(meters: Int): String {
    return if (meters >= 1000) String.format(Locale.US, "%.1f km", meters / 1000f) else "$meters m"
}

fun sendMediaKeyEvent(context: Context, keyCode: Int) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
}

// --- 1. INSTRUMENT CLUSTER (Optimalizovaný - rozdělený na části) ---
@Composable
fun InstrumentCluster(
    modifier: Modifier = Modifier,
    carState: CarState,
    gpsStatus: String?,
    batteryLevel: Int,
    instruction: NavInstruction?
) {
    // Hlavní kontejner - překreslí se jen při změně rozměrů
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // A. Horní lišta (Statusy, Navigace, RPM)
        // Posíláme jen konkrétní parametry, aby se nepřekreslovalo vše
        TopStatusSection(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            gpsStatus = gpsStatus,
            isConnected = carState.isConnected,
            isDemoMode = carState.isDemoMode,
            rpm = carState.rpm,
            coolantTemp = carState.coolantTemp,
            batteryLevel = batteryLevel,
            instruction = instruction
        )

        // B. Rychloměr (Uprostřed dole)
        SpeedometerSection(
            modifier = Modifier.align(Alignment.Center).padding(top = 50.dp),
            speed = carState.speed
        )
    }
}

// --- PODKOMPONENTA: Horní Lišta (Statusy + RPM + Navigace) ---
@Composable
fun TopStatusSection(
    modifier: Modifier,
    gpsStatus: String?,
    isConnected: Boolean,
    isDemoMode: Boolean,
    rpm: Int,
    coolantTemp: Int,
    batteryLevel: Int,
    instruction: NavInstruction?
) {
    Box(modifier = modifier) {
        // 1. LEVÁ STRANA
        Row(modifier = Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            if (gpsStatus != null) {
                Text(
                    text = gpsStatus,
                    color = Color.White,
                    modifier = Modifier.background(if (gpsStatus == "SEARCHING...") Color.Red else Color.DarkGray, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (isConnected) Icon(Icons.Default.BluetoothConnected, "OBD", tint = Color.Green, modifier = Modifier.size(24.dp))
            else if (isDemoMode) Text("DEMO", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        // 2. STŘED (Navigace + RPM)
        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.6f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (instruction != null) {
                NavigationDisplay(instruction)
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }
            // RPM Lišta (vyčleněná, aby neblikala celá obrazovka)
            RpmBar(rpm = rpm)
        }

        // 3. PRAVÁ STRANA
        Row(modifier = Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            if (coolantTemp > 0) {
                Text(text = "$coolantTemp°C", color = if (coolantTemp >= 105) Color.Red else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(text = "$batteryLevel%", color = if (batteryLevel < 20) Color.Red else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(if (batteryLevel > 20) Icons.Default.BatteryFull else Icons.Default.BatteryAlert, "Bat", tint = if (batteryLevel < 20) Color.Red else Color.Green, modifier = Modifier.size(24.dp))
        }
    }
}

// --- PODKOMPONENTA: Navigace ---
@Composable
fun NavigationDisplay(instruction: NavInstruction) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val icon = when {
            instruction.modifier?.contains("left") == true -> Icons.AutoMirrored.Filled.ArrowBack
            instruction.modifier?.contains("right") == true -> Icons.AutoMirrored.Filled.ArrowForward
            else -> Icons.Default.Navigation
        }
        Icon(icon, "Nav", tint = Color.Cyan, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = formatDistance(instruction.distance), color = Color.Cyan, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
    Text(text = instruction.text, color = Color.White, fontSize = 16.sp, maxLines = 1, fontWeight = FontWeight.Medium)
}

// --- PODKOMPONENTA: RPM Lišta (Kreslí se jen při změně RPM) ---
@Composable
fun RpmBar(rpm: Int) {
    if (rpm > 0) {
        val isRedline = rpm >= 5500
        val progress = (rpm / 7000f).coerceIn(0f, 1f)

        Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            drawRect(color = Color.DarkGray, size = size)
            val barWidth = size.width * progress
            val barColor = if (isRedline) Color.Red else Color.White

            drawRect(
                color = barColor,
                topLeft = Offset((size.width - barWidth) / 2, 0f),
                size = size.copy(width = barWidth)
            )
        }
        Text(text = "$rpm", color = if (isRedline) Color.Red else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}

// --- PODKOMPONENTA: Rychloměr ---
@Composable
fun SpeedometerSection(modifier: Modifier, speed: Int) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$speed",
            fontSize = 110.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = (-4).sp
        )
        Text(text = "KM/H", fontSize = 16.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
    }
}

// --- ZBYTEK KÓDU ZŮSTÁVÁ BEZ ZMĚN (NightPanel, Dock, GearSelector...) ---
// (Zkopíruj sem zbytek souboru, který už jsi měl, ten je v pořádku)

@Composable
fun NightPanelScreen(speed: Int, instruction: NavInstruction?, onExit: () -> Unit) {
    val animatedSpeed by animateIntAsState(targetValue = speed, animationSpec = tween(500), label = "Speed")
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { onExit() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (instruction != null) {
                Text(text = formatDistance(instruction.distance), color = Color.Cyan, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = "$animatedSpeed", color = Color(0xFFE0E0E0), fontSize = 140.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = "km/h", color = Color.DarkGray, fontSize = 24.sp)
        }
    }
}

@Composable
fun WideMusicPlayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    Row(modifier = modifier.fillMaxHeight().background(Color(0xFF1A1A1A), shape = RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.aspectRatio(1f).fillMaxHeight().background(Color.DarkGray, shape = RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("Bluetooth Audio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
            Text("Tap to play", color = Color.Gray, fontSize = 14.sp, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = { sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.SkipPrevious, "Prev", tint = Color.White, modifier = Modifier.size(40.dp)) }
            IconButton(onClick = { isPlaying = !isPlaying; sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, modifier = Modifier.size(64.dp)) { Icon(if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, "Play", tint = Color.Cyan, modifier = Modifier.fillMaxSize()) }
            IconButton(onClick = { sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(40.dp)) }
        }
    }
}

@Composable
fun Dock(modifier: Modifier = Modifier, isNightPanel: Boolean = false, onNightPanelToggle: () -> Unit = {}) {
    var showAppsMenu by remember { mutableStateOf(false) }
    val dockAlpha by animateFloatAsState(targetValue = if (isNightPanel) 0.5f else 1.0f, label = "DimDock")
    Row(modifier = modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).alpha(dockAlpha), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) { WideMusicPlayer(modifier = Modifier.fillMaxWidth()) }
        Box {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showAppsMenu = !showAppsMenu }.padding(8.dp)) {
                Icon(Icons.Default.Apps, "Apps", tint = if (showAppsMenu) Color.Cyan else Color.White, modifier = Modifier.size(48.dp)); Text("Apps", color = Color.Gray, fontSize = 12.sp)
            }
            if (showAppsMenu) {
                Popup(alignment = Alignment.TopCenter, onDismissRequest = { showAppsMenu = false }, offset = IntOffset(0, -450)) {
                    Column(modifier = Modifier.width(220.dp).background(Color(0xFF222222), shape = RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("CONTROLS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        AppMenuItem(Icons.Default.NightsStay, "Night Panel", isNightPanel) { onNightPanelToggle(); showAppsMenu = false }
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
        Icon(icon, null, tint = if (isActive) Color.Cyan else Color.White, modifier = Modifier.size(32.dp)); Spacer(modifier = Modifier.width(12.dp)); Text(label, color = if (isActive) Color.Cyan else Color.White, fontSize = 16.sp)
    }
}

@Composable
fun GearSelector(currentGear: String, onGearSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(72.dp).fillMaxHeight(0.6f).background(color = Color(0xFF222222).copy(alpha = 0.9f), shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        val gears = listOf("P", "R", "N", "D")
        gears.forEach { gear ->
            val isSelected = gear == currentGear
            Box(modifier = Modifier.size(56.dp).background(color = if (isSelected) Color(0xFF3E3E3E) else Color.Transparent, shape = RoundedCornerShape(12.dp)).clickable { onGearSelected(gear) }, contentAlignment = Alignment.Center) {
                Text(text = gear, fontSize = if (isSelected) 28.sp else 22.sp, fontWeight = FontWeight.Bold, color = if (isSelected) { when (gear) { "P" -> Color.Red; "R" -> Color.White; "N" -> Color.White; "D" -> Color(0xFF00FF00); else -> Color.White } } else Color.Gray)
            }
        }
    }
}