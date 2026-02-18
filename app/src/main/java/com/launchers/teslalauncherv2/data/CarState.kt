package com.launchers.teslalauncherv2.data

// Global state holder for vehicle telemetry, updated by the OBD manager
data class CarState(
    val speed: Int = 0,               // Vehicle speed in km/h
    val rpm: Int = 0,                 // Engine revolutions per minute
    val coolantTemp: Int = 0,         // Engine coolant temperature in Celsius
    val engineLoad: Int = 0,          // Calculated engine load percentage
    val fuelLevel: Float = 0f,        // Fuel tank level percentage
    val batteryVoltage: String = "0.0V", // Vehicle battery voltage
    val isConnected: Boolean = false, // Bluetooth ELM327 connection status
    val isDemoMode: Boolean = false,  // True if running simulated test data
    val error: String? = null         // Active OBD Diagnostic Trouble Code (e.g., "P0171")
)