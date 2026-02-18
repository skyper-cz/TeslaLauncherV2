package com.launchers.teslalauncherv2.data

import com.mapbox.geojson.Point

// Represents a location result from Mapbox/Google Geocoding APIs
data class SearchSuggestion(
    val name: String,
    val point: Point
)

// Represents a single step/maneuver in a driving route
data class NavInstruction(
    val text: String,           // Human-readable instruction (e.g., "Turn left onto Main St")
    var distance: Int,          // Distance to maneuver in meters (var to allow live countdown)
    val modifier: String?,      // Turn direction modifier (e.g., "left", "right", "slight right")
    val maneuverPoint: Point?   // The exact GPS coordinates of the intersection/turn
)