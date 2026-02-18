package com.launchers.teslalauncherv2.hardware

import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.widget.UVCCameraTextureView

// Fullscreen layout invoked when the vehicle shifts into Reverse (R)
@Composable
fun ReverseCameraScreen() {
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf("Initializing...") }
    var isRunning by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        USBCameraView(refreshTrigger = refreshTrigger, onStatusChange = { running, msg -> isRunning = running; statusMessage = msg })

        // Shows connection status and retry button if feed fails
        if (!isRunning) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = statusMessage, color = if (statusMessage.contains("Error") || statusMessage.contains("DENIED")) Color.Red else Color.Gray, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { refreshTrigger++ }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                    Icon(Icons.Default.Refresh, "Retry"); Spacer(modifier = Modifier.width(8.dp)); Text("RETRY CONNECTION")
                }
            }
        }
        GuideLinesOverlay()
    }
}

// Low-level component bridging libuvc hardware rendering with Jetpack Compose
@Composable
fun USBCameraView(refreshTrigger: Int, onStatusChange: (Boolean, String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mUVCCamera by remember { mutableStateOf<UVCCamera?>(null) }
    var mPreviewSurface by remember { mutableStateOf<SurfaceTexture?>(null) }
    var hasRequestedPermission by remember { mutableStateOf(false) }

    // --- SAFE CAMERA TEARDOWN ---
    // Critical to prevent native C++ crashes within libuvc when hardware drops out
    fun releaseCamera() {
        try {
            mUVCCamera?.stopPreview()
        } catch (e: Exception) {} // Ignore crashes if device abruptly disconnected
        try {
            mUVCCamera?.close()
        } catch (e: Exception) {}
        mUVCCamera = null
    }

    // Connects the initialized camera hardware to the Android SurfaceTexture
    fun startPreviewSafely() {
        val camera = mUVCCamera
        val surface = mPreviewSurface
        if (camera != null && surface != null) {
            try {
                camera.setPreviewTexture(surface)
                camera.startPreview()
                onStatusChange(true, "")
            } catch (e: Exception) {
                Log.e("CameraUtils", "Preview Failed: ${e.message}")
                onStatusChange(false, "Preview Error")
                releaseCamera() // Clean up immediately on failure
            }
        }
    }

    val usbMonitor = remember {
        CustomUSBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {
                onStatusChange(false, "USB Detected...")
            }

            override fun onConnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {
                if (mUVCCamera != null) return

                val camera = UVCCamera()
                try {
                    camera.open(ctrlBlock)
                    // Attempt HD resolution, fallback to SD if unsupported
                    try { camera.setPreviewSize(1280, 720, UVCCamera.FRAME_FORMAT_MJPEG) } catch (_: Exception) {
                        try { camera.setPreviewSize(640, 480, UVCCamera.FRAME_FORMAT_MJPEG) } catch (_: Exception) {}
                    }
                    mUVCCamera = camera
                    startPreviewSafely()
                } catch (e: Exception) {
                    onStatusChange(false, "Open Error: ${e.message}")
                    try { camera.close() } catch (_: Exception) {}
                }
            }

            override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                releaseCamera()
                onStatusChange(false, "DISCONNECTED")
                hasRequestedPermission = false
            }

            override fun onDettach(device: UsbDevice?) {
                releaseCamera()
                onStatusChange(false, "USB REMOVED")
                hasRequestedPermission = false
            }

            override fun onCancel(device: UsbDevice?) {
                onStatusChange(false, "PERMISSION DENIED")
                hasRequestedPermission = false
            }
        })
    }

    // Workaround for specific head units: Verify USB connection on app resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val devices = usbMonitor.getDeviceList()
                        if (devices.isNotEmpty()) {
                            val device = devices[0]
                            if (usbMonitor.hasPermission(device) && mUVCCamera == null) {
                                onStatusChange(false, "Connecting...")
                                usbMonitor.requestPermission(device)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CameraUtils", "Resume Check Failed", e)
                    }
                }, 500)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Initial hardware scan and permission request trigger
    LaunchedEffect(refreshTrigger) {
        try {
            if (!usbMonitor.isRegistered()) usbMonitor.register()
            val devices = usbMonitor.getDeviceList()
            if (devices.isNotEmpty()) {
                val device = devices[0]
                if (usbMonitor.hasPermission(device)) {
                    usbMonitor.requestPermission(device)
                } else if (!hasRequestedPermission) {
                    onStatusChange(false, "Requesting Permission...")
                    hasRequestedPermission = true
                    usbMonitor.requestPermission(device)
                }
            } else {
                onStatusChange(false, "NO USB DEVICE")
                hasRequestedPermission = false
            }
        } catch (e: Exception) {
            onStatusChange(false, "Init Error: ${e.message}")
        }
    }

    // Disconnect resources when navigating away from the reverse view
    DisposableEffect(Unit) {
        onDispose {
            releaseCamera()
            try { usbMonitor.unregister() } catch (_: Exception) {}
        }
    }

    // Renders the actual raw video feed inside Compose via AndroidView
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                UVCCameraTextureView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            mPreviewSurface = surface
                            startPreviewSafely()
                        }
                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            releaseCamera()
                            mPreviewSurface = null
                            return true
                        }
                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// Draws static distance markers (Red, Yellow, Green) over the camera feed
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

        drawZone(h, h * 0.85f, Color.Red)
        drawZone(h * 0.85f, h * 0.65f, Color.Yellow)
        drawZone(h * 0.65f, endY, Color.Green)
    }
}