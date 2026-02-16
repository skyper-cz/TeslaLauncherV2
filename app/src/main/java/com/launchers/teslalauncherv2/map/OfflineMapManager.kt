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

object OfflineMapManager {

    fun downloadRegion(
        context: Context,
        regionId: String,
        geometry: Geometry, // ZMĚNA: Přijímáme libovolný tvar regionu
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tileStore = TileStore.create()
                val offlineManager = OfflineManager()
                val accessToken = context.getString(R.string.mapbox_access_token)

                // 1. Styl mapy
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

                // 2. Definice dlaždic a levelu zoomu
                val tilesetDescriptorOptions = TilesetDescriptorOptions.Builder()
                    .styleURI(Style.Companion.TRAFFIC_NIGHT)
                    .minZoom(0)
                    .maxZoom(13) // Zoom 13 je ideální kompromis mezi detailem a velikostí pro celá auta
                    .build()

                val descriptor = offlineManager.createTilesetDescriptor(tilesetDescriptorOptions)

                // 3. Spuštění stahování pro danou geometrii (vybraný stát/region)
                val loadOptions = TileRegionLoadOptions.Builder()
                    .geometry(geometry) // POUŽITÍ GEOMETRIE
                    .descriptors(listOf(descriptor))
                    .acceptExpired(true)
                    .networkRestriction(NetworkRestriction.NONE)
                    .build()

                tileStore.loadTileRegion(
                    regionId, // ID regionu (např. cz_moravia)
                    loadOptions,
                    { progress: TileRegionLoadProgress ->
                        val percentage = if (progress.requiredResourceCount > 0) {
                            ((progress.completedResourceCount.toDouble() / progress.requiredResourceCount.toDouble()) * 100).toInt()
                        } else 0
                        CoroutineScope(Dispatchers.Main).launch { onProgress(percentage) }
                    },
                    { expected ->
                        CoroutineScope(Dispatchers.Main).launch {
                            if (expected.isError) onError(expected.error?.message ?: "Chyba stahování")
                            else onComplete()
                        }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Crash při stahování") }
            }
        }
    }

    // Přidej tuto funkci do objektu OfflineMapManager
    fun deleteRegion(regionId: String) {
        // Zde záleží na tvé verzi Mapboxu, obvykle to vypadá nějak takto:
        // TileStore.create().remove(regionId)
        // nebo
        // OfflineManager().removeOfflineRegion(regionId)
    }
}