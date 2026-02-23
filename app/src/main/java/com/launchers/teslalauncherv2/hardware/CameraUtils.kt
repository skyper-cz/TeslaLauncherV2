@file:Suppress("OPT_IN_USAGE", "DEPRECATION", "SpellCheckingInspection", "UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER")

package com.launchers.teslalauncherv2.hardware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCParam
import com.serenegiant.usb.IFrameCallback
import java.io.File
import java.io.FileOutputStream

// Imports for AI and Coroutines
import com.launchers.teslalauncherv2.ai.AiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val TAG = "UVC_CAMERA_DEBUG"
const val ACTION_USB_PERMISSION = "com.launchers.teslalauncherv2.USB_PERMISSION"

@Composable
fun USBCameraView(refreshTriggerState: MutableState<Int>, onStatusChange: (Boolean, String) -> Unit) {
    val context = LocalContext.current
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }

    val sharedPrefs = remember { context.getSharedPreferences("TeslaSettings", Context.MODE_PRIVATE) }
    val isDashcamEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("enable_dashcam", false) && sharedPrefs.getBoolean("master_experimental", false)) }
    val isAiEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("enable_ai", false) && sharedPrefs.getBoolean("master_experimental", false)) }

    var mUVCCamera by remember { mutableStateOf<UVCCamera?>(null) }
    var mPreviewSurface by remember { mutableStateOf<SurfaceTexture?>(null) }
    var mMediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    var mTextureView by remember { mutableStateOf<TextureView?>(null) }
    var currentCameraRole by remember { mutableStateOf("NONE") }

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

    fun startPreviewSafely(cam: UVCCamera? = mUVCCamera, role: String = currentCameraRole) {
        val camera = cam ?: return

        try {
            // Setup Native Frame Callback for AI (Bypasses the UI entirely)
            var lastAiProcessTime = 0L

            camera.setFrameCallback(IFrameCallback { frame ->
                if (isAiEnabled && role == "FRONT") {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastAiProcessTime >= 500) {
                        lastAiProcessTime = currentTime
                        frame?.let { buffer ->
                            try {
                                buffer.clear()
                                val bitmap = Bitmap.createBitmap(activeWidth, activeHeight, Bitmap.Config.RGB_565)
                                bitmap.copyPixelsFromBuffer(buffer)

                                // Launch AI processing on a background thread so we do not block the USB stream
                                CoroutineScope(Dispatchers.Default).launch {
                                    val result = AiManager.analyzeFrame(bitmap)
                                    Log.d("AI_VISION", result)
                                }
                            } catch (e: Exception) {
                                Log.e("AI_VISION", "Native frame conversion error", e)
                            }
                        }
                    }
                }
            }, UVCCamera.PIXEL_FORMAT_RGB565)

            if (role == "FRONT") {
                if (isDashcamEnabled && unassignedDevice == null) {
                    val newVideoPath = DashcamManager.getNewOutputFilePath(context)
                    if (newVideoPath != null) {
                        Log.i(TAG, "Dashcam ENABLED for FRONT. Recording to: $newVideoPath")

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
                        onStatusChange(false, "Storage Error.")
                    }
                } else {
                    onStatusChange(false, "Dashcam disabled in settings.")
                }
            } else if (role == "REAR") {
                val surface = mPreviewSurface
                if (surface != null) {
                    camera.setPreviewTexture(surface)
                    camera.startPreview()
                    onStatusChange(true, "")
                } else {
                    onStatusChange(false, "Waiting for screen...")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Native preview start failed", e)
            onStatusChange(false, "Engine Error: ${e.message}")
        }
    }

    DisposableEffect(refreshTriggerState.value) {
        var usbMonitor: USBMonitor? = null

        val listener = object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {
                onStatusChange(false, "Device found. Requesting rights...")
                if (device != null) {
                    try {
                        if (usbManager.hasPermission(device)) {
                            usbMonitor?.requestPermission(device)
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

                        var savedRole = "NONE"
                        if (device != null) {
                            val camId = "${device.vendorId}_${device.productId}"
                            savedRole = sharedPrefs.getString("cam_role_$camId", "NONE") ?: "NONE"
                            currentCameraRole = savedRole

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
                            startPreviewSafely(cam = camera, role = savedRole)
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
                    mTextureView = this
                    isOpaque = true

                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            Log.d(TAG, "SurfaceTexture Available")
                            mPreviewSurface = surface
                            startPreviewSafely()
                        }
                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            Log.d(TAG, "SurfaceTexture Destroyed")
                            mPreviewSurface = null
                            mTextureView = null

                            if (currentCameraRole != "FRONT") {
                                try { mUVCCamera?.stopPreview() } catch (e: Exception) {}
                            }
                            return true
                        }
                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (unassignedDevice != null) {
            Dialog(onDismissRequest = { /* Must select an option */ }) {
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
                                    releaseCamera()
                                    refreshTriggerState.value++
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