package com.launchers.teslalauncherv2.data

import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon

// 1. Datové třídy pro hierarchii
data class MapRegion(val id: String, val name: String, val sizeMb: String, val geometry: Polygon)
data class MapCountry(val name: String, val regions: List<MapRegion>)
data class MapContinent(val name: String, val countries: List<MapCountry>)

// 2. Pomocná funkce pro vytvoření Bounding Boxu (Ohraničujícího obdélníku)
fun createBoundingBox(west: Double, south: Double, east: Double, north: Double): Polygon {
    val points = listOf(
        Point.fromLngLat(west, south), // Jihozápad
        Point.fromLngLat(east, south), // Jihovýchod
        Point.fromLngLat(east, north), // Severovýchod
        Point.fromLngLat(west, north), // Severozápad
        Point.fromLngLat(west, south)  // Uzavření smyčky
    )
    return Polygon.fromLngLats(listOf(points))
}

// 3. Naše databáze kontinentů, zemí a regionů
object OfflineRegionsDatabase {
    val continents = listOf(
        MapContinent("Europe", listOf(
            MapCountry("Czech Republic", listOf(
                MapRegion("cz_prague_central", "Prague & Central Bohemia", "~ 120 MB", createBoundingBox(13.5, 49.5, 15.5, 50.5)),
                MapRegion("cz_moravia", "Moravia & Silesia", "~ 150 MB", createBoundingBox(15.5, 48.5, 18.9, 50.3)),
                MapRegion("cz_west", "West & South Bohemia", "~ 130 MB", createBoundingBox(12.0, 48.5, 14.5, 50.5))
            )),
            MapCountry("Slovakia", listOf(
                MapRegion("sk_all", "Entire Slovakia", "~ 210 MB", createBoundingBox(16.8, 47.7, 22.6, 49.6))
            )),
            MapCountry("Germany", listOf(
                MapRegion("de_bavaria", "Bavaria (Bayern)", "~ 350 MB", createBoundingBox(8.9, 47.2, 13.9, 50.5)),
                MapRegion("de_saxony", "Saxony (Sachsen)", "~ 180 MB", createBoundingBox(11.8, 50.1, 15.0, 51.7))
            ))
        )),
        MapContinent("North America", listOf(
            MapCountry("USA", listOf(
                MapRegion("us_california", "California", "~ 850 MB", createBoundingBox(-124.4, 32.5, -114.1, 42.0)),
                MapRegion("us_texas", "Texas", "~ 900 MB", createBoundingBox(-106.6, 25.8, -93.5, 36.5))
            ))
        ))
    )
}

// Přidej na konec souboru OfflineRegions.kt

fun createBoundingBoxAround(lat: Double, lon: Double, radiusKm: Double = 50.0): Polygon {
    // 1 stupeň zeměpisné šířky je cca 111 km
    val latDelta = radiusKm / 111.0
    val lonDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)))

    val west = lon - lonDelta
    val south = lat - latDelta
    val east = lon + lonDelta
    val north = lat + latDelta

    val points = listOf(
        Point.fromLngLat(west, south),
        Point.fromLngLat(east, south),
        Point.fromLngLat(east, north),
        Point.fromLngLat(west, north),
        Point.fromLngLat(west, south)
    )
    return Polygon.fromLngLats(listOf(points))
}