package com.launchers.teslalauncherv2.data

import com.mapbox.geojson.Geometry
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon

// Převede souřadnice rovnou na správný geometrický tvar (Polygon) pro Mapbox stahovač
fun createBBoxGeometry(minLng: Double, minLat: Double, maxLng: Double, maxLat: Double): Geometry {
    val points = listOf(
        Point.fromLngLat(minLng, minLat),
        Point.fromLngLat(maxLng, minLat),
        Point.fromLngLat(maxLng, maxLat),
        Point.fromLngLat(minLng, maxLat),
        Point.fromLngLat(minLng, minLat) // Uzavření obdélníku
    )
    return Polygon.fromLngLats(listOf(points)) as Geometry
}

// Funkce pro "Smart Region" okruh (okolí auta)
fun createBoundingBoxAround(lat: Double, lng: Double, radiusKm: Double): Geometry {
    val latOffset = radiusKm / 111.0
    val lngOffset = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)))

    val minLat = lat - latOffset
    val maxLat = lat + latOffset
    val minLng = lng - lngOffset
    val maxLng = lng + lngOffset

    return createBBoxGeometry(minLng, minLat, maxLng, maxLat)
}

data class MapRegion(
    val id: String,
    val name: String,
    val sizeMb: String,
    val geometry: Geometry,
    // 🌟 PŘIDANÝ PARAMETR PRO OCHRANU PŘED TILE LIMITEM
    val maxZoom: Double = 12.0
)

data class MapCountry(
    val name: String,
    val regions: List<MapRegion>
)

data class MapContinent(
    val name: String,
    val countries: List<MapCountry>
)

object OfflineRegionsDatabase {
    val continents = listOf(
        MapContinent(
            name = "Europe",
            countries = listOf(
                MapCountry("Česká republika", listOf(
                    // Velké oblasti - nižší zoom (hlavní tahy)
                    MapRegion("cz_west", "Čechy (Západ a Střed)", "~150 MB", createBBoxGeometry(12.09, 48.55, 15.50, 51.05), maxZoom = 11.5),
                    MapRegion("cz_east", "Morava a Slezsko", "~120 MB", createBBoxGeometry(15.50, 48.55, 18.86, 50.30), maxZoom = 11.5),
                    // Města - vysoký zoom (detailní ulice)
                    MapRegion("cz_prague", "Praha a okolí", "~50 MB", createBBoxGeometry(14.22, 49.94, 14.73, 50.17), maxZoom = 14.5),
                    MapRegion("cz_brno", "Brno a okolí", "~40 MB", createBBoxGeometry(16.50, 49.10, 16.75, 49.30), maxZoom = 14.5),
                    MapRegion("cz_ostrava", "Ostrava a okolí", "~40 MB", createBBoxGeometry(18.10, 49.70, 18.40, 49.90), maxZoom = 14.5)
                )),
                MapCountry("Slovensko", listOf(
                    MapRegion("sk_west", "Západní Slovensko", "~100 MB", createBBoxGeometry(16.83, 47.73, 19.50, 49.61), maxZoom = 11.5),
                    MapRegion("sk_east", "Východní a Střední SR", "~120 MB", createBBoxGeometry(19.50, 48.00, 22.57, 49.40), maxZoom = 11.5),
                    MapRegion("sk_bratislava", "Bratislava a okolí", "~40 MB", createBBoxGeometry(16.95, 48.10, 17.25, 48.25), maxZoom = 14.5)
                )),
                MapCountry("Německo", listOf(
                    MapRegion("de_north", "Severní Německo", "~250 MB", createBBoxGeometry(5.86, 51.0, 14.0, 55.0), maxZoom = 11.0),
                    MapRegion("de_south", "Jižní Německo (Bavorsko)", "~280 MB", createBBoxGeometry(7.5, 47.2, 13.8, 51.0), maxZoom = 11.0),
                    MapRegion("de_berlin", "Berlín a okolí", "~80 MB", createBBoxGeometry(12.92, 52.33, 13.76, 52.68), maxZoom = 14.0),
                    MapRegion("de_munich", "Mnichov a okolí", "~70 MB", createBBoxGeometry(11.35, 48.05, 11.75, 48.25), maxZoom = 14.0)
                )),
                MapCountry("Rakousko", listOf(
                    MapRegion("at_east", "Východní Rakousko", "~150 MB", createBBoxGeometry(13.0, 46.37, 17.16, 49.02), maxZoom = 11.5),
                    MapRegion("at_west", "Alpy a Tyrolsko", "~140 MB", createBBoxGeometry(9.53, 46.8, 13.0, 47.8), maxZoom = 11.5),
                    MapRegion("at_vienna", "Vídeň a okolí", "~60 MB", createBBoxGeometry(16.18, 48.11, 16.57, 48.32), maxZoom = 14.5)
                )),
                MapCountry("Polsko", listOf(
                    MapRegion("pl_south", "Jižní Polsko (Slezsko, Krakov)", "~200 MB", createBBoxGeometry(14.12, 49.0, 24.15, 51.5), maxZoom = 11.0),
                    MapRegion("pl_north", "Severní Polsko", "~220 MB", createBBoxGeometry(14.12, 51.5, 24.15, 54.83), maxZoom = 11.0),
                    MapRegion("pl_warsaw", "Varšava a okolí", "~70 MB", createBBoxGeometry(20.85, 52.10, 21.25, 52.35), maxZoom = 14.0)
                )),
                MapCountry("Itálie", listOf(
                    MapRegion("it_north", "Severní Itálie a Alpy", "~250 MB", createBBoxGeometry(6.62, 43.75, 13.85, 47.09), maxZoom = 11.0),
                    MapRegion("it_south", "Jižní Itálie", "~200 MB", createBBoxGeometry(11.0, 36.6, 18.5, 43.8), maxZoom = 11.0),
                    MapRegion("it_rome", "Řím a okolí", "~60 MB", createBBoxGeometry(12.35, 41.75, 12.65, 42.05), maxZoom = 14.0),
                    MapRegion("it_milan", "Milán a okolí", "~65 MB", createBBoxGeometry(8.9, 45.3, 9.4, 45.6), maxZoom = 14.0)
                )),
                MapCountry("Chorvatsko", listOf(
                    MapRegion("hr_north", "Istrie a Kvarner", "~100 MB", createBBoxGeometry(13.48, 44.5, 15.5, 46.55), maxZoom = 12.0),
                    MapRegion("hr_south", "Dalmácie (Zadar - Dubrovník)", "~120 MB", createBBoxGeometry(15.0, 42.39, 18.5, 44.5), maxZoom = 12.0),
                    MapRegion("hr_zagreb", "Záhřeb a okolí", "~40 MB", createBBoxGeometry(15.8, 45.7, 16.2, 45.9), maxZoom = 14.0)
                )),
                MapCountry("Francie", listOf(
                    MapRegion("fr_north", "Severní Francie", "~300 MB", createBBoxGeometry(-5.0, 47.0, 8.2, 51.1), maxZoom = 10.5),
                    MapRegion("fr_south", "Jižní Francie", "~280 MB", createBBoxGeometry(-1.5, 42.3, 7.5, 47.0), maxZoom = 10.5),
                    MapRegion("fr_paris", "Paříž a okolí", "~90 MB", createBBoxGeometry(2.1, 48.7, 2.6, 49.0), maxZoom = 14.0)
                )),
                MapCountry("Velká Británie", listOf(
                    MapRegion("uk_england", "Anglie", "~280 MB", createBBoxGeometry(-6.0, 50.0, 1.8, 55.0), maxZoom = 11.0),
                    MapRegion("uk_scotland", "Skotsko", "~150 MB", createBBoxGeometry(-8.0, 55.0, -1.5, 59.0), maxZoom = 11.0),
                    MapRegion("uk_london", "Londýn", "~100 MB", createBBoxGeometry(-0.5, 51.3, 0.3, 51.7), maxZoom = 14.0)
                )),
                MapCountry("Španělsko", listOf(
                    MapRegion("es_north", "Severní Španělsko", "~220 MB", createBBoxGeometry(-9.3, 40.0, 3.3, 43.8), maxZoom = 11.0),
                    MapRegion("es_south", "Jižní Španělsko", "~200 MB", createBBoxGeometry(-7.5, 36.0, -0.5, 40.0), maxZoom = 11.0),
                    MapRegion("es_madrid", "Madrid", "~60 MB", createBBoxGeometry(-4.0, 40.3, -3.4, 40.6), maxZoom = 14.0)
                ))
            )
        ),
        MapContinent(
            name = "North America",
            countries = listOf(
                MapCountry("United States", listOf(
                    MapRegion("us_ny", "New York & New Jersey", "~150 MB", createBBoxGeometry(-75.3, 38.9, -71.8, 41.5), maxZoom = 11.5),
                    MapRegion("us_ca_north", "California (North)", "~180 MB", createBBoxGeometry(-124.0, 36.0, -119.0, 42.0), maxZoom = 11.0),
                    MapRegion("us_ca_south", "California (South)", "~180 MB", createBBoxGeometry(-120.0, 32.5, -114.0, 36.0), maxZoom = 11.0),
                    MapRegion("us_fl", "Florida", "~150 MB", createBBoxGeometry(-87.6, 24.5, -80.0, 31.0), maxZoom = 11.0),
                    MapRegion("us_tx", "Texas (East)", "~200 MB", createBBoxGeometry(-100.0, 25.8, -93.5, 34.0), maxZoom = 11.0),
                    MapRegion("us_nyc_city", "NYC Metro Area", "~90 MB", createBBoxGeometry(-74.1, 40.5, -73.7, 40.9), maxZoom = 14.0),
                    MapRegion("us_la_city", "Los Angeles Metro", "~90 MB", createBBoxGeometry(-118.5, 33.7, -117.8, 34.3), maxZoom = 14.0)
                )),
                MapCountry("Canada", listOf(
                    MapRegion("ca_toronto", "Toronto Metro", "~80 MB", createBBoxGeometry(-79.6, 43.5, -79.1, 43.9), maxZoom = 13.5),
                    MapRegion("ca_vancouver", "Vancouver & BC South", "~120 MB", createBBoxGeometry(-123.5, 49.0, -121.0, 50.0), maxZoom = 12.0)
                ))
            )
        )
    )
}