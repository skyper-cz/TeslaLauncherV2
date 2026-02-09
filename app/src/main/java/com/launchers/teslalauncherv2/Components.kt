package com.launchers.teslalauncherv2

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup

// --- POMOCNÁ FUNKCE ---
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
        // LEVÁ ČÁST
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            if (instruction != null) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when {
                            instruction.modifier?.contains("left") == true -> Icons.AutoMirrored.Filled.ArrowBack
                            instruction.modifier?.contains("right") == true -> Icons.AutoMirrored.Filled.ArrowForward
                            instruction.modifier?.contains("uturn") == true -> Icons.Default.Refresh
                            else -> Icons.Default.ArrowUpward
                        }
                        Icon(icon, null, tint = Color.Cyan, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("${instruction.distance} m", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(instruction.text, color = Color.LightGray, fontSize = 16.sp, maxLines = 2, modifier = Modifier.widthIn(max = 200.dp))
                }
            } else {
                Text("12:00", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            }
        }

        // STŘED
        val alignmentBias by animateFloatAsState(targetValue = if (instruction != null) 0.5f else 0.0f, animationSpec = tween(1000), label = "Shift")
        Column(
            modifier = Modifier
                .align(BiasAlignment(alignmentBias, 0f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDemoToggle() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$animatedSpeed", fontSize = 90.sp, fontWeight = FontWeight.Bold, color = if (isDemoMode) Color.Yellow else Color.White)
            Text("KM/H", fontSize = 18.sp, color = Color.Gray)
        }

        // PRAVÁ ČÁST
        Row(modifier = Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            val batteryColor = if (battery > 20) Color.Green else Color.Red
            Text("$battery%", fontSize = 24.sp, color = batteryColor, modifier = Modifier.padding(end = 6.dp))
            Icon(if (battery > 20) Icons.Default.BatteryFull else Icons.Default.BatteryAlert, "Battery", tint = batteryColor, modifier = Modifier.size(32.dp))
        }
    }
}

// --- 2. WIDE MUSIC PLAYER (Velký) ---
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
        // A) Obal Alba
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

        // B) Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Bluetooth Audio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
            Text("Tap to play", color = Color.Gray, fontSize = 14.sp, maxLines = 1)
        }

        // C) Ovládání
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = { sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.SkipPrevious, "Prev", tint = Color.White, modifier = Modifier.size(40.dp))
            }

            IconButton(
                onClick = {
                    isPlaying = !isPlaying
                    sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = "Play",
                    tint = Color.Cyan,
                    modifier = Modifier.fillMaxSize()
                )
            }

            IconButton(
                onClick = { sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT) },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }
}

// --- 3. DOCK (S Menu a Night Panelem) ---
@Composable
fun Dock(
    modifier: Modifier = Modifier,
    isNightPanel: Boolean = false,
    onNightPanelToggle: () -> Unit = {}
) {
    var showAppsMenu by remember { mutableStateOf(false) }

    // Ztlumení Docku v Night Panelu
    val dockAlpha by animateFloatAsState(targetValue = if (isNightPanel) 0.5f else 1.0f, label = "DimDock")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding() // Oprava překrývání spodní lištou!
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(dockAlpha),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. HUDBA
        Box(modifier = Modifier.weight(1f)) {
            WideMusicPlayer(modifier = Modifier.fillMaxWidth())
        }

        // 2. TLAČÍTKO "APPS"
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { showAppsMenu = !showAppsMenu }
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "Apps",
                    tint = if (showAppsMenu) Color.Cyan else Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Text("Apps", color = Color.Gray, fontSize = 12.sp)
            }

            // 3. MENU
            if (showAppsMenu) {
                Popup(
                    alignment = Alignment.TopCenter,
                    onDismissRequest = { showAppsMenu = false },
                    offset = androidx.compose.ui.unit.IntOffset(0, -450)
                ) {
                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .background(Color(0xFF222222), shape = RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("CONTROLS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        // Night Panel Tlačítko
                        AppMenuItem(
                            icon = Icons.Default.NightsStay,
                            label = "Night Panel",
                            isActive = isNightPanel,
                            onClick = { onNightPanelToggle(); showAppsMenu = false }
                        )
                        HorizontalDivider(color = Color.Gray, thickness = 0.5.dp)
                        AppMenuItem(Icons.Default.DirectionsCar, "Vehicle Settings")
                        AppMenuItem(Icons.Default.Map, "Saved Places")
                        AppMenuItem(Icons.Default.Settings, "System Settings")

                        Button(
                            onClick = { showAppsMenu = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("CLOSE", color = Color.White) }
                    }
                }
            }
        }
    }
}

// --- 4. APP MENU ITEM (Opravená definice) ---
@Composable
fun AppMenuItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) Color.Cyan else Color.White,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = if (isActive) Color.Cyan else Color.White,
            fontSize = 16.sp
        )
    }
}