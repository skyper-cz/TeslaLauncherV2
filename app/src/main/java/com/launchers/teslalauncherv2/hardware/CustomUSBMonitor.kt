package com.launchers.teslalauncherv2.hardware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.serenegiant.usb.USBMonitor
import java.util.ArrayList

class CustomUSBMonitor(context: Context, private val listener: USBMonitor.OnDeviceConnectListener) {

    private val appContext = context.applicationContext
    private val usbManager: UsbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val actionUsbPermission = "com.launchers.teslalauncherv2.USB_PERMISSION"

    private val wrappedMonitor = USBMonitor(appContext, listener)
    private var isRegistered = false

    private val permissionIntent: PendingIntent?
        get() {
            return try {
                val intent = Intent(actionUsbPermission)
                intent.setPackage(appContext.packageName)

                // ZMĚNA: Přidán FLAG_UPDATE_CURRENT, aby se intent obnovil
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= 31) {
                    flags = flags or PendingIntent.FLAG_MUTABLE
                }

                PendingIntent.getBroadcast(appContext, 0, intent, flags)
            } catch (e: Exception) {
                Log.e("CustomUSBMonitor", "Error creating PendingIntent", e)
                null
            }
        }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (actionUsbPermission == action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            listener.onConnect(device, wrappedMonitor.openDevice(device), true)
                        }
                    } else {
                        if (device != null) listener.onCancel(device)
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (device != null) {
                    listener.onDisconnect(device, null)
                }
            }
        }
    }

    fun register() {
        if (isRegistered) return
        try {
            val filter = IntentFilter(actionUsbPermission)
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            ContextCompat.registerReceiver(appContext, usbReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            isRegistered = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            appContext.unregisterReceiver(usbReceiver)
        } catch (e: Exception) { e.printStackTrace() }
        isRegistered = false
        try {
            wrappedMonitor.destroy()
        } catch (e: Exception) {}
    }

    fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            listener.onConnect(device, wrappedMonitor.openDevice(device), true)
        } else {
            val intent = permissionIntent
            if (intent != null) {
                usbManager.requestPermission(device, intent)
            }
        }
    }

    // Nová funkce pro vynucené otevření
    fun forceOpen(device: UsbDevice) {
        try {
            listener.onConnect(device, wrappedMonitor.openDevice(device), true)
        } catch (e: Exception) {
            Log.e("CustomUSBMonitor", "Force Open Failed", e)
            throw e // Pošleme chybu dál, ať ji vidíme v UI
        }
    }

    fun getDeviceList(): List<UsbDevice> {
        return try {
            ArrayList(usbManager.deviceList.values)
        } catch (e: Exception) { emptyList() }
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)
    fun isRegistered(): Boolean = isRegistered
}