package com.launchers.teslalauncherv2.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.launchers.teslalauncherv2.data.NavInstruction
import com.launchers.teslalauncherv2.data.SearchSuggestion
import com.launchers.teslalauncherv2.map.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Mapbox Imports
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.FillExtrusionLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.mapbox.maps.plugin.PuckBearing

// Mapbox Style Expressions
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.eq
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.get
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.literal
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.match

// Google Maps Imports
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.Polyline

@Composable
fun Viewport(
    modifier: Modifier = Modifier,
    isNightPanel: Boolean = false,
    is3dModeExternal: Boolean = false,
    searchEngine: String,
    currentLocation: Location?,
    routeGeoJson: String?,
    onRouteGeoJsonUpdated: (String?) -> Unit,
    onInstructionUpdated: (List<NavInstruction>) -> Unit,
    onRouteDurationUpdated: (Int?) -> Unit,
    onSpeedLimitsUpdated: (List<Int?>) -> Unit,
    onDestinationSet: (Point?) -> Unit,
    onCancelRoute: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val mapViewportState = rememberMapViewportState { setCameraOptions { center(Point.fromLngLat(14.4378, 50.0755)); zoom(11.0); pitch(0.0) } }

    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val googleApiKey = remember { try { val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA); ai.metaData.getString("com.google.android.geo.API_KEY") ?: "" } catch (e: Exception) { "" } }

    LaunchedEffect(is3dModeExternal) {
        val tPitch = if (is3dModeExternal) 60.0 else 0.0
        val tZoom = if (is3dModeExternal) 16.0 else 13.0
        val tBear = if (is3dModeExternal) FollowPuckViewportStateBearing.SyncWithLocationPuck else FollowPuckViewportStateBearing.Constant(0.0)
        val tPad = if (is3dModeExternal) EdgeInsets(800.0, 0.0, 0.0, 0.0) else EdgeInsets(0.0, 0.0, 0.0, 0.0)

        mapViewportState.transitionToFollowPuckState(
            FollowPuckViewportStateOptions.Builder()
                .pitch(tPitch)
                .zoom(tZoom)
                .bearing(tBear)
                .padding(tPad)
                .build()
        )
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            delay(300)
            if (searchEngine == "GOOGLE") NetworkManager.fetchGoogleSuggestions(searchQuery, googleApiKey) { results -> suggestions = results }
            else NetworkManager.fetchSuggestions(searchQuery, context) { results -> suggestions = results }
        } else suggestions = emptyList()
    }

    fun performSearchAndRoute(destinationPoint: Point) {
        focusManager.clearFocus()
        suggestions = emptyList()
        searchQuery = ""

        onDestinationSet(destinationPoint)

        val tPitch = if (is3dModeExternal) 60.0 else 0.0
        val tZoom = if (is3dModeExternal) 16.0 else 13.0
        val tBear = if (is3dModeExternal) FollowPuckViewportStateBearing.SyncWithLocationPuck else FollowPuckViewportStateBearing.Constant(0.0)
        val tPad = if (is3dModeExternal) EdgeInsets(0.0, 0.0, 1000.0, 0.0) else EdgeInsets(0.0, 0.0, 0.0, 0.0)

        mapViewportState.transitionToFollowPuckState(FollowPuckViewportStateOptions.Builder().bearing(tBear).pitch(tPitch).zoom(tZoom).padding(tPad).build())

        val currentPoint = if (currentLocation != null) Point.fromLngLat(currentLocation.longitude, currentLocation.latitude) else (mapViewportState.cameraState?.center ?: Point.fromLngLat(14.4378, 50.0755))

        if (searchEngine == "GOOGLE") {
            NetworkManager.fetchGoogleRoute(currentPoint, destinationPoint, googleApiKey) { geo, instr, dur, limits ->
                scope.launch(Dispatchers.Main) { onRouteGeoJsonUpdated(geo); onInstructionUpdated(instr); onRouteDurationUpdated(dur); onSpeedLimitsUpdated(limits) }
            }
        } else {
            NetworkManager.fetchRouteManual(currentPoint, destinationPoint, context) { geo, instr, dur, limits ->
                scope.launch(Dispatchers.Main) { onRouteGeoJsonUpdated(geo); onInstructionUpdated(instr); onRouteDurationUpdated(dur); onSpeedLimitsUpdated(limits) }
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth().background(Color.DarkGray)) {
        TeslaMap(modifier = Modifier.fillMaxSize(), mapViewportState = mapViewportState, routeGeoJson = routeGeoJson, is3dMode = is3dModeExternal)
        val uiAlpha by animateFloatAsState(targetValue = if (isNightPanel) 0f else 1f, label = "UI Fade")

        if (uiAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().alpha(uiAlpha)) {

                if (routeGeoJson != null) {
                    IconButton(
                        onClick = onCancelRoute,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 24.dp, top = 16.dp)
                            .size(50.dp)
                            .background(Color(0xFF8B0000), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Route", tint = Color.White)
                    }
                }

                Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).fillMaxWidth(0.55f)) {
                    TeslaSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = { if (suggestions.isNotEmpty()) performSearchAndRoute(suggestions.first().point) }
                    )

                    if (suggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).background(Color.Black.copy(alpha = 0.95f), shape = RoundedCornerShape(16.dp))) {
                            items(suggestions) { place ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { performSearchAndRoute(place.point) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, tint = Color.Cyan, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(12.dp)); Text(place.name, color = Color.White, fontSize = 16.sp)
                                }
                                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                            }
                        }
                    }
                }

                FloatingActionButton(onClick = {
                    val tPitch = if (is3dModeExternal) 60.0 else 0.0
                    val tZoom = if (is3dModeExternal) 16.0 else 13.0
                    val tBear = if (is3dModeExternal) FollowPuckViewportStateBearing.SyncWithLocationPuck else FollowPuckViewportStateBearing.Constant(0.0)
                    val tPad = if (is3dModeExternal) EdgeInsets(0.0, 0.0, 1000.0, 0.0) else EdgeInsets(0.0, 0.0, 0.0, 0.0)
                    mapViewportState.transitionToFollowPuckState(FollowPuckViewportStateOptions.Builder().bearing(tBear).pitch(tPitch).zoom(tZoom).padding(tPad).build())
                }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), containerColor = Color.Black, contentColor = Color.White) { Icon(Icons.Default.MyLocation, "Locate Me") }
            }
        }
    }
}

@Composable
fun GoogleViewport(
    modifier: Modifier = Modifier,
    isNightPanel: Boolean = false,
    is3dModeExternal: Boolean = false,
    searchEngine: String,
    currentLocation: Location?,
    routeGeoJson: String?,
    onRouteGeoJsonUpdated: (String?) -> Unit,
    onInstructionUpdated: (List<NavInstruction>) -> Unit,
    onRouteDurationUpdated: (Int?) -> Unit,
    onSpeedLimitsUpdated: (List<Int?>) -> Unit,
    onDestinationSet: (Point?) -> Unit,
    onCancelRoute: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val googleApiKey = remember { try { val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA); ai.metaData.getString("com.google.android.geo.API_KEY") ?: "" } catch (e: Exception) { "" } }
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(50.0755, 14.4378), 13f) }

    LaunchedEffect(currentLocation, is3dModeExternal) {
        if (is3dModeExternal && currentLocation != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(currentLocation.latitude, currentLocation.longitude))
                        .zoom(16f)
                        .tilt(60f)
                        .bearing(currentLocation.bearing)
                        .build()
                ), 1500
            )
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            delay(300)
            if (searchEngine == "GOOGLE") NetworkManager.fetchGoogleSuggestions(searchQuery, googleApiKey) { results -> suggestions = results }
            else NetworkManager.fetchSuggestions(searchQuery, context) { results -> suggestions = results }
        } else suggestions = emptyList()
    }

    fun performSearchAndRoute(destinationPoint: Point) {
        focusManager.clearFocus()
        suggestions = emptyList()
        searchQuery = ""

        onDestinationSet(destinationPoint)

        scope.launch {
            val tTilt = if (is3dModeExternal) 60f else 0f
            val tZoom = if (is3dModeExternal) 16f else 13f
            val tBear = if (is3dModeExternal && currentLocation != null) currentLocation.bearing else 0f
            val targetLatLng = if (currentLocation != null) LatLng(currentLocation.latitude, currentLocation.longitude) else cameraPositionState.position.target

            cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(targetLatLng).zoom(tZoom).tilt(tTilt).bearing(tBear).build()
            ), 1000)
        }

        val currentPoint = if (currentLocation != null) Point.fromLngLat(currentLocation.longitude, currentLocation.latitude) else Point.fromLngLat(cameraPositionState.position.target.longitude, cameraPositionState.position.target.latitude)

        if (searchEngine == "GOOGLE") {
            NetworkManager.fetchGoogleRoute(currentPoint, destinationPoint, googleApiKey) { geo, instr, dur, limits ->
                scope.launch(Dispatchers.Main) { onRouteGeoJsonUpdated(geo); onInstructionUpdated(instr); onRouteDurationUpdated(dur); onSpeedLimitsUpdated(limits) }
            }
        } else {
            NetworkManager.fetchRouteManual(currentPoint, destinationPoint, context) { geo, instr, dur, limits ->
                scope.launch(Dispatchers.Main) { onRouteGeoJsonUpdated(geo); onInstructionUpdated(instr); onRouteDurationUpdated(dur); onSpeedLimitsUpdated(limits) }
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth().background(Color.DarkGray)) {
        val bottomPadding = if (is3dModeExternal) 250.dp else 0.dp
        GoogleMapDisplay(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraPositionState, routeGeoJson = routeGeoJson, is3dMode = is3dModeExternal, bottomPadding = bottomPadding)

        val uiAlpha by animateFloatAsState(targetValue = if (isNightPanel) 0f else 1f, label = "UI Fade")

        if (uiAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().alpha(uiAlpha)) {

                if (routeGeoJson != null) {
                    IconButton(
                        onClick = onCancelRoute,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 24.dp, top = 16.dp)
                            .size(50.dp)
                            .background(Color(0xFF8B0000), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Route", tint = Color.White)
                    }
                }

                Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).fillMaxWidth(0.55f)) {
                    TeslaSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = { if (suggestions.isNotEmpty()) performSearchAndRoute(suggestions.first().point) }
                    )

                    if (suggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).background(Color.Black.copy(alpha = 0.95f), shape = RoundedCornerShape(16.dp))) {
                            items(suggestions) { place ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { performSearchAndRoute(place.point) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, tint = Color.Cyan, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(12.dp)); Text(place.name, color = Color.White, fontSize = 16.sp)
                                }
                                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                            }
                        }
                    }
                }

                FloatingActionButton(onClick = {
                    scope.launch {
                        val tTilt = if (is3dModeExternal) 60f else 0f
                        val tZoom = if (is3dModeExternal) 16f else 13f
                        val tBear = if (is3dModeExternal && currentLocation != null) currentLocation.bearing else 0f
                        if (currentLocation != null) cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(currentLocation.latitude, currentLocation.longitude)).zoom(tZoom).tilt(tTilt).bearing(tBear).build()), 1000)
                        else cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(cameraPositionState.position.target).zoom(tZoom).tilt(tTilt).bearing(tBear).build()), 1000)
                    }
                }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), containerColor = Color.Black, contentColor = Color.White) { Icon(Icons.Default.MyLocation, "Locate Me") }
            }
        }
    }
}

@Composable
fun TeslaMap(modifier: Modifier = Modifier, mapViewportState: com.mapbox.maps.extension.compose.animation.viewport.MapViewportState, routeGeoJson: String? = null, is3dMode: Boolean = false) {
    val context = LocalContext.current
    var locationPermissionGranted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) locationPermissionGranted = true }

    MapboxMap(modifier = Modifier.fillMaxSize(), mapViewportState = mapViewportState) {
        MapEffect(locationPermissionGranted) { mapView ->
            mapView.mapboxMap.loadStyleUri(Style.TRAFFIC_NIGHT) { style ->
                val sourceId = "route-source"; val layerId = "route-layer"
                if (!style.styleSourceExists(sourceId)) style.addSource(geoJsonSource(sourceId) { data("") })
                if (!style.styleLayerExists(layerId)) {
                    style.addLayer(lineLayer(layerId, sourceId) {

                        // 🌟 TADY JE TA MAGIE PRO BARVENÍ DLE DOPRAVY!
                        lineColor(
                            match(
                                get("congestion"),
                                literal("unknown"), literal("#00B0FF"), // Výchozí modrá
                                literal("low"), literal("#00B0FF"),     // Nízký provoz = modrá
                                literal("moderate"), literal("#FF9900"),// Střední provoz = oranžová
                                literal("heavy"), literal("#FF0000"),   // Těžký provoz = červená
                                literal("severe"), literal("#8B0000"),  // Zácpa = tmavě červená
                                literal("#00B0FF")                      // Fallback
                            )
                        )
                        lineWidth(8.0)
                        lineOpacity(0.85) // Mírně vyšší neprůhlednost, ať barvy lépe vyniknou
                        lineCap(LineCap.ROUND)
                        lineJoin(LineJoin.ROUND)
                    })
                }
                if (!style.styleLayerExists("3d-buildings")) { val bLayer = FillExtrusionLayer("3d-buildings", "composite"); bLayer.sourceLayer("building"); bLayer.filter(eq(get("extrude"), literal("true"))); bLayer.minZoom(15.0); bLayer.fillExtrusionColor(Color.DarkGray.toArgb()); bLayer.fillExtrusionOpacity(0.8); bLayer.fillExtrusionHeight(get("height")); bLayer.fillExtrusionBase(get("min_height")); style.addLayer(bLayer) }
            }
            if (locationPermissionGranted) {
                mapView.location.updateSettings {
                    enabled = true
                    pulsingEnabled = true
                    puckBearingEnabled = true
                    puckBearing = PuckBearing.COURSE
                }
            }
        }
        MapEffect(routeGeoJson) { mapView -> if (routeGeoJson != null) mapView.mapboxMap.getStyle { style -> if (style.styleSourceExists("route-source")) (style.getSource("route-source") as? GeoJsonSource)?.data(routeGeoJson) } else mapView.mapboxMap.getStyle { style -> if (style.styleSourceExists("route-source")) (style.getSource("route-source") as? GeoJsonSource)?.data("") } }
    }
}

@Composable
fun GoogleMapDisplay(modifier: Modifier = Modifier, cameraPositionState: com.google.maps.android.compose.CameraPositionState, routeGeoJson: String? = null, is3dMode: Boolean = false, bottomPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val uiSettings = remember { MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false) }
    val mapProperties = remember(is3dMode) { MapProperties(isMyLocationEnabled = true, isTrafficEnabled = true, maxZoomPreference = 20f, minZoomPreference = 5f) }

    // 🌟 ROBUSTNĚJŠÍ PARSER PRO GOOGLE (Protože Mapbox mu teď posílá rozsekanou trasu)
    val routePoints = remember(routeGeoJson) {
        val points = mutableListOf<LatLng>()
        if (routeGeoJson != null) {
            try {
                val featureCollection = com.mapbox.geojson.FeatureCollection.fromJson(routeGeoJson)
                featureCollection.features()?.forEach { feature ->
                    val lineString = feature.geometry() as? com.mapbox.geojson.LineString
                    lineString?.coordinates()?.forEach { pt ->
                        points.add(LatLng(pt.latitude(), pt.longitude()))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        points
    }

    GoogleMap(modifier = modifier, cameraPositionState = cameraPositionState, properties = mapProperties, uiSettings = uiSettings, contentPadding = PaddingValues(bottom = bottomPadding)) {
        if (routePoints.isNotEmpty()) Polyline(points = routePoints, color = Color(0xAA00B0FF), width = 24f, geodesic = true)
    }
}