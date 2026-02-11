package com.launchers.teslalauncherv2

import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.widget.UVCCameraTextureView

@Composable
fun ReverseCameraScreen() {
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf("Initializing...") }
    var isRunning by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        USBCameraView(refreshTrigger = refreshTrigger, onStatusChange = { running, msg -> isRunning = running; statusMessage = msg })

        if (!isRunning) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = statusMessage, color = if (statusMessage.contains("Error") || statusMessage.contains("DENIED")) Color.Red else Color.Gray, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { refreshTrigger++ }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                    Icon(Icons.Default.Refresh, "Retry"); Spacer(modifier = Modifier.width(8.dp)); Text("CONNECT USB")
                }
            }
        }
        GuideLinesOverlay()
    }
}

@Composable
fun USBCameraView(refreshTrigger: Int, onStatusChange: (Boolean, String) -> Unit) {
    val context = LocalContext.current
    var mUVCCamera by remember { mutableStateOf<UVCCamera?>(null) }
    var mPreviewSurface by remember { mutableStateOf<SurfaceTexture?>(null) }

    // !!! POUŽÍVÁME CustomUSBMonitor (To je důležité) !!!
    val usbMonitor = remember {
        CustomUSBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) { onStatusChange(false, "USB Detected...") }
            override fun onConnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {
                val camera = UVCCamera()
                try {
                    camera.open(ctrlBlock)
                    try { camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG) }
                    catch (e: Exception) { try { camera.setPreviewSize(640, 480, UVCCamera.FRAME_FORMAT_MJPEG) } catch(_: Exception) {} }
                    if (mPreviewSurface != null) {
                        camera.setPreviewTexture(mPreviewSurface); camera.startPreview(); mUVCCamera = camera; onStatusChange(true, "LIVE")
                    }
                } catch (e: Exception) { onStatusChange(false, "Camera Error: ${e.message}"); try { camera.close() } catch (_: Exception) {} }
            }
            override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) { mUVCCamera?.close(); onStatusChange(false, "DISCONNECTED") }
            override fun onDettach(device: UsbDevice?) { onStatusChange(false, "USB REMOVED") }
            override fun onCancel(device: UsbDevice?) { onStatusChange(false, "PERMISSION DENIED") }
        })
    }

    LaunchedEffect(refreshTrigger) {
        try {
            if (!usbMonitor.isRegistered()) usbMonitor.register()
            val devices = usbMonitor.getDeviceList()
            if (devices.isEmpty()) onStatusChange(false, "NO USB DEVICE FOUND")
            else { val device = devices[0]; onStatusChange(false, "Requesting: ${device.deviceName}"); usbMonitor.requestPermission(device) }
        } catch (e: Exception) { onStatusChange(false, "Init Error: ${e.message}") }
    }

    DisposableEffect(Unit) { onDispose { try { mUVCCamera?.close(); mUVCCamera = null; usbMonitor.unregister() } catch (e: Exception) { e.printStackTrace() } } }

    AndroidView(factory = { ctx -> UVCCameraTextureView(ctx).apply {
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) { mPreviewSurface = surface }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { mUVCCamera?.close(); mPreviewSurface = null; return true }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }}, modifier = Modifier.fillMaxSize())
}

@Composable
fun GuideLinesOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val bottomWidth = w * 0.6f; val topWidth = w * 0.4f; val endY = h * 0.3f
        val leftStart = (w - bottomWidth) / 2; val rightStart = leftStart + bottomWidth
        val leftEnd = (w - topWidth) / 2; val rightEnd = leftEnd + topWidth
        fun drawZone(y1: Float, y2: Float, color: Color) {
            val path = Path().apply {
                val f1 = (h - y1) / (h - endY); val f2 = (h - y2) / (h - endY)
                val l1 = leftStart + (leftEnd - leftStart) * f1; val r1 = rightStart + (rightEnd - rightStart) * f1
                val l2 = leftStart + (leftEnd - leftStart) * f2; val r2 = rightStart + (rightEnd - rightStart) * f2
                moveTo(l1, y1); lineTo(l2, y2); moveTo(r1, y1); lineTo(r2, y2); moveTo(l1, y1); lineTo(r1, y1)
            }
            drawPath(path, color, style = Stroke(width = 5.dp.toPx()))
        }
        drawZone(h, h * 0.85f, Color.Red); drawZone(h * 0.85f, h * 0.65f, Color.Yellow); drawZone(h * 0.65f, endY, Color.Green)
    }
}