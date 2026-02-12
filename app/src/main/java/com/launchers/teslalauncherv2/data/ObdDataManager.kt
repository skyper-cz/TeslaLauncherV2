package com.launchers.teslalauncherv2.data

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.random.Random

object ObdDataManager {
    // Používáme tvou existující definici CarState
    private val _carState = MutableStateFlow(CarState())
    val carState: StateFlow<CarState> = _carState.asStateFlow()

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var isRunning = false

    // --- PŘIPOJENÍ K REÁLNÉMU AUTU ---
    fun connect(context: Context, macAddress: String) {
        if (isRunning) return
        isRunning = true
        stopDemo() // Pro jistotu vypneme demo

        CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                try {
                    updateState { it.copy(error = "Connecting...", isConnected = false, isDemoMode = false) }
                    connectToDevice(context, macAddress)

                    updateState { it.copy(isConnected = true, error = null) }
                    initializeObd()

                    // Hlavní smyčka čtení dat
                    while (isRunning && socket?.isConnected == true) {
                        // Čteme data sekvenčně
                        val rpmStr = runCommand("010C")
                        val speedStr = runCommand("010D")
                        val tempStr = runCommand("0105")
                        val loadStr = runCommand("0104") // Zátěž motoru
                        val fuelStr = runCommand("012F") // Palivo (nemusí fungovat všude)
                        val voltStr = runCommand("AT RV") // Napětí baterie (příkaz čipu, ne OBD)

                        updateState { it.copy(
                            rpm = parseRpm(rpmStr),
                            speed = parseSpeed(speedStr),
                            coolantTemp = parseTemp(tempStr),
                            engineLoad = parsePercent(loadStr),
                            fuelLevel = parsePercentFloat(fuelStr),
                            batteryVoltage = parseVoltage(voltStr)
                        )}
                        delay(150) // Malá pauza
                    }

                } catch (e: Exception) {
                    Log.e("OBD", "Error: ${e.message}")
                    updateState { it.copy(isConnected = false, error = "Disconnected. Retrying...") }
                } finally {
                    closeSocket()
                }

                if (isRunning) delay(3000) // Počkáme před dalším pokusem
            }
        }
    }

    // --- DEMO MÓD (Simulace pro testování doma) ---
    private var demoJob: kotlinx.coroutines.Job? = null

    fun startDemoMode() {
        if (isRunning) return
        isRunning = true

        demoJob = CoroutineScope(Dispatchers.Default).launch {
            var speed = 0
            var rpm = 800
            var temp = 40

            while (isRunning) {
                // Simulace jízdy
                if (speed < 100) speed += Random.nextInt(0, 3) else speed -= Random.nextInt(0, 3)
                rpm = (speed * 30) + 800 + Random.nextInt(-50, 50)
                if (temp < 90) temp += 1

                updateState { it.copy(
                    speed = speed,
                    rpm = rpm,
                    coolantTemp = temp,
                    engineLoad = Random.nextInt(20, 60),
                    fuelLevel = 75.5f,
                    batteryVoltage = "14.2V",
                    isConnected = false, // V demu nejsme připojeni k BT
                    isDemoMode = true,
                    error = null
                )}
                delay(200)
            }
        }
    }

    fun stop() {
        isRunning = false
        stopDemo()
        closeSocket()
        updateState { it.copy(isConnected = false, isDemoMode = false) }
    }

    private fun stopDemo() {
        demoJob?.cancel()
        demoJob = null
    }

    // --- INTERNÍ LOGIKA ---

    private fun connectToDevice(context: Context, macAddress: String) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: throw IOException("No Bluetooth")
        val device = adapter.getRemoteDevice(macAddress)

        // Zde by měla být kontrola permission, ale předpokládáme, že už je máme z MainActivity
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()
        } catch (e: SecurityException) {
            throw IOException("Permission denied")
        }
    }

    private fun initializeObd() {
        sendRaw("AT Z")   // Reset
        sendRaw("AT E0")  // Echo Off
        sendRaw("AT SP 0") // Auto Protocol
    }

    private fun runCommand(cmd: String): String {
        return sendRaw(cmd)
    }

    private fun sendRaw(cmd: String): String {
        val out = socket?.outputStream ?: throw IOException("Socket null")
        val input = socket?.inputStream ?: throw IOException("Socket null")

        out.write((cmd + "\r").toByteArray())
        out.flush()

        val buffer = StringBuilder()
        var b: Int
        // Čteme znak po znaku dokud nepřijde '>'
        while (input.read().also { b = it } > -1) {
            val c = b.toChar()
            if (c == '>') break
            buffer.append(c)
        }
        return buffer.toString().replace("SEARCHING...", "").trim()
    }

    private fun closeSocket() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun updateState(update: (CarState) -> CarState) {
        _carState.value = update(_carState.value)
    }

    // --- PARSOVÁNÍ (HEX -> Hodnoty) ---

    private fun parseRpm(res: String): Int { // 010C -> 41 0C A B
        return try {
            val clean = res.replace(" ", "")
            if (clean.length >= 4) {
                val hex = clean.takeLast(4) // Poslední 4 znaky jsou data
                val a = hex.substring(0, 2).toInt(16)
                val b = hex.substring(2, 4).toInt(16)
                ((a * 256) + b) / 4
            } else 0
        } catch (e: Exception) { 0 }
    }

    private fun parseSpeed(res: String): Int { // 010D -> 41 0D A
        return try {
            val clean = res.replace(" ", "")
            if (clean.length >= 2) clean.takeLast(2).toInt(16) else 0
        } catch (e: Exception) { 0 }
    }

    private fun parseTemp(res: String): Int { // 0105 -> 41 05 A
        return try {
            val clean = res.replace(" ", "")
            if (clean.length >= 2) clean.takeLast(2).toInt(16) - 40 else 0
        } catch (e: Exception) { 0 }
    }

    private fun parsePercent(res: String): Int { // Pro Load (0104) -> A * 100 / 255
        return try {
            val clean = res.replace(" ", "")
            if (clean.length >= 2) {
                val a = clean.takeLast(2).toInt(16)
                (a * 100) / 255
            } else 0
        } catch (e: Exception) { 0 }
    }

    private fun parsePercentFloat(res: String): Float { // Pro Palivo (012F)
        return try {
            val clean = res.replace(" ", "")
            if (clean.length >= 2) {
                val a = clean.takeLast(2).toInt(16)
                (a * 100f) / 255f
            } else 0f
        } catch (e: Exception) { 0f }
    }

    private fun parseVoltage(res: String): String { // AT RV -> "12.4V"
        // ELM327 vrací napětí rovnou jako text, stačí vyčistit
        return if (res.contains("V")) res.replace(">", "").trim() else "0.0V"
    }
}