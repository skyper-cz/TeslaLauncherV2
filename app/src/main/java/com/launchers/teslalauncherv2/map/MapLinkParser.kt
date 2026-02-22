package com.launchers.teslalauncherv2.map

import android.net.Uri
import android.util.Log
import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object MapLinkParser {

    /**
     * Zpracuje příchozí intent a pokusí se z něj vytáhnout GPS souřadnice cíle.
     * Protože to může zahrnovat síťový dotaz (rozbalení goo.gl odkazu), běží to v suspend funkci.
     */
    suspend fun parseIntentForDestination(uri: Uri?): Point? = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext null

        Log.d("MapLinkParser", "Processing incoming map URI: $uri")
        val urlString = uri.toString()

        try {
            // 1. Ošetření geo: URI (např. geo:37.7749,-122.4194?q=San+Francisco)
            if (urlString.startsWith("geo:")) {
                val coords = urlString.substringAfter("geo:").substringBefore("?").split(",")
                if (coords.size == 2) {
                    val lat = coords[0].toDoubleOrNull()
                    val lng = coords[1].toDoubleOrNull()
                    if (lat != null && lng != null) {
                        return@withContext Point.fromLngLat(lng, lat)
                    }
                }
            }

            // 2. Ošetření zkrácených odkazů (goo.gl, maps.app.goo.gl) - musíme je rozbalit
            var finalUrl = urlString
            if (urlString.contains("goo.gl")) {
                finalUrl = expandShortUrl(urlString)
                Log.d("MapLinkParser", "Expanded Short URL to: $finalUrl")
            }

            // 3. Ošetření plného Google Maps odkazu
            // Typický formát: https://www.google.com/maps/place/.../@50.088,14.42,15z...
            val atIndex = finalUrl.indexOf("/@")
            if (atIndex != -1) {
                val coordsPart = finalUrl.substring(atIndex + 2).substringBefore(",")
                val coords = finalUrl.substring(atIndex + 2).split(",")
                if (coords.size >= 2) {
                    val lat = coords[0].toDoubleOrNull()
                    val lng = coords[1].toDoubleOrNull()
                    if (lat != null && lng != null) {
                        return@withContext Point.fromLngLat(lng, lat)
                    }
                }
            }

            // Záložní pokus pro odkazy typu ?q=lat,lng
            val queryParam = Uri.parse(finalUrl).getQueryParameter("q")
            if (queryParam != null) {
                val coords = queryParam.split(",")
                if (coords.size == 2) {
                    val lat = coords[0].toDoubleOrNull()
                    val lng = coords[1].toDoubleOrNull()
                    if (lat != null && lng != null) {
                        return@withContext Point.fromLngLat(lng, lat)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("MapLinkParser", "Failed to parse map link", e)
        }

        return@withContext null
    }

    // Pomocná funkce pro zjištění plné cesty u zkracovačů (nepotřebuje stahovat tělo stránky)
    private fun expandShortUrl(shortUrl: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(shortUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false // Nechceme jít dál, chceme jen adresu přesměrování
            connection.connect()
            val expandedUrl = connection.getHeaderField("Location")
            return expandedUrl ?: shortUrl
        } catch (e: Exception) {
            return shortUrl
        } finally {
            connection?.disconnect()
        }
    }
}