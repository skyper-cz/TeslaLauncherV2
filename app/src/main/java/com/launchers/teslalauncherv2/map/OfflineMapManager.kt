package com.launchers.teslalauncherv2.map

import android.content.Context
import android.util.Log
import com.launchers.teslalauncherv2.R
import com.mapbox.common.NetworkRestriction
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileRegionLoadProgress
import com.mapbox.common.TileStore
import com.mapbox.geojson.Geometry
import com.mapbox.maps.GlyphsRasterizationMode
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.StylePackLoadOptions
import com.mapbox.maps.TilesetDescriptorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Singleton managing Mapbox offline region downloads
object OfflineMapManager {

    // Initiates the download of a specific geographic area for offline use
    fun downloadRegion(
        context: Context,
        regionId: String,
        geometry: Geometry, // Allows downloading custom shapes (bounding boxes or polygons)
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tileStore = TileStore.create()
                val offlineManager = OfflineManager()
                val accessToken = context.getString(R.string.mapbox_access_token)

                // 1. Prepare style options (dark mode)
                val stylePackOptions = StylePackLoadOptions.Builder()
                    .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
                    .acceptExpired(true)
                    .build()

                offlineManager.loadStylePack(
                    Style.Companion.TRAFFIC_NIGHT,
                    stylePackOptions,
                    { },
                    { expected -> if (expected.isError) Log.e("MapboxOffline", "Style error: ${expected.error}") }
                )

                // 2. Define zoom levels to download (0-13 covers routing without eating up all storage)
                val tilesetDescriptorOptions = TilesetDescriptorOptions.Builder()
                    .styleURI(Style.Companion.TRAFFIC_NIGHT)
                    .minZoom(0)
                    .maxZoom(13)
                    .build()

                val descriptor = offlineManager.createTilesetDescriptor(tilesetDescriptorOptions)

                // 3. Start fetching tiles within the provided geometry boundary
                val loadOptions = TileRegionLoadOptions.Builder()
                    .geometry(geometry)
                    .descriptors(listOf(descriptor))
                    .acceptExpired(true)
                    .networkRestriction(NetworkRestriction.NONE)
                    .build()

                tileStore.loadTileRegion(
                    regionId,
                    loadOptions,
                    { progress: TileRegionLoadProgress ->
                        // Calculate percentage based on completed vs required tiles
                        val percentage = if (progress.requiredResourceCount > 0) {
                            ((progress.completedResourceCount.toDouble() / progress.requiredResourceCount.toDouble()) * 100).toInt()
                        } else 0
                        CoroutineScope(Dispatchers.Main).launch { onProgress(percentage) }
                    },
                    { expected ->
                        CoroutineScope(Dispatchers.Main).launch {
                            if (expected.isError) onError(expected.error?.message ?: "Download Error")
                            else onComplete()
                        }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Crash during download") }
            }
        }
    }

    // Stub for deleting an offline region from local storage to free up space
    fun deleteRegion(regionId: String) {
        // Implementation depends on the specific Mapbox SDK version:
        // TileStore.create().remove(regionId)
    }
}