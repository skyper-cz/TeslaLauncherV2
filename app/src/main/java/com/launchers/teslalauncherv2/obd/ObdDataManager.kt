package com.launchers.teslalauncherv2.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
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

    @SuppressLint("MissingPermission")
    fun connect(context: Context, macAddress: String) {
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

                    // Inicializace ELM327 (Reset a vypnutí echa)
                    sendCommand(outStream, inStream, "ATZ\r")
                    delay(500)
                    sendCommand(outStream, inStream, "ATE0\r")
                    delay(200)

                    while (!isUserStopped && socket?.isConnected == true) {
                        // 1. Čtení rychlosti (PID 010D)
                        val speedRaw = sendCommand(outStream, inStream, "010D\r")
                        val speed = parseSpeed(speedRaw)

                        delay(100) // Pauza mezi příkazy, aby se ELM nezahltil

                        // 2. Čtení otáček (PID 010C)
                        val rpmRaw = sendCommand(outStream, inStream, "010C\r")
                        val rpm = parseRpm(rpmRaw)

                        // Aktualizace budíků, pokud přišla validní data
                        if (speed != null || rpm != null) {
                            _carState.value = _carState.value.copy(
                                speed = speed ?: _carState.value.speed,
                                rpm = rpm ?: _carState.value.rpm
                            )
                        }

                        delay(200) // Cyklus čtení
                    }
                    socket?.close()

                } catch (e: Exception) {
                    _carState.value = _carState.value.copy(
                        isConnected = false, speed = 0, rpm = 0, error = "Hledám OBD..."
                    )
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

            // Čteme, dokud neuvidíme znak '>' (ELM327 prompt)
            while (inStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                val chunk = String(buffer, 0, bytesRead)
                response.append(chunk)
                if (chunk.contains(">")) break
            }
            return response.toString().replace(">", "").trim()
        } catch (e: Exception) {
            return ""
        }
    }

    private fun parseSpeed(rawData: String): Int? {
        // Očekávaný formát: "41 0D XX" (XX je rychlost v Hex)
        val cleanData = rawData.replace(" ", "").replace("\r", "").replace("\n", "")
        if (cleanData.contains("410D") && cleanData.length >= 6) {
            val hex = cleanData.substring(cleanData.indexOf("410D") + 4, cleanData.indexOf("410D") + 6)
            return hex.toIntOrNull(16)
        }
        return null
    }

    private fun parseRpm(rawData: String): Int? {
        // Očekávaný formát: "41 0C XX YY" (Otáčky = ((XX * 256) + YY) / 4)
        val cleanData = rawData.replace(" ", "").replace("\r", "").replace("\n", "")
        if (cleanData.contains("410C") && cleanData.length >= 8) {
            val hexA = cleanData.substring(cleanData.indexOf("410C") + 4, cleanData.indexOf("410C") + 6)
            val hexB = cleanData.substring(cleanData.indexOf("410C") + 6, cleanData.indexOf("410C") + 8)
            val a = hexA.toIntOrNull(16) ?: 0
            val b = hexB.toIntOrNull(16) ?: 0
            return ((a * 256) + b) / 4
        }
        return null
    }

    fun stop() {
        isUserStopped = true
        monitorJob?.cancel()
        _carState.value = _carState.value.copy(isConnected = false, speed = 0, rpm = 0)
    }
}