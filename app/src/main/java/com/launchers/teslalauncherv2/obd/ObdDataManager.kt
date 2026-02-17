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

object ObdDataManager {
    private val _carState = MutableStateFlow(CarState())
    val carState: StateFlow<CarState> = _carState.asStateFlow()

    private var monitorJob: Job? = null
    private var isUserStopped = false
    private const val TAG = "OBD_DEBUG"

    @SuppressLint("MissingPermission")
    fun connect(context: Context, macAddress: String) {
        Log.d(TAG, "Startuji připojení k: $macAddress")
        isUserStopped = false
        monitorJob?.cancel()

        monitorJob = CoroutineScope(Dispatchers.IO).launch {
            while (!isUserStopped) {
                try {
                    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter = bluetoothManager.adapter
                    val device = adapter?.getRemoteDevice(macAddress)
                    val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                    val socket = device?.createRfcommSocketToServiceRecord(uuid)

                    socket?.connect()
                    _carState.value = _carState.value.copy(isConnected = true, error = null)

                    val inStream = socket?.inputStream
                    val outStream = socket?.outputStream

                    sendCommand(outStream, inStream, "ATZ\r")
                    delay(500)
                    sendCommand(outStream, inStream, "ATE0\r")
                    delay(200)

                    var loopCounter = 0

                    while (!isUserStopped && socket?.isConnected == true) {
                        // 1. Rychlost
                        val speedRaw = sendCommand(outStream, inStream, "010D\r")
                        val speed = parseSpeed(speedRaw)
                        delay(100)

                        // 2. Otáčky
                        val rpmRaw = sendCommand(outStream, inStream, "010C\r")
                        val rpm = parseRpm(rpmRaw)
                        delay(100)

                        // 3. Chybové kódy motoru (Každý 25. cyklus = cca 10 vteřin)
                        loopCounter++
                        var dtcError: String? = _carState.value.error
                        if (loopCounter % 25 == 0) {
                            val dtcRaw = sendCommand(outStream, inStream, "03\r")
                            dtcError = parseDTC(dtcRaw)
                        }

                        if (speed != null || rpm != null) {
                            _carState.value = _carState.value.copy(
                                speed = speed ?: _carState.value.speed,
                                rpm = rpm ?: _carState.value.rpm,
                                error = dtcError
                            )
                        }
                        delay(200)
                    }
                    socket?.close()

                } catch (e: Exception) {
                    _carState.value = _carState.value.copy(isConnected = false, speed = 0, rpm = 0, error = null)
                }
                if (!isUserStopped) delay(5000)
            }
        }
    }

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

    private fun parseSpeed(rawData: String): Int? {
        val clean = rawData.replace(" ", "").replace("\r", "").replace("\n", "")
        if (clean.contains("410D") && clean.length >= 6) {
            return clean.substring(clean.indexOf("410D") + 4, clean.indexOf("410D") + 6).toIntOrNull(16)
        }
        return null
    }

    private fun parseRpm(rawData: String): Int? {
        val clean = rawData.replace(" ", "").replace("\r", "").replace("\n", "")
        if (clean.contains("410C") && clean.length >= 8) {
            val a = clean.substring(clean.indexOf("410C") + 4, clean.indexOf("410C") + 6).toIntOrNull(16) ?: 0
            val b = clean.substring(clean.indexOf("410C") + 6, clean.indexOf("410C") + 8).toIntOrNull(16) ?: 0
            return ((a * 256) + b) / 4
        }
        return null
    }

    // 🌟 NOVÝ PARSER NA CHYBY MOTORU (P/C/B/U kódy)
    private fun parseDTC(rawData: String): String? {
        val clean = rawData.replace(" ", "").replace("\r", "").replace("\n", "")
        if (clean.contains("43")) {
            val start = clean.indexOf("43") + 2
            if (start + 4 <= clean.length) {
                val hex = clean.substring(start, start + 4)
                if (hex != "0000") { // 0000 znamená bez chyb
                    val prefix = when (hex[0]) {
                        '0' -> "P0"; '1' -> "P1"; '2' -> "P2"; '3' -> "P3"
                        '4' -> "C0"; '5' -> "C1"; '6' -> "C2"; '7' -> "C3"
                        '8' -> "B0"; '9' -> "B1"; 'A', 'a' -> "B2"; 'B', 'b' -> "B3"
                        'C', 'c' -> "U0"; 'D', 'd' -> "U1"; 'E', 'e' -> "U2"; 'F', 'f' -> "U3"
                        else -> "P0"
                    }
                    return "$prefix${hex.substring(1)}" // Výsledek např. P0171
                }
            }
        }
        return null
    }

    fun stop() {
        isUserStopped = true
        monitorJob?.cancel()
        _carState.value = _carState.value.copy(isConnected = false, speed = 0, rpm = 0)
    }
}