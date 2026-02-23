package com.launchers.teslalauncherv2.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class AiVisionState(
    val detectedSpeedLimit: Int? = null,
    val warningMessage: String? = null,
    val rawText: String = "", // Pridano pro sledovani, co AI realne cte
    val lastUpdate: Long = 0L
)

object AiManager {
    val isAiSupported = true

    private val _visionState = MutableStateFlow(AiVisionState())
    val visionState: StateFlow<AiVisionState> = _visionState.asStateFlow()

    // Inicializace ML Kitu
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Seznam povolených EU limitů
    private val validSpeedLimits = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130)

    fun initialize(context: Context) {
        Log.i("AI_MANAGER", "Initializing ML Kit Vision Engine...")
    }

    suspend fun analyzeFrame(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    var detectedLimit: Int? = null

                    // Vezmeme veskery rozpoznany text a dame ho do jednoho radku pro debug panel
                    val allReadText = visionText.text.replace("\n", " | ")

                    for (block in visionText.textBlocks) {
                        val text = block.text.trim()
                        val possibleNumber = text.toIntOrNull()

                        if (possibleNumber != null && validSpeedLimits.contains(possibleNumber)) {
                            detectedLimit = possibleNumber
                            break
                        }
                    }

                    // Zapiseme stav do UI vzdy (i kdyz nenajde limit), abychom videli rawText a posunuli cas
                    _visionState.value = AiVisionState(
                        detectedSpeedLimit = detectedLimit,
                        rawText = allReadText,
                        lastUpdate = System.currentTimeMillis()
                    )

                    if (detectedLimit != null) {
                        if (continuation.isActive) continuation.resume("AI Detected: $detectedLimit")
                    } else {
                        if (continuation.isActive) continuation.resume("AI Scanned: $allReadText")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("AI_MANAGER", "Error analyzing frame", e)
                    if (continuation.isActive) continuation.resume("AI Error: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("AI_MANAGER", "Image processing error", e)
            if (continuation.isActive) continuation.resume("AI Setup Error: ${e.message}")
        }
    }

    fun close() {
        Log.i("AI_MANAGER", "Closing AI engine...")
        recognizer.close()
    }
}