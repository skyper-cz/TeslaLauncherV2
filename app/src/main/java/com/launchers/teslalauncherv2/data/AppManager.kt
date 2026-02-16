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

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val intent: Intent
)

object AppManager {
    // Zde si držíme aplikace v paměti
    private var cachedApps: List<AppInfo> = emptyList()

    // Tato funkce se zavolá tiše na pozadí hned po startu Launcheru
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
                .filter { it.packageName != context.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }

    // Tato funkce už jen okamžitě vrátí hotový seznam z paměti
    fun getApps(): List<AppInfo> = cachedApps

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