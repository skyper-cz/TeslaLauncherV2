package com.launchers.teslalauncherv2.media

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Data class representing currently playing media
data class TrackInfo(
    val title: String = "No Media Playing",
    val artist: String = "Tap to grant permission",
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false
)

// Singleton state holder for the UI to observe media changes
object MediaManager {
    private val _currentTrack = MutableStateFlow(TrackInfo())
    val currentTrack: StateFlow<TrackInfo> = _currentTrack

    fun updateTrack(title: String, artist: String, albumArt: Bitmap?, isPlaying: Boolean) {
        _currentTrack.value = TrackInfo(title, artist, albumArt, isPlaying)
    }
}