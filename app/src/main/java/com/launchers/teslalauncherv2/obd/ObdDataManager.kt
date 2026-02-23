package com.launchers.teslalauncherv2.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import com.launchers.teslalauncherv2.data.CarState
import java.io.InputStream
import java.io.OutputStream

// Singleton manager handling background OBD2 Bluetooth communication
object ObdDataManager {
    // Exposes current car metrics to the UI securely
    private val _carState = MutableStateFlow(CarState())
    val carState: StateFlow<CarState> = _carState.asStateFlow()

    private var monitorJob: Job? = null
    private var isUserStopped = false
    private const val TAG = "OBD_DEBUG"

    // Initiates the Bluetooth socket and begins polling OBD commands
    @SuppressLint("MissingPermission")
    fun connect(context: Context, macAddress: String) {
        Log.d(TAG, "Startuji připojení k: $macAddress")
        isUserStopped = false
        monitorJob?.cancel()

        monitorJob = CoroutineScope(Dispatchers.IO).launch {
            while (!isUserStopped) {
                // 🌟 OPRAVA: Deklarace socketu přesunuta ven, abychom ho mohli spolehlivě zavřít
                var socket: android.bluetooth.BluetoothSocket? = null

                try {
                    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter = bluetoothManager.adapter
                    val device = adapter?.getRemoteDevice(macAddress)

                    // Standard SPP (Serial Port Profile) UUID for ELM327 adapters
                    val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                    socket = device?.createRfcommSocketToServiceRecord(uuid)

                    socket?.connect()
                    _carState.value = _carState.value.copy(isConnected = true, error = null)

                    val inStream = socket?.inputStream
                    val outStream = socket?.outputStream

                    // Initial ELM327 setup: Reset and turn off echo
                    sendCommand(outStream, inStream, "ATZ\r")
                    delay(500)
                    sendCommand(outStream, inStream, "ATE0\r")
                    delay(200)

                    var loopCounter = 0

                    // Continuous polling loop
                    while (!isUserStopped && socket?.isConnected == true) {
                        // 1. Fetch Vehicle Speed (PID 0D)
                        val speedRaw = sendCommand(outStream, inStream, "010D\r")
                        val speed = parseSpeed(speedRaw)
                        delay(100)

                        // 2. Fetch Engine RPM (PID 0C)
                        val rpmRaw = sendCommand(outStream, inStream, "010C\r")
                        val rpm = parseRpm(rpmRaw)
                        delay(100)

                        // 3. Fetch Diagnostic Trouble Codes (Every ~10 seconds)
                        loopCounter++
                        var dtcError: String? = _carState.value.error
                        if (loopCounter % 25 == 0) {
                            val dtcRaw = sendCommand(outStream, inStream, "03\r")
                            dtcError = parseDTC(dtcRaw)
                        }

                        // Update global state if valid data was received
                        if (speed != null || rpm != null) {
                            _carState.value = _carState.value.copy(
                                speed = speed ?: _carState.value.speed,
                                rpm = rpm ?: _carState.value.rpm,
                                error = dtcError
                            )
                        }
                        delay(200) // Prevent flooding the Bluetooth serial bus
                    }

                } catch (e: Exception) {
                    // Handle disconnects gracefully
                    _carState.value = _carState.value.copy(isConnected = false, speed = 0, rpm = 0, error = null)
                } finally {
                    // 🌟 OPRAVA: Zde se socket VŽDY bezpečně zavře
                    try {
                        socket?.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Chyba při zavírání socketu", e)
                    }
                }

                // Auto-reconnect delay
                if (!isUserStopped) delay(5000)
            }
        }
    }

    // Writes hex command to BT socket and reads response until '>' character
    private fun sendCommand(outStream: OutputStream?, inStream: InputStream?, command: String): String {
        try {
            outStream?.write(command.toByteArray())
            outStream?.flush()
            val buffer = ByteArray(1024)
            var bytesRead: Int
            val response = StringBuilder()
            while (inStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                val chunk = String(buffer, 0, bytesRead)
                response.append(chunk)
                if (chunk.contains(">")) break
            }
            return response.toString().replace(">", "").trim()
        } catch (e: Exception) { return "" }
    }

    // Extracts hexadecimal speed value from raw ELM327 string
    private fun parseSpeed(rawData: String): Int? {
        val clean = rawData.replace(" ", "").replace("\r", "").replace("\n", "")
        if (clean.contains("410D") && clean.length >= 6) {
            return clean.substring(clean.indexOf("410D") + 4, clean.indexOf("410D") + 6).toIntOrNull(16)
        }
        return null
    }

    // Extracts and calculates hexadecimal RPM value from raw ELM327 string
    private fun parseRpm(rawData: String): Int? {
        val clean = rawData.replace(" ", "").replace("\r", "").replace("\n", "")
        if (clean.contains("410C") && clean.length >= 8) {
            val a = clean.substring(clean.indexOf("410C") + 4, clean.indexOf("410C") + 6).toIntOrNull(16) ?: 0
            val b = clean.substring(clean.indexOf("410C") + 6, clean.indexOf("410C") + 8).toIntOrNull(16) ?: 0
            return ((a * 256) + b) / 4
        }
        return null
    }

    // Parses raw Mode 03 hex response to decode standard engine trouble codes (e.g., P0171)
    private fun parseDTC(rawData: String): String? {
        val clean = rawData.replace(" ", "").replace("\r", "").replace("\n", "")
        if (clean.contains("43")) {
            val start = clean.indexOf("43") + 2
            if (start + 4 <= clean.length) {
                val hex = clean.substring(start, start + 4)
                if (hex != "0000") { // 0000 means no active codes
                    val prefix = when (hex[0]) {
                        '0' -> "P0"; '1' -> "P1"; '2' -> "P2"; '3' -> "P3"
                        '4' -> "C0"; '5' -> "C1"; '6' -> "C2"; '7' -> "C3"
                        '8' -> "B0"; '9' -> "B1"; 'A', 'a' -> "B2"; 'B', 'b' -> "B3"
                        'C', 'c' -> "U0"; 'D', 'd' -> "U1"; 'E', 'e' -> "U2"; 'F', 'f' -> "U3"
                        else -> "P0"
                    }
                    return "$prefix${hex.substring(1)}"
                }
            }
        }
        return null
    }

    // Halts background polling completely
    fun stop() {
        isUserStopped = true
        monitorJob?.cancel()
        _carState.value = _carState.value.copy(isConnected = false, speed = 0, rpm = 0)
    }
}