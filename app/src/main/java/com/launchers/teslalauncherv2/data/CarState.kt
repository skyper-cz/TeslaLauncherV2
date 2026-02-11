package com.launchers.teslalauncherv2.data

data class CarState(
    val speed: Int = 0,               // Rychlost km/h
    val rpm: Int = 0,                 // Otáčky
    val coolantTemp: Int = 0,         // Teplota vody °C
    val engineLoad: Int = 0,          // Zátěž motoru %
    val fuelLevel: Float = 0f,        // Palivo %
    val batteryVoltage: String = "0.0V", // Napětí
    val isConnected: Boolean = false, // Je připojeno?
    val isDemoMode: Boolean = false,  // Běží simulace?
    val error: String? = null         // Chybová hláška
)