package com.launchers.teslalauncherv2.GUI

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.launchers.teslalauncherv2.data.CarState
import com.launchers.teslalauncherv2.data.MediaManager
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

// --- 1. INSTRUMENT CLUSTER (NOVÝ LAYOUT) ---
@Composable
fun InstrumentCluster(
    modifier: Modifier = Modifier,
    carState: CarState,
    gpsStatus: String?,
    batteryLevel: Int,
    instruction: NavInstruction?
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // A. Horní lišta (Statusy fixně nahoře)
        TopStatusBar(
            gpsStatus = gpsStatus,
            isConnected = carState.isConnected,
            isDemoMode = carState.isDemoMode,
            batteryLevel = batteryLevel
        )

        Spacer(modifier = Modifier.weight(1f)) // Pružná mezera

        // B. Hlavní část (Navigace vlevo, Rychlost vpravo)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom // Zarovnání dolů
        ) {
            // LEVÁ ČÁST: Navigace + RPM
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                if (instruction != null) {
                    NavigationDisplay(instruction)
                }
                Spacer(modifier = Modifier.height(16.dp))
                RpmBar(rpm = carState.rpm)
            }

            // PRAVÁ ČÁST: Rychlost
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End // Rychlost zarovnána doprava
            ) {
                if (carState.coolantTemp > 0) {
                    Text(text = "${carState.coolantTemp}°C", color = Color.Gray, fontSize = 14.sp)
                }
                Text(
                    text = "${carState.speed}",
                    fontSize = 90.sp, // Velké číslo
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-2).sp
                )
                Text(text = "KM/H", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// --- PODKOMPONENTA: Horní Lišta (Statusy) ---
@Composable
fun TopStatusBar(
    gpsStatus: String?,
    isConnected: Boolean,
    isDemoMode: Boolean,
    batteryLevel: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Levá strana (GPS, OBD)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (gpsStatus != null) {
                Text(
                    text = gpsStatus,
                    color = Color.White,
                    modifier = Modifier.background(Color.DarkGray, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (isConnected) Icon(Icons.Default.BluetoothConnected, "OBD", tint = Color.Green, modifier = Modifier.size(20.dp))
            else if (isDemoMode) Text("DEMO", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        // Pravá strana (Baterie)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "$batteryLevel%", color = if (batteryLevel < 20) Color.Red else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(if (batteryLevel > 20) Icons.Default.BatteryFull else Icons.Default.BatteryAlert, "Bat", tint = if (batteryLevel < 20) Color.Red else Color.Green, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun NavigationDisplay(instruction: NavInstruction) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = when {
                instruction.modifier?.contains("left") == true -> Icons.AutoMirrored.Filled.ArrowBack
                instruction.modifier?.contains("right") == true -> Icons.AutoMirrored.Filled.ArrowForward
                else -> Icons.Default.Navigation
            }
            Icon(icon, "Nav", tint = Color.Cyan, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = formatDistance(instruction.distance), color = Color.Cyan, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Text(text = instruction.text, color = Color.White, fontSize = 18.sp, maxLines = 2, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RpmBar(rpm: Int) {
    if (rpm > 0) {
        val isRedline = rpm >= 5500
        val progress = (rpm / 7000f).coerceIn(0f, 1f)
        Column(modifier = Modifier.fillMaxWidth(0.8f)) { // Kratší bar
            Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                drawRect(color = Color.DarkGray, size = size)
                val barWidth = size.width * progress
                val barColor = if (isRedline) Color.Red else Color.White
                drawRect(color = barColor, topLeft = Offset(0f, 0f), size = size.copy(width = barWidth)) // Zarovnáno doleva
            }
            Text(text = "$rpm RPM", color = if (isRedline) Color.Red else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// Funkce SpeedometerSection už není potřeba jako samostatná, je integrována v Clusteru
// Ale necháme ji zde pro zpětnou kompatibilitu, pokud by ji volal někdo jiný (nevolá).

@Composable
fun NightPanelScreen(
    speed: Int,
    rpm: Int,
    error: String?,
    instruction: NavInstruction?,
    onExit: () -> Unit
) {
    val animatedSpeed by animateIntAsState(targetValue = speed, animationSpec = tween(500), label = "Speed")

    // Hlavní kontejner - Černé pozadí
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // 1. STŘEDOVÝ OBSAH (Informace)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // A) NAVIGACE (Dynamická logika)
            if (instruction != null) {
                if (instruction.distance > 2000) {
                    // Více než 2 km -> Jen hodnota v km, velmi tmavá šedá
                    Text(
                        text = String.format(Locale.US, "%.1f km", instruction.distance / 1000f),
                        color = Color(0xFF444444),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    // Méně než 2 km -> Metry + Šipka, jasnější (Cyan)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when {
                            instruction.modifier?.contains("left") == true -> Icons.AutoMirrored.Filled.ArrowBack
                            instruction.modifier?.contains("right") == true -> Icons.AutoMirrored.Filled.ArrowForward
                            else -> Icons.Default.Navigation
                        }
                        Icon(icon, null, tint = Color(0xFF008888), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "${instruction.distance} m",
                            color = Color(0xFF00AAAA),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // B) RYCHLOST (Dominantní, ztmavená)
            Text(
                text = "$animatedSpeed",
                color = Color(0xFF888888),
                fontSize = 140.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-5).sp
            )
            Text("km/h", color = Color(0xFF333333), fontSize = 20.sp)

            // C) RPM ALERT (> 3500)
            if (rpm > 3500) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "$rpm RPM",
                    color = Color(0xFFBB0000), // Tmavá červená
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // D) CHYBA (Yellow Alert)
            if (!error.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "⚠ $error",
                    color = Color(0xFFAAAA00), // Tmavá žlutá
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xFF222200), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // 2. TRIGGER ZÓNA (Kliknutí jen dole)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.15f) // Jen spodních 15% obrazovky reaguje
                .background(Color.Transparent)
                .clickable { onExit() }, // Tady je ten exit
            contentAlignment = Alignment.Center
        ) {
            // Jemná nápověda (volitelné, velmi tmavá)
            Text("TAP TO WAKE", color = Color(0xFF111111), fontSize = 10.sp, modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
fun WideMusicPlayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val trackInfo by MediaManager.currentTrack.collectAsState()

    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(16.dp))
            .padding(8.dp)
            .clickable {
                val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                try { context.startActivity(intent) } catch (_: Exception) {}
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxHeight()
                .background(Color.DarkGray, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (trackInfo.albumArt != null) {
                Image(
                    bitmap = trackInfo.albumArt!!.asImageBitmap(),
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.MusicNote, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = trackInfo.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = trackInfo.artist,
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            IconButton(onClick = { sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.SkipPrevious, "Prev", tint = Color.White, modifier = Modifier.size(28.dp))
            }

            IconButton(onClick = {
                sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }, modifier = Modifier.size(48.dp)) {
                Icon(if (trackInfo.isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, "Play", tint = Color.Cyan, modifier = Modifier.fillMaxSize())
            }

            IconButton(onClick = { sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(28.dp))
            }
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
fun GearSelector(
    currentGear: String,
    onGearSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(80.dp)
            .fillMaxHeight(0.5f)
            .background(
                color = Color(0xFF1E1E1E).copy(alpha = 0.9f),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val isP = currentGear == "P"
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    color = if (isP) Color.Red.copy(alpha = 0.8f) else Color(0xFF333333),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { onGearSelected("P") },
            contentAlignment = Alignment.Center
        ) {
            Text("P", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF111111), shape = RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { /* Reset */ },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount < -10) { onGearSelected("R") } else if (dragAmount > 10) { onGearSelected("D") }
                            change.consume()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxHeight().padding(vertical = 24.dp)
            ) {
                Text("R", fontSize = if (currentGear == "R") 32.sp else 24.sp, fontWeight = FontWeight.Bold, color = if (currentGear == "R") Color.White else Color.Gray)
                Icon(Icons.Default.UnfoldMore, null, tint = Color.DarkGray, modifier = Modifier.size(32.dp))
                Text("D", fontSize = if (currentGear == "D") 32.sp else 24.sp, fontWeight = FontWeight.Bold, color = if (currentGear == "D") Color(0xFF00FF00) else Color.Gray)
            }
        }
    }
}