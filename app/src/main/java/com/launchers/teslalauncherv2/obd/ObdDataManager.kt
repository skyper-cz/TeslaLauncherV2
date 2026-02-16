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

object ObdDataManager {
    private val _carState = MutableStateFlow(CarState())
    val carState: StateFlow<CarState> = _carState.asStateFlow()

    // Sem ukládáme našeho hlídače na pozadí
    private var monitorJob: Job? = null

    // Zámek, který říká "Uživatel to vypnul schválně, už se nepokusuj připojit"
    private var isUserStopped = false

    @SuppressLint("MissingPermission")
    fun connect(context: Context, macAddress: String) {
        isUserStopped = false

        // Pokud už nějaký hlídač běží, zastavíme ho, abychom neměli dva najednou
        monitorJob?.cancel()

        // 🌟 TOTO JE NÁŠ HLÍDAČ (Auto-Reconnect Loop) 🌟
        monitorJob = CoroutineScope(Dispatchers.IO).launch {

            // Smyčka běží neustále, dokud aplikaci natvrdo nezavřeme
            while (!isUserStopped) {
                try {
                    // 1. POKUS O PŘIPOJENÍ
                    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter = bluetoothManager.adapter
                    val device = adapter?.getRemoteDevice(macAddress)

                    // Standardní sériový port (SPP) pro OBD2 adaptéry
                    val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                    val socket = device?.createRfcommSocketToServiceRecord(uuid)

                    // Zkusíme otevřít spojení (pokud adaptér není v dosahu, hodí to Exception a skočí to do catch bloku)
                    socket?.connect()

                    // Pokud jsme prošli přes connect(), jsme připojeni!
                    _carState.value = _carState.value.copy(isConnected = true, error = null)

                    val inStream = socket?.inputStream
                    val outStream = socket?.outputStream

                    // 2. SMYČKA ČTENÍ DAT (Běží, dokud se spojení fyzicky nepřeruší)
                    while (!isUserStopped && socket?.isConnected == true) {

                        // ==========================================
                        // ZDE JE TVŮJ KÓD PRO ČTENÍ DAT (PID příkazy)
                        // Např: outStream.write("01 0D\r".toByteArray())
                        // ==========================================

                        // Zabraňuje přetížení Bluetooth sběrnice
                        delay(200)
                    }

                    // Pokud čtecí smyčka skončí (např. socket.isConnected začne být false), bezpečně zavřeme
                    socket?.close()

                } catch (e: Exception) {
                    // 3. PŘIPOJENÍ SELHALO, NEBO SPADLO BĚHEM JÍZDY
                    // Vynulujeme budíky a ukážeme varování
                    _carState.value = _carState.value.copy(
                        isConnected = false,
                        speed = 0,
                        rpm = 0,
                        error = "Spojení ztraceno. Hledám OBD..."
                    )
                }

                // 4. ČEKÁNÍ PŘED DALŠÍM POKUSEM (5 vteřin)
                // Abychom nevybili baterii neustálým spamováním Bluetooth modulu
                if (!isUserStopped) {
                    delay(5000)
                }
            }
        }
    }

    // Tuto funkci volá MainActivity při ukončení aplikace (onDestroy)
    fun stop() {
        isUserStopped = true
        monitorJob?.cancel()
        _carState.value = _carState.value.copy(isConnected = false, speed = 0, rpm = 0)
    }
}