package com.example.damperlocator.floorplan

import java.util.UUID

/**
 * Represents a 3D point captured from ARCore
 */
data class Vector3(
    val x: Float,  // meters, relative to AR session origin
    val y: Float,  // meters, vertical (floor height)
    val z: Float   // meters, relative to AR session origin
)

/**
 * 2D representation after Y-axis projection
 */
data class Vector2(
    val x: Float,  // meters
    val y: Float   // meters (was Z in 3D)
)

/**
 * A placed pin with metadata
 */
data class FloorPlanPin(
    val id: String = UUID.randomUUID().toString(),
    val position3d: Vector3,
    val position2d: Vector2 = Vector2(position3d.x, position3d.z),
    val placedAtMs: Long = System.currentTimeMillis(),
    val label: String? = null,
    val isOnDetectedPlane: Boolean = true
)

/**
 * Complete floorplan with all pins forming a closed or open path
 */
data class FloorPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val modifiedAtMs: Long = System.currentTimeMillis(),
    val pins: List<FloorPlanPin> = emptyList(),
    val cornerPoints: List<Vector2> = emptyList(),  // User-marked corners for clean floor plan
    val features: List<RoomFeature> = emptyList(),  // Room features (doors, beacons, dampers, etc.)
    val northOffsetDegrees: Float = 0f,
    val referenceFloorY: Float = 0f,
    val perimeterMeters: Float? = null,
    val areaSquareMeters: Float? = null,
    val isClosed: Boolean = false
)

/**
 * State for the AR capture session
 */
data class FloorMapCaptureState(
    val isArSessionActive: Boolean = false,
    val isPlaneDetected: Boolean = false,
    val currentPins: List<FloorPlanPin> = emptyList(),
    val compassHeading: Float = 0f,
    val devicePitch: Float = 0f,
    val deviceRoll: Float = 0f,
    val trackingState: ArTrackingState = ArTrackingState.NOT_AVAILABLE,
    val errorMessage: String? = null,
    // Continuous recording state
    val recordingState: RecordingState = RecordingState.IDLE,
    val pathPoints: List<Vector2> = emptyList(),
    val cornerPoints: List<Vector2> = emptyList(),  // User-marked corners
    val features: List<RoomFeature> = emptyList(),  // Room features placed during mapping
    val distanceTraveled: Float = 0f,
    val startPosition: Vector2? = null,
    val distanceToStart: Float? = null,
    val currentPosition: Vector2? = null,  // Live position for corner/feature marking
    val showFeaturePicker: Boolean = false  // Show feature type picker dialog
)

enum class ArTrackingState {
    NOT_AVAILABLE,
    TRACKING,
    PAUSED,
    STOPPED
}

enum class RecordingState {
    IDLE,       // Not recording, waiting for user to start
    RECORDING,  // Actively recording path
    COMPLETED   // Recording finished, path closed
}

/**
 * Types of room features that can be marked on the floor plan
 */
enum class FeatureType(val displayName: String, val icon: String) {
    DOOR("Door", "🚪"),
    BEACON("Beacon", "📡"),
    DAMPER("Damper", "🌀"),
    HVAC_VENT("HVAC Vent", "💨"),
    HVAC_UNIT("HVAC Unit", "❄️"),
    THERMOSTAT("Thermostat", "🌡️"),
    PHOTO("Photo Point", "📷"),
    OTHER("Other", "📍")
}

/**
 * A room feature/appliance marked at a specific location
 */
data class RoomFeature(
    val id: String = UUID.randomUUID().toString(),
    val type: FeatureType,
    val position: Vector2,
    val label: String? = null,
    val photoPath: String? = null,          // Path to attached photo
    val bleDeviceAddress: String? = null,   // For beacons - linked BLE device
    val bleDeviceName: String? = null,      // BLE device name if linked
    val notes: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)
