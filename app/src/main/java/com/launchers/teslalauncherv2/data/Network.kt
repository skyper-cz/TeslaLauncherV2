package com.launchers.teslalauncherv2.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.launchers.teslalauncherv2.R
import com.mapbox.geojson.Point
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object NetworkManager {
    private val client = OkHttpClient()

    // DRŽÁK NA POSLEDNÍ HLEDÁNÍ (OPTIMALIZACE)
    private var currentSearchCall: Call? = null

    // Pomocná funkce
    private fun runOnMain(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    // --- 1. NAŠEPTÁVAČ (S AUTOMATICKÝM ZRUŠENÍM STARÉHO) ---
    fun fetchSuggestions(query: String, context: Context, onResult: (List<SearchSuggestion>) -> Unit) {
        // KROK 1: Zrušíme jakýkoliv předchozí běžící dotaz!
        currentSearchCall?.cancel()

        val accessToken = context.getString(R.string.mapbox_access_token)
        val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/${query}.json?access_token=$accessToken&limit=5&autocomplete=true"
        val request = Request.Builder().url(url).build()

        // Uložíme si tento call jako aktuální
        currentSearchCall = client.newCall(request)

        currentSearchCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Pokud byla chyba způsobena zrušením (Canceled), ignorujeme ji
                if (call.isCanceled()) return
                e.printStackTrace()
                runOnMain { onResult(emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) return
                    val jsonString = it.body?.string() ?: return
                    val results = mutableListOf<SearchSuggestion>()
                    try {
                        val json = JSONObject(jsonString)
                        val features = json.optJSONArray("features")
                        if (features != null) {
                            for (i in 0 until features.length()) {
                                val feature = features.getJSONObject(i)
                                val name = feature.getString("place_name")
                                val center = feature.getJSONArray("center")
                                val point = Point.fromLngLat(center.getDouble(0), center.getDouble(1))
                                results.add(SearchSuggestion(name, point))
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }

                    runOnMain { onResult(results) }
                }
            }
        })
    }

    // --- 2. TRASA ---
    fun fetchRouteManual(origin: Point, destination: Point, context: Context, onRouteFound: (String, NavInstruction?) -> Unit) {
        val accessToken = context.getString(R.string.mapbox_access_token)
        val url = "https://api.mapbox.com/directions/v5/mapbox/driving-traffic/" +
                "${origin.longitude()},${origin.latitude()};${destination.longitude()},${destination.latitude()}" +
                "?geometries=geojson&steps=true&overview=full&access_token=$accessToken"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { e.printStackTrace() }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) return
                    val jsonString = it.body?.string() ?: return
                    try {
                        val json = JSONObject(jsonString)
                        val routes = json.optJSONArray("routes")
                        if (routes != null && routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val geometry = route.getJSONObject("geometry")
                            val featureString = """{ "type": "Feature", "properties": {}, "geometry": $geometry }"""

                            var navInstruction: NavInstruction? = null
                            val legs = route.getJSONArray("legs")
                            if (legs.length() > 0) {
                                val steps = legs.getJSONObject(0).getJSONArray("steps")
                                val stepIndex = if (steps.length() > 1) 1 else 0
                                if (steps.length() > 0) {
                                    val step = steps.getJSONObject(stepIndex)
                                    val maneuver = step.getJSONObject("maneuver")
                                    val location = maneuver.getJSONArray("location")
                                    val maneuverPoint = Point.fromLngLat(location.getDouble(0), location.getDouble(1))
                                    val text = maneuver.getString("instruction")
                                    val distance = step.getDouble("distance").toInt()
                                    val modifier = if (maneuver.has("modifier")) maneuver.getString("modifier") else null
                                    navInstruction = NavInstruction(text, distance, modifier, maneuverPoint)
                                }
                            }
                            runOnMain { onRouteFound(featureString, navInstruction) }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        })
    }
}