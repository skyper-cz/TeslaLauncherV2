package com.launchers.teslalauncherv2.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.launchers.teslalauncherv2.R
import com.launchers.teslalauncherv2.data.NavInstruction
import com.launchers.teslalauncherv2.data.SearchSuggestion
import com.mapbox.geojson.Point
import com.mapbox.geojson.LineString
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

object NetworkManager {
    private val client = OkHttpClient()
    private var currentSearchCall: Call? = null

    private fun runOnMain(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    private fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    // ==========================================
    // 1. MAPBOX SERVICES
    // ==========================================

    fun fetchSuggestions(query: String, context: Context, onResult: (List<SearchSuggestion>) -> Unit) {
        if (!isOnline(context)) {
            runOnMain {
                Toast.makeText(context, "Offline: Cannot search", Toast.LENGTH_SHORT).show()
                onResult(emptyList())
            }
            return
        }

        currentSearchCall?.cancel()
        val accessToken = context.getString(R.string.mapbox_access_token)
        val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/${query}.json?access_token=$accessToken&limit=5&autocomplete=true"
        val request = Request.Builder().url(url).build()

        currentSearchCall = client.newCall(request)
        currentSearchCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
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

    // 🌟 MAPBOX ROUTE: Nyní vrací i seznam limitů (4. parametr)
    fun fetchRouteManual(origin: Point, destination: Point, context: Context, onRouteFound: (String?, List<NavInstruction>, Int?, List<Int?>) -> Unit) {
        if (!isOnline(context)) {
            runOnMain { Toast.makeText(context, "Offline: Route requires internet", Toast.LENGTH_LONG).show() }
            runOnMain { onRouteFound(null, emptyList(), null, emptyList()) }
            return
        }

        val accessToken = context.getString(R.string.mapbox_access_token)

        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("api.mapbox.com")
            .addPathSegment("directions")
            .addPathSegment("v5")
            .addPathSegment("mapbox")
            .addPathSegment("driving-traffic")
            .addPathSegment("${origin.longitude()},${origin.latitude()};${destination.longitude()},${destination.latitude()}")
            .addQueryParameter("geometries", "geojson")
            .addQueryParameter("steps", "true")
            .addQueryParameter("overview", "full")
            .addQueryParameter("annotations", "maxspeed,congestion") // 🌟 PŘIDÁNA CONGESTION
            .addQueryParameter("access_token", accessToken)

        val url = urlBuilder.build().toString()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnMain { onRouteFound(null, emptyList(), null, emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        runOnMain { onRouteFound(null, emptyList(), null, emptyList()) }
                        return
                    }
                    val jsonString = it.body?.string() ?: return
                    try {
                        val json = JSONObject(jsonString)
                        val routes = json.optJSONArray("routes")
                        if (routes != null && routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val durationSeconds = route.optDouble("duration", 0.0).toInt()
                            val geometry = route.getJSONObject("geometry")

                            val legs = route.getJSONArray("legs")
                            var maxspeedArray: org.json.JSONArray? = null
                            var congestionArray: org.json.JSONArray? = null

                            if (legs.length() > 0) {
                                val annotation = legs.getJSONObject(0).optJSONObject("annotation")
                                maxspeedArray = annotation?.optJSONArray("maxspeed")
                                congestionArray = annotation?.optJSONArray("congestion")
                            }

                            // 🌟 ROZSEKÁNÍ TRASY NA SEGMENTY PRO OBARVENÍ
                            val coordinates = geometry.getJSONArray("coordinates")
                            val featuresArray = org.json.JSONArray()

                            for (i in 0 until coordinates.length() - 1) {
                                val p1 = coordinates.getJSONArray(i)
                                val p2 = coordinates.getJSONArray(i + 1)
                                val level = congestionArray?.optString(i, "unknown") ?: "unknown"

                                val segmentCoords = org.json.JSONArray().apply { put(p1); put(p2) }
                                val segmentGeometry = JSONObject().apply {
                                    put("type", "LineString")
                                    put("coordinates", segmentCoords)
                                }
                                val properties = JSONObject().apply {
                                    put("congestion", level)
                                }
                                val feature = JSONObject().apply {
                                    put("type", "Feature")
                                    put("properties", properties)
                                    put("geometry", segmentGeometry)
                                }
                                featuresArray.put(feature)
                            }

                            val featureCollection = JSONObject().apply {
                                put("type", "FeatureCollection")
                                put("features", featuresArray)
                            }
                            val featureString = featureCollection.toString()

                            // 🌟 PARSOVÁNÍ RYCHLOSTÍ
                            val speedLimits = mutableListOf<Int?>()
                            if (maxspeedArray != null) {
                                for (k in 0 until maxspeedArray.length()) {
                                    val item = maxspeedArray.optJSONObject(k)
                                    if (item != null) {
                                        val speed = item.optInt("speed", 0)
                                        speedLimits.add(if (speed > 0) speed else null)
                                    } else if (maxspeedArray.optString(k) == "none") {
                                        speedLimits.add(-1)
                                    } else {
                                        speedLimits.add(null)
                                    }
                                }
                            }

                            val navInstructions = mutableListOf<NavInstruction>()
                            if (legs.length() > 0) {
                                val steps = legs.getJSONObject(0).getJSONArray("steps")
                                for (i in 0 until steps.length()) {
                                    val step = steps.getJSONObject(i)
                                    val maneuver = step.getJSONObject("maneuver")
                                    val location = maneuver.getJSONArray("location")
                                    val maneuverPoint = Point.fromLngLat(location.getDouble(0), location.getDouble(1))
                                    val text = maneuver.getString("instruction")
                                    val distance = step.getDouble("distance").toInt()
                                    val modifier = if (maneuver.has("modifier")) maneuver.getString("modifier") else null

                                    if (distance > 0 || i == steps.length() - 1) {
                                        navInstructions.add(NavInstruction(text, distance, modifier, maneuverPoint))
                                    }
                                }
                            }

                            if (navInstructions.size > 1 && navInstructions.first().distance < 10) {
                                navInstructions.removeAt(0)
                            }

                            runOnMain { onRouteFound(featureString, navInstructions, durationSeconds, speedLimits) }
                        } else {
                            runOnMain { onRouteFound(null, emptyList(), null, emptyList()) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnMain { onRouteFound(null, emptyList(), null, emptyList()) }
                    }
                }
            }
        })
    }


    // ==========================================
    // 2. GOOGLE MAPS SERVICES
    // ==========================================

    fun fetchGoogleSuggestions(query: String, apiKey: String, onResult: (List<SearchSuggestion>) -> Unit) {
        val url = "https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(query, "UTF-8")}&key=$apiKey"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
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
                        val jsonArray = json.optJSONArray("results")
                        if (jsonArray != null) {
                            for (i in 0 until Math.min(jsonArray.length(), 5)) {
                                val item = jsonArray.getJSONObject(i)
                                val name = item.getString("formatted_address")
                                val loc = item.getJSONObject("geometry").getJSONObject("location")
                                val point = Point.fromLngLat(loc.getDouble("lng"), loc.getDouble("lat"))
                                results.add(SearchSuggestion(name, point))
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }

                    runOnMain { onResult(results) }
                }
            }
        })
    }

    // 🌟 GOOGLE ROUTE: Teď také přijímá 4 parametry v callbacku (aby to sedělo s UI)
    fun fetchGoogleRoute(start: Point, end: Point, apiKey: String, onRouteFound: (String?, List<NavInstruction>, Int?, List<Int?>) -> Unit) {
        val url = "https://maps.googleapis.com/maps/api/directions/json?origin=${start.latitude()},${start.longitude()}&destination=${end.latitude()},${end.longitude()}&key=$apiKey"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnMain { onRouteFound(null, emptyList(), null, emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        runOnMain { onRouteFound(null, emptyList(), null, emptyList()) }
                        return
                    }
                    val jsonString = it.body?.string() ?: return
                    try {
                        val json = JSONObject(jsonString)
                        val routes = json.optJSONArray("routes")

                        if (routes != null && routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val polyline = route.getJSONObject("overview_polyline").getString("points")

                            val lineString = LineString.fromPolyline(polyline, 5)
                            val geometryJson = lineString.toJson()
                            val featureString = """{ "type": "FeatureCollection", "features": [{ "type": "Feature", "properties": {}, "geometry": $geometryJson }] }"""

                            val navInstructions = mutableListOf<NavInstruction>()
                            var durationSeconds = 0

                            val legs = route.getJSONArray("legs")
                            if (legs.length() > 0) {
                                val leg = legs.getJSONObject(0)
                                durationSeconds = leg.getJSONObject("duration").getInt("value")

                                val steps = leg.getJSONArray("steps")
                                for (i in 0 until steps.length()) {
                                    val step = steps.getJSONObject(i)
                                    val text = step.getString("html_instructions").replace(Regex("<.*?>"), "")
                                    val distance = step.getJSONObject("distance").getInt("value")
                                    val endLoc = step.getJSONObject("end_location")
                                    val maneuverPoint = Point.fromLngLat(endLoc.getDouble("lng"), endLoc.getDouble("lat"))

                                    navInstructions.add(NavInstruction(text, distance, null, maneuverPoint))
                                }
                            }

                            // 🌟 Vracíme prázdný seznam limitů (Google API je v základu neumí)
                            runOnMain { onRouteFound(featureString, navInstructions, durationSeconds, emptyList()) }
                        } else {
                            runOnMain { onRouteFound(null, emptyList(), null, emptyList()) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnMain { onRouteFound(null, emptyList(), null, emptyList()) }
                    }
                }
            }
        })
    }
}