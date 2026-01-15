package com.example.damperlocator.ar

import com.example.damperlocator.floorplan.Vector3
import kotlin.math.tan

object CoordinateTransformer {

    /**
     * Project a ray from camera through screen point to a horizontal plane at floorY
     * Uses trigonometric projection when ARCore plane detection is unavailable
     *
     * @param cameraPose Camera position and orientation
     * @param screenX Normalized screen X coordinate (0-1, center = 0.5)
     * @param screenY Normalized screen Y coordinate (0-1, center = 0.5)
     * @param floorY Y coordinate of the floor plane
     * @param fovRadians Vertical field of view in radians (default ~60 degrees)
     * @param aspectRatio Screen width / height
     */
    fun projectRayToFloor(
        cameraPose: CameraPose,
        screenX: Float,
        screenY: Float,
        floorY: Float,
        fovRadians: Float = 1.047f,  // ~60 degrees
        aspectRatio: Float = 0.5625f  // 9:16 portrait
    ): Vector3? {
        // Camera height above floor
        val heightAboveFloor = cameraPose.y - floorY
        if (heightAboveFloor <= 0.01f) {
            // Camera at or below floor level - can't project
            return null
        }

        // Convert screen coordinates to ray direction
        // Center of screen is forward, edges are offset by FOV
        val halfFovV = fovRadians / 2f
        val halfFovH = halfFovV * aspectRatio

        // Screen coordinates: 0,0 = top-left, 1,1 = bottom-right
        // Convert to centered coordinates: -0.5 to 0.5
        val centeredX = screenX - 0.5f
        val centeredY = screenY - 0.5f

        // Calculate ray angles relative to camera forward
        val angleH = centeredX * halfFovH * 2f  // Horizontal angle
        val angleV = centeredY * halfFovV * 2f  // Vertical angle (positive = down)

        // Camera forward direction from quaternion
        val forward = quaternionToForward(cameraPose)

        // If looking up (negative vertical angle from camera perspective pointing down)
        // the ray won't hit the floor in front
        val effectiveDownAngle = -forward.y + angleV

        if (effectiveDownAngle <= 0.01f) {
            // Looking up or horizontal - use fixed distance projection
            return projectAtFixedDistance(cameraPose, angleH, floorY, DEFAULT_PROJECTION_DISTANCE)
        }

        // Calculate intersection distance using trigonometry
        // tan(angle) = opposite / adjacent
        // distance = height / tan(downAngle)
        val horizontalDistance = heightAboveFloor / tan(effectiveDownAngle.toDouble()).toFloat()

        // Limit to reasonable distance
        val clampedDistance = horizontalDistance.coerceIn(0.1f, MAX_PROJECTION_DISTANCE)

        // Calculate world position
        val forwardXZ = normalizeXZ(forward.x, forward.z)

        // Apply horizontal angle rotation
        val cosH = kotlin.math.cos(angleH.toDouble()).toFloat()
        val sinH = kotlin.math.sin(angleH.toDouble()).toFloat()

        val dirX = forwardXZ.first * cosH - forwardXZ.second * sinH
        val dirZ = forwardXZ.first * sinH + forwardXZ.second * cosH

        return Vector3(
            x = cameraPose.x + dirX * clampedDistance,
            y = floorY,
            z = cameraPose.z + dirZ * clampedDistance
        )
    }

    /**
     * Fallback: project at a fixed distance when angle calculation fails
     */
    private fun projectAtFixedDistance(
        cameraPose: CameraPose,
        angleH: Float,
        floorY: Float,
        distance: Float
    ): Vector3 {
        val forward = quaternionToForward(cameraPose)
        val forwardXZ = normalizeXZ(forward.x, forward.z)

        val cosH = kotlin.math.cos(angleH.toDouble()).toFloat()
        val sinH = kotlin.math.sin(angleH.toDouble()).toFloat()

        val dirX = forwardXZ.first * cosH - forwardXZ.second * sinH
        val dirZ = forwardXZ.first * sinH + forwardXZ.second * cosH

        return Vector3(
            x = cameraPose.x + dirX * distance,
            y = floorY,
            z = cameraPose.z + dirZ * distance
        )
    }

    /**
     * Extract forward direction from quaternion
     */
    private fun quaternionToForward(pose: CameraPose): Vector3 {
        // Forward vector (-Z in camera space) transformed by quaternion
        val qx = pose.qx
        val qy = pose.qy
        val qz = pose.qz
        val qw = pose.qw

        // Rotate (0, 0, -1) by quaternion
        val x = 2f * (qx * qz + qw * qy)
        val y = 2f * (qy * qz - qw * qx)
        val z = 1f - 2f * (qx * qx + qy * qy)

        return Vector3(-x, -y, -z)
    }

    /**
     * Normalize XZ components to unit vector
     */
    private fun normalizeXZ(x: Float, z: Float): Pair<Float, Float> {
        val length = kotlin.math.sqrt(x * x + z * z)
        return if (length > 0.001f) {
            Pair(x / length, z / length)
        } else {
            Pair(0f, 1f)  // Default forward
        }
    }

    private const val DEFAULT_PROJECTION_DISTANCE = 2f  // meters
    private const val MAX_PROJECTION_DISTANCE = 10f  // meters
}
