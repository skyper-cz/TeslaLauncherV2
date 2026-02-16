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
    // 1. MAPBOX SLUŽBY
    // ==========================================

    fun fetchSuggestions(query: String, context: Context, onResult: (List<SearchSuggestion>) -> Unit) {
        if (!isOnline(context)) {
            runOnMain {
                Toast.makeText(context, "Offline: Nelze vyhledávat", Toast.LENGTH_SHORT).show()
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

    // OPRAVA ZDE: onRouteFound nyní přijímá i Int? (duration)
    fun fetchRouteManual(origin: Point, destination: Point, context: Context, onRouteFound: (String?, NavInstruction?, Int?) -> Unit) {
        if (!isOnline(context)) {
            runOnMain { Toast.makeText(context, "Offline: Trasa vyžaduje internet", Toast.LENGTH_LONG).show() }
            runOnMain { onRouteFound(null, null, null) }
            return
        }

        val accessToken = context.getString(R.string.mapbox_access_token)
        val url = "https://api.mapbox.com/directions/v5/mapbox/driving-traffic/" +
                "${origin.longitude()},${origin.latitude()};${destination.longitude()},${destination.latitude()}" +
                "?geometries=geojson&steps=true&overview=full&access_token=$accessToken"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnMain { onRouteFound(null, null, null) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        runOnMain { onRouteFound(null, null, null) }
                        return
                    }
                    val jsonString = it.body?.string() ?: return
                    try {
                        val json = JSONObject(jsonString)
                        val routes = json.optJSONArray("routes")
                        if (routes != null && routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val geometry = route.getJSONObject("geometry")
                            val featureString = """{ "type": "FeatureCollection", "features": [{ "type": "Feature", "properties": {}, "geometry": $geometry }] }"""

                            // Získání celkového času v sekundách
                            val durationSeconds = route.optDouble("duration", 0.0).toInt()

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

                                    navInstruction =
                                        NavInstruction(text, distance, modifier, maneuverPoint)
                                }
                            }
                            // Odesíláme všechny 3 hodnoty!
                            runOnMain { onRouteFound(featureString, navInstruction, durationSeconds) }
                        } else {
                            runOnMain { onRouteFound(null, null, null) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnMain { onRouteFound(null, null, null) }
                    }
                }
            }
        })
    }


    // ==========================================
    // 2. GOOGLE MAPS SLUŽBY
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

    // OPRAVA ZDE: onRouteFound nyní přijímá i Int? (duration)
    fun fetchGoogleRoute(start: Point, end: Point, apiKey: String, onRouteFound: (String?, NavInstruction?, Int?) -> Unit) {
        val url = "https://maps.googleapis.com/maps/api/directions/json?origin=${start.latitude()},${start.longitude()}&destination=${end.latitude()},${end.longitude()}&key=$apiKey"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnMain { onRouteFound(null, null, null) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        runOnMain { onRouteFound(null, null, null) }
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

                            var navInstruction: NavInstruction? = null
                            var durationSeconds = 0

                            val legs = route.getJSONArray("legs")
                            if (legs.length() > 0) {
                                val leg = legs.getJSONObject(0)
                                // Získání celkového času od Googlu
                                durationSeconds = leg.getJSONObject("duration").getInt("value")

                                val steps = leg.getJSONArray("steps")
                                if (steps.length() > 0) {
                                    val step = steps.getJSONObject(0)
                                    val text = step.getString("html_instructions").replace(Regex("<.*?>"), "")
                                    val distance = step.getJSONObject("distance").getInt("value")
                                    val endLoc = step.getJSONObject("end_location")
                                    val maneuverPoint = Point.fromLngLat(endLoc.getDouble("lng"), endLoc.getDouble("lat"))

                                    navInstruction =
                                        NavInstruction(text, distance, null, maneuverPoint)
                                }
                            }
                            // Odesíláme všechny 3 hodnoty!
                            runOnMain { onRouteFound(featureString, navInstruction, durationSeconds) }
                        } else {
                            runOnMain { onRouteFound(null, null, null) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnMain { onRouteFound(null, null, null) }
                    }
                }
            }
        })
    }
}