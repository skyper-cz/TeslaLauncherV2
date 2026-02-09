package com.launchers.teslalauncherv2

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.widget.UVCCameraTextureView

// --- HLAVNÍ KOMPONENTA COUVACÍ KAMERY ---
@Composable
fun ReverseCameraScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. USB Kamera (Podklad)
        USBCameraView()

        // 2. Vodicí čáry (Překryv)
        GuideLinesOverlay()
    }
}

@Composable
fun USBCameraView() {
    val context = LocalContext.current

    // Udržujeme stav pro kameru a plochu
    var mUVCCamera by remember { mutableStateOf<UVCCamera?>(null) }
    var mPreviewSurface by remember { mutableStateOf<SurfaceTexture?>(null) }

    // USB Monitor řídí připojení/odpojení kabelu
    val usbMonitor = remember {
        USBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {
                Toast.makeText(context, "USB Camera Attached", Toast.LENGTH_SHORT).show()
                // Požádáme o povolení (vyskočí systémový dialog)
                try {
                    // Voláme to v bezpečném bloku, kdyby byl monitor null
                    // Poznámka: V Compose to odkazujeme přes vnější scope, což je zde OK
                } catch (e: Exception) { e.printStackTrace() }
            }

            override fun onConnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {
                // Zařízení povoleno a připojeno -> Spustíme kameru
                val camera = UVCCamera()
                try {
                    camera.open(ctrlBlock)
                    camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG)
                } catch (e: Exception) {
                    try {
                        // Fallback rozlišení, kdyby kamera neuměla to defaultní
                        camera.setPreviewSize(640, 480, UVCCamera.FRAME_FORMAT_MJPEG)
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }

                // Pokud už máme připravenou plochu (TextureView), začneme kreslit
                if (mPreviewSurface != null) {
                    try {
                        camera.setPreviewTexture(mPreviewSurface)
                        camera.startPreview()
                        mUVCCamera = camera
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                // Úklid při odpojení
                mUVCCamera?.close()
                mUVCCamera = null
            }

            override fun onDettach(device: UsbDevice?) {
                Toast.makeText(context, "USB Camera Detached", Toast.LENGTH_SHORT).show()
            }
            override fun onCancel(device: UsbDevice?) {}
        })
    }

    // Registrace/Odregistrace monitoru při zobrazení/skrytí obrazovky
    DisposableEffect(Unit) {
        usbMonitor.register()
        onDispose {
            mUVCCamera?.close()
            mUVCCamera = null
            usbMonitor.unregister()
        }
    }

    AndroidView(
        factory = { ctx ->
            // Použijeme knihovní TextureView, ale s obecným listenerem
            UVCCameraTextureView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // --- ZDE JE OPRAVA ---
                // Používáme standardní Android listener místo knihovního Callbacku
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {

                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        // Plocha je připravena
                        mPreviewSurface = surface

                        // Zkusíme najít připojená zařízení a požádat o přístup
                        // (pokud už je kamera zapojená při startu aplikace)
                        val devices = usbMonitor.deviceList
                        if (devices.isNotEmpty()) {
                            usbMonitor.requestPermission(devices[0])
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        // Plocha se ničí -> zastavit náhled
                        mUVCCamera?.close()
                        mUVCCamera = null
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

// --- VODICÍ ČÁRY ---
@Composable
fun GuideLinesOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val bottomWidth = w * 0.6f
        val topWidth = w * 0.4f
        val startY = h
        val endY = h * 0.3f

        val leftStart = (w - bottomWidth) / 2
        val rightStart = leftStart + bottomWidth
        val leftEnd = (w - topWidth) / 2
        val rightEnd = leftEnd + topWidth

        fun drawZone(y1: Float, y2: Float, color: Color) {
            val path = Path().apply {
                val fraction1 = (h - y1) / (h - endY)
                val l1 = leftStart + (leftEnd - leftStart) * fraction1
                val r1 = rightStart + (rightEnd - rightStart) * fraction1

                val fraction2 = (h - y2) / (h - endY)
                val l2 = leftStart + (leftEnd - leftStart) * fraction2
                val r2 = rightStart + (rightEnd - rightStart) * fraction2

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