package com.launchers.teslalauncherv2.hardware // Uprav si package podle toho, kam to dáš

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DashcamManager {
    private const val TAG = "DashcamManager"

    // Maximální povolená velikost složky (1 GB = 1024 * 1024 * 1024 bytů)
    // Pokud chceš víc, změň to třeba na 2L * 1024 * 1024 * 1024 pro 2 GB.
    private const val MAX_FOLDER_SIZE_BYTES = 2L * 1024 * 1024 * 1024

    /**
     * Vrátí cestu k novému souboru, kam se má uložit aktuální nahrávka.
     * Zároveň zkontroluje a promaže staré soubory.
     */
    fun getNewOutputFilePath(context: Context): String? {
        val dashcamDir = getDashcamDirectory(context) ?: return null

        // Zkontrolujeme a promažeme staré záznamy (Loop recording logika)
        maintainStorageLimit(dashcamDir)

        // Vygenerujeme název souboru podle aktuálního času
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val newFile = File(dashcamDir, "dashcam_$timestamp.mp4")

        Log.d(TAG, "Prepared new dashcam file: ${newFile.absolutePath}")
        return newFile.absolutePath
    }

    private fun getDashcamDirectory(context: Context): File? {
        // Ukládáme do veřejné složky telefonu (Filmy), aby se na to uživatel mohl podívat z galerie
        val externalMoviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        if (externalMoviesDir != null) {
            val dashcamDir = File(externalMoviesDir, "TeslaDashcam")
            if (!dashcamDir.exists()) {
                dashcamDir.mkdirs()
            }
            return dashcamDir
        }
        Log.e(TAG, "Cannot access external storage for Movies")
        return null
    }

    /**
     * Zjistí velikost složky a maže nejstarší soubory, dokud se nevejde do limitu.
     */
    private fun maintainStorageLimit(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return

        val files = dir.listFiles()?.filter { it.extension == "mp4" }?.toMutableList() ?: return

        // Spočítáme aktuální velikost všech videí
        var totalSize = files.sumOf { it.length() }

        if (totalSize < MAX_FOLDER_SIZE_BYTES) {
            return // Místa je dost, nic nemažeme
        }

        Log.w(TAG, "Storage limit reached ($totalSize bytes). Initiating cleanup loop.")

        // Seřadíme soubory od nejstaršího (upraveno lastModified) po nejnovější
        files.sortBy { it.lastModified() }

        // Mažeme postupně ty nejstarší, dokud se nedostaneme pod 80 % limitu (aby bylo místo na to nové)
        val targetSize = (MAX_FOLDER_SIZE_BYTES * 0.8).toLong()

        val iterator = files.iterator()
        while (iterator.hasNext() && totalSize > targetSize) {
            val fileToDelete = iterator.next()
            val fileSize = fileToDelete.length()

            if (fileToDelete.delete()) {
                Log.d(TAG, "Deleted old video to save space: ${fileToDelete.name}")
                totalSize -= fileSize
            } else {
                Log.e(TAG, "Failed to delete old video: ${fileToDelete.name}")
            }
        }
    }
}