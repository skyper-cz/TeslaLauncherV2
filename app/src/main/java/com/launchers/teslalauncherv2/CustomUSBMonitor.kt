package com.launchers.teslalauncherv2

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.serenegiant.usb.USBMonitor
import java.util.ArrayList

class CustomUSBMonitor(private val context: Context, private val listener: USBMonitor.OnDeviceConnectListener) {

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val actionUsbPermission = "com.launchers.teslalauncherv2.USB_PERMISSION"

    // Instance původní knihovny
    private val wrappedMonitor = USBMonitor(context, listener)
    private var isRegistered = false

    // --- OPRAVA PRO OBRÁZEK 1 (PendingIntent) ---
    private val permissionIntent: PendingIntent by lazy {
        val intent = Intent(actionUsbPermission)
        val flags = if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_MUTABLE // Nutné pro Android 12+
        } else {
            0
        }
        PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    // --- OPRAVA PRO OBRÁZEK 2 (Receiver Exported) ---
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (actionUsbPermission == action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            listener.onConnect(device, wrappedMonitor.openDevice(device), true)
                        }
                    } else {
                        if (device != null) listener.onCancel(device)
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    listener.onDisconnect(device, null)
                }
            }
        }
    }

    fun register() {
        if (isRegistered) return
        val filter = IntentFilter(actionUsbPermission)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)

        // ZDE JE OPRAVA PRO ANDROID 14 (Obrázek 2):
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        isRegistered = true
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isRegistered = false
        wrappedMonitor.destroy()
    }

    fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            listener.onConnect(device, wrappedMonitor.openDevice(device), true)
        } else {
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    fun getDeviceList(): List<UsbDevice> {
        return ArrayList(usbManager.deviceList.values)
    }

    fun isRegistered(): Boolean {
        return isRegistered
    }
}