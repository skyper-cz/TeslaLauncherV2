package com.launchers.teslalauncherv2.ai

import android.content.Context
import android.graphics.Bitmap

object AiManager {
    // Flag for UI to know if AI features should be visible
    val isAiSupported = false

    fun initialize(context: Context) {
        // Do nothing in standard version
    }

    fun analyzeFrame(bitmap: Bitmap): String {
        return ""
    }

    fun close() {
        // Do nothing
    }
}