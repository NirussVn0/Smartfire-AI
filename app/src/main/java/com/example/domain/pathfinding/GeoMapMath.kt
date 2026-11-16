package com.example.domain.pathfinding

import com.example.domain.model.Point2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoLatLng(
    val lat: Double,
    val lng: Double
)

data class GeoPolygonMetrics(
    val widthMeters: Float,
    val lengthMeters: Float,
    val areaM2: Float,
    val edgeLengthsMeters: List<Float>
)

object GeoMapMath {

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val METERS_PER_DEG_LAT = 111_320.0

    fun haversineDistanceMeters(a: GeoLatLng, b: GeoLatLng): Float {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)

        val sinDLat = sin(dLat / 2.0)
        val sinDLng = sin(dLng / 2.0)
        val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLng * sinDLng
        val c = 2.0 * atan2(sqrt(h), sqrt((1.0 - h).coerceAtLeast(0.0)))
        return (EARTH_RADIUS_METERS * c).toFloat()
    }

    /**
     * Converts a list of GPS roof corners into real metric dimensions (East-West width,
     * North-South length, per-wall Haversine edge lengths, and Shoelace area in m²).
     */
    fun calculateGeoPolygonMetrics(points: List<GeoLatLng>): GeoPolygonMetrics {
        if (points.size < 2) {
            return GeoPolygonMetrics(
                widthMeters = 12f,
                lengthMeters = 16f,
                areaM2 = 0f,
                edgeLengthsMeters = emptyList()
            )
        }

        val centerLat = points.map { it.lat }.average()
        val centerLng = points.map { it.lng }.average()
        val metersPerDegLng = METERS_PER_DEG_LAT * cos(Math.toRadians(centerLat)).coerceAtLeast(0.2)

        val localMeters = points.map { pt ->
            val eastM = ((pt.lng - centerLng) * metersPerDegLng).toFloat()
            val northM = ((pt.lat - centerLat) * METERS_PER_DEG_LAT).toFloat()
            Point2D(eastM, northM)
        }

        val minX = localMeters.minOf { it.x }
        val maxX = localMeters.maxOf { it.x }
        val minY = localMeters.minOf { it.y }
        val maxY = localMeters.maxOf { it.y }

        val widthM = (maxX - minX).coerceIn(3.0f, 300.0f)
        val lengthM = (maxY - minY).coerceIn(3.0f, 300.0f)

        val edgeCount = if (points.size >= 3) points.size else points.size - 1
        val edgeLengths = (0 until edgeCount).map { i ->
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            haversineDistanceMeters(p1, p2)
        }

        var shoelaceArea = 0f
        if (localMeters.size >= 3) {
            for (i in localMeters.indices) {
                val p1 = localMeters[i]
                val p2 = localMeters[(i + 1) % localMeters.size]
                shoelaceArea += (p1.x * p2.y - p2.x * p1.y)
            }
            shoelaceArea = abs(shoelaceArea) * 0.5f
        }

        return GeoPolygonMetrics(
            widthMeters = (widthM * 10f).roundToInt() / 10f,
            lengthMeters = (lengthM * 10f).roundToInt() / 10f,
            areaM2 = (shoelaceArea * 10f).roundToInt() / 10f,
            edgeLengthsMeters = edgeLengths
        )
    }

    /**
     * Projects real GPS roof corners onto a normalized [0..1] 2D floorplan coordinate frame
     * aligned strictly North-Up (North = -Y, East = +X), preserving true geometric aspect ratio.
     */
    fun projectGeoPolygonToNormalized(points: List<GeoLatLng>): List<Point2D> {
        if (points.size < 3) {
            return listOf(
                Point2D(0.12f, 0.12f),
                Point2D(0.88f, 0.12f),
                Point2D(0.88f, 0.88f),
                Point2D(0.12f, 0.88f)
            )
        }

        val centerLat = points.map { it.lat }.average()
        val centerLng = points.map { it.lng }.average()
        val metersPerDegLng = METERS_PER_DEG_LAT * cos(Math.toRadians(centerLat)).coerceAtLeast(0.2)

        // Screen Y points South (+Y = down), so North is negative Y
        val localPts = points.map { pt ->
            val xM = ((pt.lng - centerLng) * metersPerDegLng).toFloat()
            val yM = (-(pt.lat - centerLat) * METERS_PER_DEG_LAT).toFloat()
            Point2D(xM, yM)
        }

        val minX = localPts.minOf { it.x }
        val maxX = localPts.maxOf { it.x }
        val minY = localPts.minOf { it.y }
        val maxY = localPts.maxOf { it.y }
        val spanX = (maxX - minX).coerceAtLeast(1.0f)
        val spanY = (maxY - minY).coerceAtLeast(1.0f)
        val maxSpan = max(spanX, spanY)
        val scale = 0.78f / maxSpan
        val midX = (minX + maxX) / 2f
        val midY = (minY + maxY) / 2f

        return localPts.map { pt ->
            Point2D(
                x = (0.50f + (pt.x - midX) * scale).coerceIn(0.06f, 0.94f),
                y = (0.50f + (pt.y - midY) * scale).coerceIn(0.06f, 0.94f)
            )
        }
    }

    /**
     * Converts saved normalized floorplan corners back into GeoLatLng centered around [center]
     * using real metric dimensions so existing maps immediately render accurately on Google Maps.
     */
    fun normalizedPointsToGeoPolygon(
        normPoints: List<Point2D>,
        center: GeoLatLng,
        widthMeters: Float,
        lengthMeters: Float
    ): List<GeoLatLng> {
        if (normPoints.size < 3) return emptyList()
        val wM = widthMeters.coerceIn(5f, 150f)
        val lM = lengthMeters.coerceIn(5f, 150f)
        val metersPerDegLng = METERS_PER_DEG_LAT * cos(Math.toRadians(center.lat)).coerceAtLeast(0.2)

        val avgX = normPoints.map { it.x }.average().toFloat()
        val avgY = normPoints.map { it.y }.average().toFloat()
        val spanX = (normPoints.maxOf { it.x } - normPoints.minOf { it.x }).coerceAtLeast(0.2f)
        val spanY = (normPoints.maxOf { it.y } - normPoints.minOf { it.y }).coerceAtLeast(0.2f)

        return normPoints.map { pt ->
            val eastMeters = ((pt.x - avgX) / spanX) * wM
            val northMeters = (-(pt.y - avgY) / spanY) * lM
            GeoLatLng(
                lat = center.lat + (northMeters / METERS_PER_DEG_LAT),
                lng = center.lng + (eastMeters / metersPerDegLng)
            )
        }
    }

    fun createGeoTemplateRect(
        center: GeoLatLng,
        widthMeters: Double = 10.0,
        lengthMeters: Double = 16.0
    ): List<GeoLatLng> {
        val metersPerDegLng = METERS_PER_DEG_LAT * cos(Math.toRadians(center.lat)).coerceAtLeast(0.2)
        val halfW = (widthMeters / 2.0) / metersPerDegLng
        val halfL = (lengthMeters / 2.0) / METERS_PER_DEG_LAT
        return listOf(
            GeoLatLng(center.lat + halfL, center.lng - halfW), // NW
            GeoLatLng(center.lat + halfL, center.lng + halfW), // NE
            GeoLatLng(center.lat - halfL, center.lng + halfW), // SE
            GeoLatLng(center.lat - halfL, center.lng - halfW)  // SW
        )
    }

    fun createGeoTemplateLShape(
        center: GeoLatLng,
        widthMeters: Double = 14.0,
        lengthMeters: Double = 16.0
    ): List<GeoLatLng> {
        val metersPerDegLng = METERS_PER_DEG_LAT * cos(Math.toRadians(center.lat)).coerceAtLeast(0.2)
        val halfW = (widthMeters / 2.0) / metersPerDegLng
        val halfL = (lengthMeters / 2.0) / METERS_PER_DEG_LAT
        val wingW = (widthMeters * 0.14) / metersPerDegLng
        return listOf(
            GeoLatLng(center.lat + halfL, center.lng - halfW),
            GeoLatLng(center.lat + halfL, center.lng + wingW),
            GeoLatLng(center.lat, center.lng + wingW),
            GeoLatLng(center.lat, center.lng + halfW),
            GeoLatLng(center.lat - halfL, center.lng + halfW),
            GeoLatLng(center.lat - halfL, center.lng - halfW)
        )
    }

    /**
     * Parses raw "lat, lng" coordinate input if user searches coordinates directly.
     */
    fun parseCoordinatesOrNull(query: String): GeoLatLng? {
        val parts = query.trim().split(",", " ", ";").mapNotNull { it.trim().toDoubleOrNull() }
        if (parts.size == 2) {
            val lat = parts[0]
            val lng = parts[1]
            if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                return GeoLatLng(lat, lng)
            }
        }
        return null
    }
}
