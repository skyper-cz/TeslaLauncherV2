package com.launchers.teslalauncherv2.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

// Background service intercepting active media sessions across the system
class MusicService : NotificationListenerService() {

    private lateinit var mediaSessionManager: MediaSessionManager
    private var currentController: MediaController? = null

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

        val component = ComponentName(this, MusicService::class.java)

        try {
            // Listen for apps opening/closing media sessions
            mediaSessionManager.addOnActiveSessionsChangedListener({ controllers ->
                updateControllers(controllers)
            }, component)

            // Capture initial state on startup
            updateControllers(mediaSessionManager.getActiveSessions(component))
        } catch (e: SecurityException) {
            // Fails silently if user has not yet granted Notification Access
        }
    }

    // Finds the active media player (e.g., Spotify) and attaches listeners to it
    private fun updateControllers(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) return

        currentController?.unregisterCallback(mediaControllerCallback)

        // Prioritize actively playing sessions
        currentController = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()

        currentController?.registerCallback(mediaControllerCallback)
        updateCurrentTrack(currentController)
    }

    // Callback responding to track skips or play/pause toggles
    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateCurrentTrack(currentController)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateCurrentTrack(currentController)
        }
    }

    // Extracts text and image data from the active session and pushes it to MediaManager
    private fun updateCurrentTrack(controller: MediaController?) {
        if (controller == null) return
        val metadata = controller.metadata
        val state = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Neznámá skladba"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Neznámý interpret"

        // Some apps use ALBUM_ART, others use ART
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        MediaManager.updateTrack(title, artist, albumArt, isPlaying)
    }

    override fun onDestroy() {
        currentController?.unregisterCallback(mediaControllerCallback)
        super.onDestroy()
    }

    // Required by NotificationListenerService but unused for media
    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}