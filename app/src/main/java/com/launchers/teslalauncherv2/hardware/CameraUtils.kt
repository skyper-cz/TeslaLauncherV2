@file:Suppress("OPT_IN_USAGE", "DEPRECATION", "SpellCheckingInspection", "UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER")

package com.launchers.teslalauncherv2.hardware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.window.Dialog // 🌟 Přidán import pro Dialog
import androidx.core.content.ContextCompat
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCParam

private const val TAG = "UVC_CAMERA_DEBUG"
private const val ACTION_USB_PERMISSION = "com.launchers.teslalauncherv2.USB_PERMISSION"

@Composable
fun ReverseCameraScreen(isReverseGear: Boolean) {
    val refreshTrigger = remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf("Initializing UVC Engine...") }
    var isRunning by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(if (isReverseGear) Color.Black else Color.Transparent)) {
        USBCameraView(
            refreshTriggerState = refreshTrigger,
            onStatusChange = { running, msg ->
                isRunning = running
                statusMessage = msg
            }
        )

        if (isReverseGear) {
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
fun USBCameraView(refreshTriggerState: MutableState<Int>, onStatusChange: (Boolean, String) -> Unit) {
    val context = LocalContext.current
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }

    val sharedPrefs = remember { context.getSharedPreferences("TeslaSettings", Context.MODE_PRIVATE) }
    val isDashcamEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("enable_dashcam", false) && sharedPrefs.getBoolean("master_experimental", false)) }

    var mUVCCamera by remember { mutableStateOf<UVCCamera?>(null) }
    var mPreviewSurface by remember { mutableStateOf<SurfaceTexture?>(null) }
    var mMediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    var activeWidth by remember { mutableIntStateOf(640) }
    var activeHeight by remember { mutableIntStateOf(480) }

    var unassignedDevice by remember { mutableStateOf<UsbDevice?>(null) }

    fun stopRecording() {
        try {
            mMediaRecorder?.stop()
            mMediaRecorder?.release()
            Log.d(TAG, "Dashcam recording stopped and saved.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder", e)
        } finally {
            mMediaRecorder = null
        }
    }

    fun releaseCamera() {
        Log.d(TAG, "Stopping UVC preview and releasing resources")
        stopRecording()
        try { mUVCCamera?.stopPreview() } catch (e: Exception) { }
        try { mUVCCamera?.destroy() } catch (e: Exception) { }
        mUVCCamera = null
    }

    fun startPreviewSafely() {
        val camera = mUVCCamera
        val surface = mPreviewSurface
        if (camera != null && surface != null) {
            try {
                if (isDashcamEnabled && unassignedDevice == null) {
                    val newVideoPath = DashcamManager.getNewOutputFilePath(context)
                    if (newVideoPath != null) {
                        Log.i(TAG, "Dashcam ENABLED. Recording to: $newVideoPath")

                        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
                        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        recorder.setOutputFile(newVideoPath)
                        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                        recorder.setVideoEncodingBitRate(5000000)
                        recorder.setVideoFrameRate(30)
                        recorder.setVideoSize(activeWidth, activeHeight)
                        recorder.prepare()

                        camera.setPreviewDisplay(recorder.surface)
                        camera.startPreview()
                        recorder.start()

                        mMediaRecorder = recorder
                        onStatusChange(true, "Recording Dashcam ($activeWidth x $activeHeight)...")
                    } else {
                        onStatusChange(false, "Storage Error. Falling back to preview.")
                        camera.setPreviewTexture(surface)
                        camera.startPreview()
                    }
                } else {
                    camera.setPreviewTexture(surface)
                    camera.startPreview()
                    onStatusChange(true, "")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Native preview start failed", e)
                onStatusChange(false, "Engine Error: ${e.message}")
                releaseCamera()
            }
        }
    }

    DisposableEffect(refreshTriggerState.value) {

        // 🌟 OPRAVA: Deklarujeme usbMonitor předem, abychom ho mohli uvnitř volat
        var usbMonitor: USBMonitor? = null

        val listener = object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {
                onStatusChange(false, "Device found. Requesting rights...")
                if (device != null) {
                    try {
                        if (usbManager.hasPermission(device)) {
                            usbMonitor?.requestPermission(device) // Nyní to kompilátor zná!
                        } else {
                            val intent = Intent(ACTION_USB_PERMISSION).apply { setPackage(context.packageName) }
                            val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            val permissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
                            usbManager.requestPermission(device, permissionIntent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error requesting permission on attach", e)
                    }
                }
            }

            override fun onDeviceOpen(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {
                releaseCamera()
                try {
                    val camera = UVCCamera(UVCParam())
                    ctrlBlock?.let { block ->
                        camera.open(block)

                        if (device != null) {
                            val camId = "${device.vendorId}_${device.productId}"
                            val savedRole = sharedPrefs.getString("cam_role_$camId", "NONE")
                            if (savedRole == "NONE") {
                                Log.i(TAG, "Unassigned camera detected! Triggering Role UI.")
                                unassignedDevice = device
                            } else {
                                Log.i(TAG, "Camera recognized. Role: $savedRole")
                            }
                        }

                        val sizeSet = try {
                            camera.setPreviewSize(1280, 720, UVCCamera.FRAME_FORMAT_MJPEG)
                            activeWidth = 1280; activeHeight = 720
                            true
                        } catch (e1: Exception) {
                            try {
                                camera.setPreviewSize(1280, 720, UVCCamera.FRAME_FORMAT_YUYV)
                                activeWidth = 1280; activeHeight = 720
                                true
                            } catch (e2: Exception) {
                                try {
                                    camera.setPreviewSize(640, 480, UVCCamera.FRAME_FORMAT_MJPEG)
                                    activeWidth = 640; activeHeight = 480
                                    true
                                } catch (e3: Exception) {
                                    try {
                                        camera.setPreviewSize(640, 480, UVCCamera.FRAME_FORMAT_YUYV)
                                        activeWidth = 640; activeHeight = 480
                                        true
                                    } catch (e4: Exception) {
                                        try {
                                            Log.d(TAG, "Trying Default Format")
                                            camera.setPreviewSize(640, 480)
                                            activeWidth = 640; activeHeight = 480
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
                            throw Exception("No supported format.")
                        }
                    }
                } catch (e: Exception) {
                    onStatusChange(false, "Init Error: ${e.message}")
                }
            }

            override fun onDeviceClose(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) { releaseCamera() }
            override fun onDetach(device: UsbDevice?) { releaseCamera() }
            override fun onCancel(device: UsbDevice?) { onStatusChange(false, "Permission Denied") }
        }

        // Tady monitor skutečně vytvoříme a předáme mu ten Listener nahoře
        usbMonitor = USBMonitor(context, listener)

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
                            onStatusChange(false, "Connecting...")
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                if (device != null) usbMonitor?.requestPermission(device)
                            }, 500)
                        } else {
                            onStatusChange(false, "Permission Denied")
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(context, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        usbMonitor.register()

        val devices = usbManager.deviceList
        if (devices.isNotEmpty()) {
            val device = devices.values.first()
            if (usbManager.hasPermission(device)) {
                usbMonitor.requestPermission(device)
            } else {
                val intent = Intent(ACTION_USB_PERMISSION).apply { setPackage(context.packageName) }
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
            usbMonitor?.unregister()
            usbMonitor?.destroy()
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

        if (unassignedDevice != null) {
            Dialog(onDismissRequest = { /* Musí vybrat! */ }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF222222), RoundedCornerShape(16.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Videocam, null, tint = Color.Cyan, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("NEW CAMERA DETECTED", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Where is this camera mounted?", color = Color.LightGray, fontSize = 16.sp)

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = {
                                    val camId = "${unassignedDevice!!.vendorId}_${unassignedDevice!!.productId}"
                                    sharedPrefs.edit().putString("cam_role_$camId", "FRONT").apply()
                                    unassignedDevice = null
                                    releaseCamera()
                                    refreshTriggerState.value++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004400)),
                                modifier = Modifier.height(60.dp)
                            ) {
                                Text("FRONT (Dashcam)")
                            }

                            Button(
                                onClick = {
                                    val camId = "${unassignedDevice!!.vendorId}_${unassignedDevice!!.productId}"
                                    sharedPrefs.edit().putString("cam_role_$camId", "REAR").apply()
                                    unassignedDevice = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF000044)),
                                modifier = Modifier.height(60.dp)
                            ) {
                                Text("REAR (Reverse)")
                            }
                        }
                    }
                }
            }
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