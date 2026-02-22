package com.launchers.teslalauncherv2.data

object DTCManager {
    // Jednoduchá databáze nejčastějších chyb (můžeme časem rozšířit)
    private val codes = mapOf(
        // --- AIR AND FUEL MONITORING (P01xx - P02xx) ---
        "P0101" to "Mass or Volume Air Flow Circuit Range/Performance",
        "P0113" to "Intake Air Temperature Sensor 1 Circuit High",
        "P0121" to "Throttle/Pedal Position Sensor A Circuit Range/Performance",
        "P0130" to "O2 Sensor Circuit Malfunction (Bank 1, Sensor 1)",
        "P0171" to "System Too Lean (Bank 1)",
        "P0172" to "System Too Rich (Bank 1)",
        "P0174" to "System Too Lean (Bank 2)",
        "P0175" to "System Too Rich (Bank 2)",
        "P0201" to "Injector Circuit Malfunction - Cylinder 1",
        "P0234" to "Engine Overboost Condition",

        // --- IGNITION SYSTEM & MISFIRES (P03xx) ---
        "P0300" to "Random/Multiple Cylinder Misfire Detected",
        "P0301" to "Cylinder 1 Misfire Detected",
        "P0302" to "Cylinder 2 Misfire Detected",
        "P0303" to "Cylinder 3 Misfire Detected",
        "P0304" to "Cylinder 4 Misfire Detected",
        "P0325" to "Knock Sensor 1 Circuit Malfunction",
        "P0340" to "Camshaft Position Sensor Circuit Malfunction",

        // --- AUXILIARY EMISSION CONTROLS (P04xx) ---
        "P0401" to "Exhaust Gas Recirculation (EGR) Flow Insufficient Detected",
        "P0420" to "Catalyst System Efficiency Below Threshold (Bank 1)",
        "P0430" to "Catalyst System Efficiency Below Threshold (Bank 2)",
        "P0442" to "Evaporative Emission System Leak Detected (Small Leak)",
        "P0455" to "Evaporative Emission System Leak Detected (Gross Leak)",

        // --- VEHICLE SPEED, IDLE CONTROL & COMPUTER (P05xx - P06xx) ---
        "P0500" to "Vehicle Speed Sensor Malfunction",
        "P0505" to "Idle Control System Malfunction",
        "P0603" to "Internal Control Module Keep Alive Memory (KAM) Error",

        // --- TRANSMISSION (P07xx) ---
        "P0700" to "Transmission Control System Malfunction (MIL Request)",
        "P0705" to "Transmission Range Sensor Circuit Malfunction (PRNDL Input)",
        "P0730" to "Incorrect Gear Ratio",

        // --- POWERTRAIN / TIMING (P00xx) ---
        "P0011" to "Camshaft Position - Timing Over-Advanced (Bank 1)",
        "P0012" to "Camshaft Position - Timing Over-Retarded (Bank 1)",
        "P0031" to "HO2S Heater Control Circuit Low (Bank 1, Sensor 1)"
    )

    fun getDescription(code: String?): String {
        if (code.isNullOrEmpty()) return ""
        // Ořízneme případné mezery nebo neviditelné znaky
        val cleanCode = code.trim().uppercase()

        return if (codes.containsKey(cleanCode)) {
            "${codes[cleanCode]} ($cleanCode)"
        } else {
            "Engine Fault Detected ($cleanCode)"
        }
    }

    // Rozhodne, zda je chyba kritická (pro červenou barvu)
    fun isCritical(code: String?): Boolean {
        if (code.isNullOrEmpty()) return false
        // Kritické chyby motoru (např. vynechávání válců nebo tlak oleje)
        return code.startsWith("P03") || code.startsWith("P00")
    }
}