package com.launchers.teslalauncherv2.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Data class representing an installed application on the device
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val intent: Intent
)

// Singleton manager responsible for loading and caching the list of installed apps
object AppManager {
    // In-memory cache to prevent slow, repeated queries to the Android PackageManager
    private var cachedApps: List<AppInfo> = emptyList()

    // Silently loads all launchable apps in the background during application startup
    suspend fun prefetchApps(context: Context) = withContext(Dispatchers.IO) {
        if (cachedApps.isEmpty()) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolveInfos = pm.queryIntentActivities(intent, 0)

            cachedApps = resolveInfos.map { resolveInfo ->
                val label = resolveInfo.loadLabel(pm).toString()
                val packageName = resolveInfo.activityInfo.packageName
                val drawable = resolveInfo.loadIcon(pm)
                val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: Intent()

                AppInfo(label, packageName, drawableToImageBitmap(drawable), launchIntent)
            }
                .filter { it.packageName != context.packageName } // Exclude the launcher itself
                .sortedBy { it.label.lowercase() }
        }
    }

    // Instantly returns the cached list for the App Drawer UI
    fun getApps(): List<AppInfo> = cachedApps

    // Helper function to convert Android Vector/Bitmap Drawables into Compose-friendly ImageBitmaps
    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap.asImageBitmap()
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}