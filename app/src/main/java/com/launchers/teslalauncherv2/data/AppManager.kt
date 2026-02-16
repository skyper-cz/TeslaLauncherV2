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

// Jak vypadá jedna aplikace v našem seznamu
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val intent: Intent
)

object AppManager {
    // Funkce, která prohledá tablet a vrátí seřazený seznam aplikací
    suspend fun getInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // Najdeme všechny aplikace, které se dají spustit
        val resolveInfos = pm.queryIntentActivities(intent, 0)

        resolveInfos.map { resolveInfo ->
            val label = resolveInfo.loadLabel(pm).toString()
            val packageName = resolveInfo.activityInfo.packageName
            val drawable = resolveInfo.loadIcon(pm)
            val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: Intent()

            AppInfo(label, packageName, drawableToImageBitmap(drawable), launchIntent)
        }
            .filter { it.packageName != context.packageName } // Skryje náš vlastní Tesla Launcher ze seznamu
            .sortedBy { it.label.lowercase() } // Seřadí podle abecedy
    }

    // Pomocná funkce: Převod starých Android ikonek do moderní Compose grafiky
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