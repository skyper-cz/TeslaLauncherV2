package com.launchers.teslalauncherv2

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InstrumentCluster(
    modifier: Modifier = Modifier,
    instruction: NavInstruction? = null,
    speed: Int = 0,
    battery: Int = 80,
    isDemoMode: Boolean = false,
    onDemoToggle: () -> Unit = {}
) {
    val animatedSpeed by animateIntAsState(
        targetValue = speed,
        animationSpec = tween(durationMillis = 500),
        label = "SpeedAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // 1. LEVÁ ČÁST
        Box(modifier = Modifier.align(Alignment.CenterStart), contentAlignment = Alignment.CenterStart) {
            if (instruction != null) {
                Column(horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when {
                            instruction.modifier?.contains("left") == true -> Icons.AutoMirrored.Filled.ArrowBack
                            instruction.modifier?.contains("right") == true -> Icons.AutoMirrored.Filled.ArrowForward
                            instruction.modifier?.contains("uturn") == true -> Icons.Default.Refresh
                            else -> Icons.Default.ArrowUpward
                        }
                        Icon(icon, null, tint = Color.Cyan, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${instruction.distance} m", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(instruction.text, color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, modifier = Modifier.widthIn(max = 200.dp))
                }
            } else {
                Text("12:00", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            }
        }

        // 2. STŘED
        val alignmentBias by animateFloatAsState(targetValue = if (instruction != null) 0.5f else 0.0f, animationSpec = tween(1000), label = "SpeedometerShift")
        Column(
            modifier = Modifier
                .align(BiasAlignment(alignmentBias, 0f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDemoToggle() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$animatedSpeed", fontSize = 80.sp, fontWeight = FontWeight.Bold, color = if (isDemoMode) Color.Yellow else Color.White)
            Text("KM/H", fontSize = 16.sp, color = Color.Gray)
        }

        // 3. PRAVÁ ČÁST
        Row(modifier = Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            val batteryColor = if (battery > 20) Color.Green else Color.Red
            Text("$battery%", fontSize = 20.sp, color = batteryColor, modifier = Modifier.padding(end = 4.dp))
            Icon(if (battery > 20) Icons.Default.BatteryFull else Icons.Default.BatteryAlert, "Battery", tint = batteryColor)
        }

        if (instruction == null) {
            Icon(Icons.Default.DirectionsCar, "Car Status", tint = Color.DarkGray, modifier = Modifier.align(Alignment.BottomCenter).size(32.dp))
        }
    }
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