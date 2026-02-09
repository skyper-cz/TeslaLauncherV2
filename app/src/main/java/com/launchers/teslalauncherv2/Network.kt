package com.launchers.teslalauncherv2

import android.content.Context
import com.mapbox.geojson.Point
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

// Funkce pro Našeptávač
fun fetchSuggestions(query: String, context: Context, onResult: (List<SearchSuggestion>) -> Unit) {
    val accessToken = context.getString(R.string.mapbox_access_token)
    val client = OkHttpClient()
    val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/${query}.json?access_token=$accessToken&limit=5&autocomplete=true"
    val request = Request.Builder().url(url).build()

    client.newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) { e.printStackTrace() }
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            response.body?.string()?.let { jsonString ->
                try {
                    val json = JSONObject(jsonString)
                    val features = json.optJSONArray("features")
                    val results = mutableListOf<SearchSuggestion>()
                    if (features != null) {
                        for (i in 0 until features.length()) {
                            val feature = features.getJSONObject(i)
                            val name = feature.getString("place_name")
                            val center = feature.getJSONArray("center")
                            val point = Point.fromLngLat(center.getDouble(0), center.getDouble(1))
                            results.add(SearchSuggestion(name, point))
                        }
                    }
                    onResult(results)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    })
}

// Funkce pro Trasu + Instrukce
fun fetchRouteManual(origin: Point, destination: Point, context: Context, onRouteFound: (String, NavInstruction?) -> Unit) {
    val accessToken = context.getString(R.string.mapbox_access_token)
    val client = OkHttpClient()
    val url = "https://api.mapbox.com/directions/v5/mapbox/driving-traffic/" +
            "${origin.longitude()},${origin.latitude()};${destination.longitude()},${destination.latitude()}" +
            "?geometries=geojson&steps=true&overview=full&access_token=$accessToken"
    val request = Request.Builder().url(url).build()

    client.newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) { e.printStackTrace() }
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            response.body?.string()?.let { jsonString ->
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
                            if (steps.length() > 1) {
                                val step = steps.getJSONObject(1)
                                val maneuver = step.getJSONObject("maneuver")
                                val text = maneuver.getString("instruction")
                                val distance = step.getDouble("distance").toInt()
                                val modifier = if (maneuver.has("modifier")) maneuver.getString("modifier") else null
                                navInstruction = NavInstruction(text, distance, modifier)
                            } else if (steps.length() > 0) {
                                val step = steps.getJSONObject(0)
                                val maneuver = step.getJSONObject("maneuver")
                                val text = maneuver.getString("instruction")
                                val distance = step.getDouble("distance").toInt()
                                navInstruction = NavInstruction(text, distance, null)
                            }
                        }
                        onRouteFound(featureString, navInstruction)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    })
}