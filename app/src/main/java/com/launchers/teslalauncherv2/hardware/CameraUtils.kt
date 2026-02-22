package com.launchers.teslalauncherv2.hardware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.core.content.ContextCompat
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCParam

private const val TAG = "UVC_CAMERA_DEBUG"
private const val ACTION_USB_PERMISSION = "com.launchers.teslalauncherv2.USB_PERMISSION"

@Composable
fun ReverseCameraScreen() {
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf("Initializing UVC Engine...") }
    var isRunning by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        USBCameraView(
            refreshTrigger = refreshTrigger,
            onStatusChange = { running, msg ->
                isRunning = running
                statusMessage = msg
            }
        )

        if (!isRunning) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = statusMessage,
                    color = if (statusMessage.contains("Error") || statusMessage.contains("Denied") || statusMessage.contains("Timeout")) Color.Red else Color.Gray,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    Log.d(TAG, "User triggered manual reconnection")
                    refreshTrigger++
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

@Composable
fun USBCameraView(refreshTrigger: Int, onStatusChange: (Boolean, String) -> Unit) {
    val context = LocalContext.current
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }

    var mUVCCamera by remember { mutableStateOf<UVCCamera?>(null) }
    var mPreviewSurface by remember { mutableStateOf<SurfaceTexture?>(null) }

    fun releaseCamera() {
        Log.d(TAG, "Stopping UVC preview and releasing resources")
        try { mUVCCamera?.stopPreview() } catch (e: Exception) { }
        try { mUVCCamera?.destroy() } catch (e: Exception) { }
        mUVCCamera = null
    }

    fun startPreviewSafely() {
        val camera = mUVCCamera
        val surface = mPreviewSurface
        if (camera != null && surface != null) {
            try {
                camera.setPreviewTexture(surface)
                camera.startPreview()
                onStatusChange(true, "")
            } catch (e: Exception) {
                Log.e(TAG, "Native preview start failed", e)
                onStatusChange(false, "Preview Error: ${e.message}")
                releaseCamera()
            }
        }
    }

    DisposableEffect(refreshTrigger) {
        Log.d(TAG, "Setting up USBMonitor and Connection Listeners")

        val usbMonitor = USBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {
                Log.d(TAG, "USBMonitor: Device attached")
                onStatusChange(false, "Device found. Checking rights...")
            }

            override fun onDeviceOpen(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {
                Log.i(TAG, "USBMonitor: Device opened successfully. Initializing UVCCamera")
                releaseCamera()
                try {
                    val camera = UVCCamera(UVCParam())
                    ctrlBlock?.let { block ->
                        camera.open(block)

                        // 🌟 OPRAVENO: Vrácena PLNÁ kaskáda rozlišení a formátů.
                        // Zkoušíme postupně vše, co starší i nové kamery mohou umět.
                        val sizeSet = try {
                            Log.d(TAG, "Trying 1280x720 MJPEG")
                            camera.setPreviewSize(1280, 720, UVCCamera.FRAME_FORMAT_MJPEG)
                            true
                        } catch (e1: Exception) {
                            try {
                                Log.d(TAG, "Trying 1280x720 YUYV")
                                camera.setPreviewSize(1280, 720, UVCCamera.FRAME_FORMAT_YUYV)
                                true
                            } catch (e2: Exception) {
                                try {
                                    Log.d(TAG, "Trying 640x480 MJPEG")
                                    camera.setPreviewSize(640, 480, UVCCamera.FRAME_FORMAT_MJPEG)
                                    true
                                } catch (e3: Exception) {
                                    try {
                                        Log.d(TAG, "Trying 640x480 YUYV")
                                        camera.setPreviewSize(640, 480, UVCCamera.FRAME_FORMAT_YUYV)
                                        true
                                    } catch (e4: Exception) {
                                        try {
                                            Log.d(TAG, "Trying Default Format")
                                            camera.setPreviewSize(640, 480)
                                            true
                                        } catch (e5: Exception) {
                                            false
                                        }
                                    }
                                }
                            }
                        }

                        if (sizeSet) {
                            mUVCCamera = camera
                            startPreviewSafely()
                        } else {
                            throw Exception("No supported preview format found.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Camera init failed: ${e.message}")
                    onStatusChange(false, "Init Error: ${e.message}")
                }
            }

            override fun onDeviceClose(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) { releaseCamera() }
            override fun onDetach(device: UsbDevice?) { releaseCamera() }
            override fun onCancel(device: UsbDevice?) { onStatusChange(false, "Permission Denied") }
        })

        // SPRÁVNÝ BROADCAST RECEIVER
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            Log.d(TAG, "Broadcast: Permission GRANTED by User!")
                            onStatusChange(false, "Connecting to camera...")

                            // Na Androidu 14 musíme chvíli počkat, než OS zapíše práva hluboko do systému. Na A10 to ničemu neškodí.
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                if (device != null) {
                                    Log.d(TAG, "Triggering USBMonitor after delay.")
                                    usbMonitor.requestPermission(device)
                                }
                            }, 500)
                        } else {
                            Log.e(TAG, "Broadcast: Permission DENIED by User")
                            onStatusChange(false, "Permission Denied")
                        }
                    }
                }
            }
        }

        // Exportovaný receiver (Povinné pro nové Androidy)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(context, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        usbMonitor.register()

        // -------------------------------------------------------------
        // VLASTNÍ ŽÁDOST O PRÁVA S MUTABLE FLAGEM (Funguje A10 i A14)
        // -------------------------------------------------------------
        val devices = usbManager.deviceList
        if (devices.isNotEmpty()) {
            val device = devices.values.first()
            if (usbManager.hasPermission(device)) {
                usbMonitor.requestPermission(device)
            } else {
                Log.d(TAG, "Forcing explicit system permission dialog (MUTABLE)")
                onStatusChange(false, "Awaiting system permission...")

                val intent = Intent(ACTION_USB_PERMISSION).apply {
                    setPackage(context.packageName)
                }

                // Zde MUSÍ být MUTABLE, jinak ztratíme výsledek kliknutí na novějších Androidech
                val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                val permissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags)

                usbManager.requestPermission(device, permissionIntent)
            }
        } else {
            onStatusChange(false, "No Camera Found")
        }

        onDispose {
            releaseCamera()
            context.unregisterReceiver(usbReceiver)
            usbMonitor.unregister()
            usbMonitor.destroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
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