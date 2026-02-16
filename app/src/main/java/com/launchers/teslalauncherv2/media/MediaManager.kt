package com.launchers.teslalauncherv2.media

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaTrackInfo(
    val title: String = "No Music Playing",
    val artist: String = "Tap to connect",
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val packageName: String? = null // Abychom věděli, z jaké appky to jde
)

object MediaManager {
    private val _currentTrack = MutableStateFlow(MediaTrackInfo())
    val currentTrack: StateFlow<MediaTrackInfo> = _currentTrack.asStateFlow()

    fun updateTrack(title: String, artist: String, albumArt: Bitmap?, isPlaying: Boolean, packageName: String?) {
        _currentTrack.value = MediaTrackInfo(title, artist, albumArt, isPlaying, packageName)
    }

    fun clear() {
        _currentTrack.value = MediaTrackInfo()
    }
}