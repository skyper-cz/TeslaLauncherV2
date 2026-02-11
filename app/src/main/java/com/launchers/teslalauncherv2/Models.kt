package com.launchers.teslalauncherv2

import com.mapbox.geojson.Point

data class SearchSuggestion(
    val name: String,
    val point: Point
)

data class NavInstruction(
    val text: String,
    var distance: Int, // Změněno na 'var', abychom to mohli aktualizovat
    val modifier: String?,
    val maneuverPoint: Point? // NOVÉ: Souřadnice, kde máme zatočit
)