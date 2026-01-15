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
 * Complete floorplan with all pins forming a closed or open path.
 * Can represent a single room or an entire floor with multiple rooms.
 */
data class FloorPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val roomLabel: String? = null,          // "Kitchen", "Living Room", etc.
    val createdAtMs: Long = System.currentTimeMillis(),
    val modifiedAtMs: Long = System.currentTimeMillis(),
    val pins: List<FloorPlanPin> = emptyList(),
    val cornerPoints: List<Vector2> = emptyList(),  // User-marked corners for clean floor plan
    val features: List<RoomFeature> = emptyList(),  // Room features (doors, beacons, dampers, etc.)
    val anchors: List<MapAnchor> = emptyList(),     // Anchor points for incremental mapping
    val northOffsetDegrees: Float = 0f,
    val referenceFloorY: Float = 0f,
    val perimeterMeters: Float? = null,
    val areaSquareMeters: Float? = null,
    val isClosed: Boolean = false,
    // Multi-room support
    val parentBuildingId: String? = null,   // ID of parent building if part of one
    val originOffset: Vector2 = Vector2(0f, 0f),  // Offset from building origin
    val rotationDegrees: Float = 0f         // Rotation to align with building
)

/**
 * A 3D marker for AR rendering
 */
data class ArMarker(
    val position3d: Vector3,
    val color: Int,  // ARGB color
    val type: MarkerType
)

enum class MarkerType {
    CORNER,
    FEATURE,
    START
}

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
    val cornerPoints: List<Vector2> = emptyList(),  // User-marked corners (2D for minimap)
    val cornerPoints3d: List<Vector3> = emptyList(),  // 3D positions for AR rendering
    val features: List<RoomFeature> = emptyList(),  // Room features placed during mapping
    val anchors: List<MapAnchor> = emptyList(),     // Anchors placed during mapping
    val distanceTraveled: Float = 0f,
    val startPosition: Vector2? = null,
    val startPosition3d: Vector3? = null,  // 3D start position for AR marker
    val distanceToStart: Float? = null,
    val currentPosition: Vector2? = null,  // Live position for corner/feature marking
    val currentPosition3d: Vector3? = null,  // 3D position for AR
    val showFeaturePicker: Boolean = false,  // Show feature type picker dialog
    // Anchor placement
    val anchorPlacementState: AnchorPlacementState = AnchorPlacementState.NONE,
    val pendingAnchorLabel: String? = null,
    // Relocalization (continuing from existing anchor)
    val relocalization: RelocalizationState = RelocalizationState(),
    // Photo capture for features
    val pendingFeaturePhotoId: String? = null  // Feature waiting for photo capture
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
    val position3d: Vector3? = null,        // 3D position for AR rendering
    val label: String? = null,
    val photoPath: String? = null,          // Path to attached photo
    val bleDeviceAddress: String? = null,   // For beacons - linked BLE device
    val bleDeviceName: String? = null,      // BLE device name if linked
    val notes: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

// ==================== Incremental Mapping ====================

/**
 * An anchor point for connecting mapping sessions.
 * Place at doorways or connection points between rooms.
 * Used to relocalize and continue mapping from a known position.
 */
data class MapAnchor(
    val id: String = UUID.randomUUID().toString(),
    val position: Vector2,
    val position3d: Vector3,
    val compassHeading: Float,              // Device compass heading when anchor placed
    val photoPath: String,                  // Reference photo for visual relocalization
    val label: String = "Anchor",           // "Doorway to Kitchen", "Main entrance"
    val connectedRoomId: String? = null,    // ID of connected room (for multi-room)
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * A building containing multiple mapped rooms connected by anchors
 */
data class MappedBuilding(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String? = null,            // Site address for reference
    val rooms: List<FloorPlan> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val modifiedAtMs: Long = System.currentTimeMillis()
)

/**
 * State for anchor placement flow
 */
enum class AnchorPlacementState {
    NONE,           // No anchor being placed
    POSITIONING,    // User is positioning at anchor location
    CAPTURING,      // Taking reference photo
    CONFIRMED       // Anchor placed and confirmed
}

/**
 * State for relocalization flow (continuing from anchor)
 */
data class RelocalizationState(
    val isRelocalizing: Boolean = false,
    val targetAnchor: MapAnchor? = null,    // Anchor we're trying to match
    val isMatched: Boolean = false,          // Successfully relocalized
    val coordinateOffset: Vector2? = null,   // Offset to apply to new coordinates
    val rotationOffset: Float = 0f           // Rotation to align with existing map
)
