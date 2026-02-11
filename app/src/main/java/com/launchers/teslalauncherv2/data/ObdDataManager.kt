package com.launchers.teslalauncherv2.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.random.Random

object ObdDataManager {

    private val _carState = MutableStateFlow(CarState())
    val carState = _carState.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // UUID pro standardní sériový port (SPP) - používají to všechny levné ELM327
    private val ELM_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // --- HLAVNÍ METODY ---

    fun startDemoMode() {
        stop()
        _carState.update { it.copy(isDemoMode = true, isConnected = true) }
        job = scope.launch {
            var simSpeed = 0
            var simRpm = 800
            while (isActive) {
                // Simulace jízdy
                simSpeed += Random.nextInt(-2, 5)
                if (simSpeed < 0) simSpeed = 0
                if (simSpeed > 160) simSpeed = 160

                simRpm = if (simSpeed == 0) 800 + Random.nextInt(-50, 50) else (simSpeed * 30) + Random.nextInt(-100, 100)

                _carState.update {
                    it.copy(
                        speed = simSpeed,
                        rpm = simRpm,
                        coolantTemp = 90 + Random.nextInt(-2, 3),
                        batteryVoltage = "14.${Random.nextInt(1, 5)}V",
                        fuelLevel = 75f
                    )
                }
                delay(200) // Aktualizace 5x za sekundu
            }
        }
    }

    @SuppressLint("MissingPermission") // Oprávnění kontrolujeme v UI před voláním
    fun connectToBluetooth(deviceAddress: String) {
        stop()
        job = scope.launch {
            try {
                _carState.update { it.copy(error = "Connecting...", isDemoMode = false) }

                val adapter = BluetoothAdapter.getDefaultAdapter()
                val device = adapter.getRemoteDevice(deviceAddress)

                // 1. Připojení k socketu
                socket = device.createRfcommSocketToServiceRecord(ELM_UUID)
                socket?.connect()

                _carState.update { it.copy(isConnected = true, error = null) }

                val input = socket!!.inputStream
                val output = socket!!.outputStream

                // 2. Inicializace ELM327 (Restart a nastavení)
                sendCommand("ATZ", input, output)   // Reset
                delay(1000)
                sendCommand("ATE0", input, output)  // Echo Off (důležité pro parsování!)
                sendCommand("ATSP0", input, output) // Auto Protocol

                // 3. Smyčka čtení dat
                while (isActive && socket!!.isConnected) {
                    // Pošleme dotazy na PIDs
                    val rpmRaw = sendCommand("010C", input, output)
                    val speedRaw = sendCommand("010D", input, output)
                    val tempRaw = sendCommand("0105", input, output)
                    val voltRaw = sendCommand("ATRV", input, output) // Napětí přímo z adaptéru

                    // Aktualizujeme stav
                    _carState.update { current ->
                        current.copy(
                            rpm = parseRPM(rpmRaw) ?: current.rpm,
                            speed = parseSpeed(speedRaw) ?: current.speed,
                            coolantTemp = parseTemp(tempRaw) ?: current.coolantTemp,
                            batteryVoltage = parseVoltage(voltRaw) ?: current.batteryVoltage
                        )
                    }
                    delay(150) // Rychlost dotazování
                }

            } catch (e: Exception) {
                Log.e("OBD", "Connection failed", e)
                _carState.update { it.copy(isConnected = false, error = "Connection Error: ${e.message}") }
                stop()
            }
        }
    }

    fun stop() {
        job?.cancel()
        try {
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        socket = null
        _carState.update { it.copy(isConnected = false) }
    }

    // --- POMOCNÉ FUNKCE PRO KOMUNIKACI ---

    private fun sendCommand(cmd: String, input: InputStream, output: OutputStream): String {
        return try {
            output.write((cmd + "\r").toByteArray())
            output.flush()

            // Čtení odpovědi (zjednodušené)
            val buffer = ByteArray(1024)
            var bytesRead = 0
            val sb = StringBuilder()

            // Čteme dokud nenarazíme na znak '>' (prompt ELM327) nebo timeout
            var c: Char
            do {
                if (input.available() > 0) {
                    val b = input.read()
                    if (b == -1) break
                    c = b.toChar()
                    if (c == '>') break // Konec zprávy
                    sb.append(c)
                } else {
                    delayBlocking(10) // Čekáme na data
                }
            } while (true)

            // Vyčistíme odpověď od mezer a bordelu
            sb.toString().replace("SEARCHING...", "").replace("\\s".toRegex(), "")
        } catch (e: Exception) {
            ""
        }
    }

    // Pomocná funkce pro blokující čekání uvnitř coroutiny
    private fun delayBlocking(ms: Long) {
        try { Thread.sleep(ms) } catch (_: Exception) {}
    }

    // --- PARSOVÁNÍ DAT (HEX to DEC) ---

    // RPM: Odpověď např. "41 0C 1A F8" -> (A*256 + B) / 4
    private fun parseRPM(raw: String): Int? {
        if (!raw.contains("410C")) return null
        return try {
            val hex = raw.substringAfter("410C").take(4) // Vezmeme 4 znaky dat
            val a = Integer.parseInt(hex.substring(0, 2), 16)
            val b = Integer.parseInt(hex.substring(2, 4), 16)
            ((a * 256) + b) / 4
        } catch (e: Exception) { null }
    }

    // Speed: Odpověď např. "41 0D 32" -> A km/h
    private fun parseSpeed(raw: String): Int? {
        if (!raw.contains("410D")) return null
        return try {
            val hex = raw.substringAfter("410D").take(2)
            Integer.parseInt(hex, 16)
        } catch (e: Exception) { null }
    }

    // Temp: Odpověď např. "41 05 7B" -> A - 40
    private fun parseTemp(raw: String): Int? {
        if (!raw.contains("4105")) return null
        return try {
            val hex = raw.substringAfter("4105").take(2)
            Integer.parseInt(hex, 16) - 40
        } catch (e: Exception) { null }
    }

    // Voltage: Odpověď např. "12.4V"
    private fun parseVoltage(raw: String): String? {
        if (raw.isEmpty()) return null
        return raw.replace("ATRV", "") // Někdy se vrátí echo
    }
}