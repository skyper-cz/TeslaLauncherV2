package com.launchers.teslalauncherv2.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MusicService : NotificationListenerService() {

    private lateinit var mediaSessionManager: MediaSessionManager
    private var currentController: MediaController? = null

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

        val component = ComponentName(this, MusicService::class.java)

        // Zaregistrujeme se k odběru změn přehrávačů
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener({ controllers ->
                updateControllers(controllers)
            }, component)

            // Načteme ten aktuální hned po startu
            updateControllers(mediaSessionManager.getActiveSessions(component))
        } catch (e: SecurityException) {
            // Aplikace ještě nemá oprávnění od uživatele
        }
    }

    private fun updateControllers(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) return

        // Odpojíme se od starého přehrávače
        currentController?.unregisterCallback(mediaControllerCallback)

        // Najdeme ten, který právě hraje (případně vezmeme první dostupný)
        currentController = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()

        currentController?.registerCallback(mediaControllerCallback)
        updateCurrentTrack(currentController)
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateCurrentTrack(currentController)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateCurrentTrack(currentController)
        }
    }

    private fun updateCurrentTrack(controller: MediaController?) {
        if (controller == null) return
        val metadata = controller.metadata
        val state = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Neznámá skladba"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Neznámý interpret"

        // Některé aplikace ukládají obal pod ALBUM_ART, jiné pod ART
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        MediaManager.updateTrack(title, artist, albumArt, isPlaying)
    }

    override fun onDestroy() {
        currentController?.unregisterCallback(mediaControllerCallback)
        super.onDestroy()
    }

    // Tyto dvě metody musí být implementované, ale necháme je prázdné
    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}