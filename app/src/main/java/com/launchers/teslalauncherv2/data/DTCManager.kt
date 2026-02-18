package com.launchers.teslalauncherv2.data

object DTCManager {
    // Jednoduchá databáze nejčastějších chyb (můžeme časem rozšířit)
    private val codes = mapOf(
        "P0300" to "Random/Multiple Cylinder Misfire Detected",
        "P0301" to "Cylinder 1 Misfire Detected",
        "P0302" to "Cylinder 2 Misfire Detected",
        "P0303" to "Cylinder 3 Misfire Detected",
        "P0304" to "Cylinder 4 Misfire Detected",
        "P0171" to "System Too Lean (Bank 1)",
        "P0172" to "System Too Rich (Bank 1)",
        "P0420" to "Catalyst System Efficiency Below Threshold",
        "P0101" to "Mass or Volume Air Flow Circuit Range/Performance",
        "P0113" to "Intake Air Temperature Sensor 1 Circuit High",
        "P0442" to "Evaporative Emission System Leak Detected (Small Leak)",
        "P0011" to "Camshaft Position - Timing Over-Advanced (Bank 1)"
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