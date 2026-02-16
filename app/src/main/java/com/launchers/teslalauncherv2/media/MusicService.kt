package com.launchers.teslalauncherv2.media

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MusicService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        processNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Pokud zmizí notifikace aktuálně hrající aplikace, vymažeme info
        if (sbn?.packageName == MediaManager.currentTrack.value.packageName) {
            MediaManager.clear()
        }
    }

    private fun processNotification(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getString(Notification.EXTRA_TEXT) // Obvykle interpret

        // Zkusíme vytáhnout MediaSession token (to je klíč k ovládání)
        val token = extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)

        if (token != null || isMusicApp(sbn.packageName)) {
            // Zkusíme získat obal alba
            var albumArt: Bitmap? = null
            try {
                val largeIcon = sbn.notification.getLargeIcon()
                if (largeIcon != null) {
                    // Načtení bitmapy z ikony (může vyžadovat kontext)
                    albumArt = drawableToBitmap(largeIcon.loadDrawable(this))
                } else {
                    val bmp = extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
                    if (bmp != null) albumArt = bmp
                }
            } catch (e: Exception) { Log.e("MusicService", "Error loading art: $e") }

            // Aktualizujeme data
            MediaManager.updateTrack(
                title = title ?: "Unknown Title",
                artist = text ?: "Unknown Artist",
                albumArt = albumArt,
                isPlaying = true, // Předpokládáme, že když je notifikace, tak to hraje (nebo je pauza)
                packageName = sbn.packageName
            )
        }
    }

    private fun isMusicApp(pkg: String): Boolean {
        return pkg.contains("spotify") || pkg.contains("music") || pkg.contains("youtube") || pkg.contains("audible")
    }

    // Pomocná funkce pro převod Drawable na Bitmap
    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) return drawable.bitmap

        try {
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        } catch (e: Exception) { return null }
    }
}