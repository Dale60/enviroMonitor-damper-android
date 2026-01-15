package com.example.damperlocator.floorplan

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object PolygonCalculator {

    /**
     * Calculate perimeter of polygon defined by ordered 2D points
     * Points should be in sequential order (clockwise or counter-clockwise)
     * Works for both open paths and closed polygons
     */
    fun calculatePerimeter(points: List<Vector2>, isClosed: Boolean = true): Float {
        if (points.size < 2) return 0f

        var perimeter = 0f
        val limit = if (isClosed) points.size else points.size - 1

        for (i in 0 until limit) {
            val current = points[i]
            val next = points[(i + 1) % points.size]
            perimeter += distance(current, next)
        }
        return perimeter
    }

    /**
     * Calculate area using Shoelace formula (Surveyor's formula)
     * Works for any simple polygon (non-self-intersecting)
     */
    fun calculateArea(points: List<Vector2>): Float {
        if (points.size < 3) return 0f

        var sum = 0f
        for (i in points.indices) {
            val current = points[i]
            val next = points[(i + 1) % points.size]
            sum += (current.x * next.y) - (next.x * current.y)
        }
        return abs(sum) / 2f
    }

    /**
     * Distance between two 2D points in meters
     */
    fun distance(a: Vector2, b: Vector2): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Check if polygon is valid (at least 3 non-collinear points)
     */
    fun isValidPolygon(points: List<Vector2>): Boolean {
        if (points.size < 3) return false
        return calculateArea(points) > 0.001f  // Min 0.001 m^2
    }

    /**
     * Get centroid of polygon for labeling
     */
    fun getCentroid(points: List<Vector2>): Vector2 {
        if (points.isEmpty()) return Vector2(0f, 0f)

        val avgX = points.map { it.x }.average().toFloat()
        val avgY = points.map { it.y }.average().toFloat()
        return Vector2(avgX, avgY)
    }

    /**
     * Rotate points to align with true north
     * northOffsetDegrees: angle from device Y-axis to true north
     */
    fun rotateToNorth(points: List<Vector2>, northOffsetDegrees: Float): List<Vector2> {
        val radians = Math.toRadians(northOffsetDegrees.toDouble())
        val cosVal = cos(radians).toFloat()
        val sinVal = sin(radians).toFloat()

        return points.map { point ->
            Vector2(
                x = point.x * cosVal - point.y * sinVal,
                y = point.x * sinVal + point.y * cosVal
            )
        }
    }

    /**
     * Get bounding box of points for UI scaling
     */
    fun getBoundingBox(points: List<Vector2>): BoundingBox {
        if (points.isEmpty()) return BoundingBox(0f, 0f, 0f, 0f)

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }

        return BoundingBox(minX, minY, maxX, maxY)
    }
}

data class BoundingBox(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
}
