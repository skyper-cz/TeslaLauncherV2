package com.launchers.teslalauncherv2

import com.mapbox.geojson.Point

data class SearchSuggestion(
    val name: String,
    val point: Point
)

data class NavInstruction(
    val text: String,
    val distance: Int,
    val modifier: String?
)