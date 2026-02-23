package com.launchers.teslalauncherv2.ai

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Dummy state class for the Standard (No-AI) flavor
data class AiVisionState(
    val detectedSpeedLimit: Int? = null,
    val warningMessage: String? = null,
    val rawText: String = "",
    val lastUpdate: Long = 0L
)

// Dummy manager that satisfies the compiler but does no actual processing
object AiManager {
    // Flag to let the rest of the app know AI is physically not included
    val isAiSupported = false

    private val _visionState = MutableStateFlow(AiVisionState())
    val visionState: StateFlow<AiVisionState> = _visionState.asStateFlow()

    fun initialize(context: Context) {
        // Do nothing in Standard version
    }

    suspend fun analyzeFrame(bitmap: Bitmap): String {
        // Return immediately without processing
        return "AI Module not included in Standard build."
    }

    fun close() {
        // Do nothing in Standard version
    }
}