package com.launchers.teslalauncherv2.hardware

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ReverseCameraScreen(isReverseGear: Boolean) {
    val refreshTrigger = remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf("Initializing UVC Engine...") }
    var isRunning by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Jádro kamery běží VŽDY a zachycuje USB připojení
        USBCameraView(
            refreshTriggerState = refreshTrigger,
            onStatusChange = { running, msg ->
                isRunning = running
                statusMessage = msg
            }
        )

        // Uživatelské rozhraní se kreslí, jen když je kamera fyzicky na očích (isReverseGear == true)
        if (isReverseGear) {
            if (!isRunning) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)))

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = statusMessage,
                        color = if (statusMessage.contains("Error") || statusMessage.contains("Denied")) Color.Red else Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        Log.d("UVC_CAMERA_DEBUG", "User triggered manual reconnection")
                        refreshTrigger.intValue++
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                        Icon(Icons.Default.Refresh, "Retry")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RETRY CONNECTION")
                    }
                }
            }
            GuideLinesOverlay()
        }
    }
}

@Composable
fun GuideLinesOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val bottomWidth = w * 0.6f
        val topWidth = w * 0.4f
        val endY = h * 0.3f
        val leftStart = (w - bottomWidth) / 2
        val rightStart = leftStart + bottomWidth
        val leftEnd = (w - topWidth) / 2
        val rightEnd = leftEnd + topWidth

        fun drawZone(y1: Float, y2: Float, color: Color) {
            val path = Path().apply {
                val f1 = (h - y1) / (h - endY)
                val f2 = (h - y2) / (h - endY)
                val l1 = leftStart + (leftEnd - leftStart) * f1
                val r1 = rightStart + (rightEnd - rightStart) * f1
                val l2 = leftStart + (leftEnd - leftStart) * f2
                val r2 = rightStart + (rightEnd - rightStart) * f2
                moveTo(l1, y1); lineTo(l2, y2)
                moveTo(r1, y1); lineTo(r2, y2)
                moveTo(l1, y1); lineTo(r1, y1)
            }
            drawPath(path, color, style = Stroke(width = 5.dp.toPx()))
        }

        drawZone(h, h * 0.85f, Color.Red)
        drawZone(h * 0.85f, h * 0.65f, Color.Yellow)
        drawZone(h * 0.65f, endY, Color.Green)
    }
}