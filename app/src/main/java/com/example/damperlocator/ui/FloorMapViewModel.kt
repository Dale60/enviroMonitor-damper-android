package com.example.damperlocator.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.damperlocator.floorplan.AnchorPlacementState
import com.example.damperlocator.floorplan.ArTrackingState
import com.example.damperlocator.floorplan.FeatureType
import com.example.damperlocator.floorplan.FloorMapCaptureState
import com.example.damperlocator.floorplan.FloorPlan
import com.example.damperlocator.floorplan.FloorPlanPin
import com.example.damperlocator.floorplan.FloorPlanRepository
import com.example.damperlocator.floorplan.MapAnchor
import com.example.damperlocator.floorplan.PolygonCalculator
import com.example.damperlocator.floorplan.RecordingState
import com.example.damperlocator.floorplan.RelocalizationState
import com.example.damperlocator.floorplan.RoomFeature
import com.example.damperlocator.floorplan.Vector2
import com.example.damperlocator.floorplan.Vector3
import com.example.damperlocator.sensors.CompassManager
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FloorMapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FloorPlanRepository(application)
    private val compassManager = CompassManager(application)

    val floorPlans: StateFlow<List<FloorPlan>> = repository.floorPlans

    private val _captureState = MutableStateFlow(FloorMapCaptureState())
    val captureState: StateFlow<FloorMapCaptureState> = _captureState.asStateFlow()

    private val _currentFloorPlan = MutableStateFlow<FloorPlan?>(null)
    val currentFloorPlan: StateFlow<FloorPlan?> = _currentFloorPlan.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.FloorMapList)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    // Sensor readings
    val compassHeading: StateFlow<Float> = compassManager.heading
    val devicePitch: StateFlow<Float> = compassManager.pitch
    val deviceRoll: StateFlow<Float> = compassManager.roll
    val compassAvailable: StateFlow<Boolean> = compassManager.isAvailable

    fun navigateTo(screen: Screen) {
        _screen.value = screen
    }

    fun startCapture(existingPlanId: String? = null) {
        compassManager.start()

        val existing = existingPlanId?.let { repository.getById(it) }
        _currentFloorPlan.value = existing ?: FloorPlan(
            name = "Floor Plan ${System.currentTimeMillis()}"
        )
        _captureState.value = FloorMapCaptureState(
            currentPins = existing?.pins ?: emptyList(),
            isArSessionActive = true
        )
    }

    fun stopCapture() {
        compassManager.stop()
        _captureState.value = _captureState.value.copy(isArSessionActive = false)
    }

    fun updateTrackingState(state: ArTrackingState, isPlaneDetected: Boolean) {
        _captureState.value = _captureState.value.copy(
            trackingState = state,
            isPlaneDetected = isPlaneDetected
        )
    }

    // ==================== Continuous Recording ====================

    companion object {
        private const val MIN_DISTANCE_BETWEEN_POINTS = 0.15f  // 15cm minimum between samples (more responsive trail)
        private const val CLOSE_TO_START_THRESHOLD = 0.5f      // 50cm to consider "back at start"
    }

    /**
     * Start recording the path - first corner is automatically marked
     */
    fun startRecording(initialPosition: Vector3) {
        val startPos = Vector2(initialPosition.x, initialPosition.z)
        android.util.Log.d("FloorMap", "Started recording at position: $startPos (3D: $initialPosition)")
        _captureState.value = _captureState.value.copy(
            recordingState = RecordingState.RECORDING,
            pathPoints = listOf(startPos),
            cornerPoints = listOf(startPos),  // First corner is the start
            cornerPoints3d = listOf(initialPosition),  // 3D start corner for AR
            currentPosition = startPos,
            currentPosition3d = initialPosition,
            startPosition = startPos,
            startPosition3d = initialPosition,  // 3D start for AR marker
            distanceTraveled = 0f,
            distanceToStart = 0f
        )
    }

    /**
     * Mark the current position as a corner point
     */
    fun markCorner() {
        val current = _captureState.value
        if (current.recordingState != RecordingState.RECORDING) return

        val pos = current.currentPosition ?: return
        val pos3d = current.currentPosition3d
        android.util.Log.d("FloorMap", "Marked corner #${current.cornerPoints.size + 1} at: $pos (3D: $pos3d)")

        _captureState.value = current.copy(
            cornerPoints = current.cornerPoints + pos,
            cornerPoints3d = if (pos3d != null) current.cornerPoints3d + pos3d else current.cornerPoints3d
        )
    }

    // ==================== Room Features ====================

    /**
     * Show the feature type picker dialog
     */
    fun showFeaturePicker() {
        _captureState.value = _captureState.value.copy(showFeaturePicker = true)
    }

    /**
     * Hide the feature type picker dialog
     */
    fun hideFeaturePicker() {
        _captureState.value = _captureState.value.copy(showFeaturePicker = false)
    }

    /**
     * Add a feature at the current position
     */
    fun addFeature(
        type: FeatureType,
        label: String? = null,
        photoPath: String? = null,
        bleDeviceAddress: String? = null,
        bleDeviceName: String? = null,
        notes: String? = null
    ) {
        val current = _captureState.value
        val pos = current.currentPosition ?: return
        val pos3d = current.currentPosition3d

        val feature = RoomFeature(
            type = type,
            position = pos,
            position3d = pos3d,
            label = label,
            photoPath = photoPath,
            bleDeviceAddress = bleDeviceAddress,
            bleDeviceName = bleDeviceName,
            notes = notes
        )

        android.util.Log.d("FloorMap", "Added feature: ${type.displayName} at $pos (3D: $pos3d)")

        _captureState.value = current.copy(
            features = current.features + feature,
            showFeaturePicker = false
        )
    }

    /**
     * Remove a feature by ID
     */
    fun removeFeature(featureId: String) {
        val current = _captureState.value
        _captureState.value = current.copy(
            features = current.features.filter { it.id != featureId }
        )
    }

    /**
     * Update a feature's photo
     */
    fun updateFeaturePhoto(featureId: String, photoPath: String?) {
        val current = _captureState.value
        _captureState.value = current.copy(
            features = current.features.map {
                if (it.id == featureId) it.copy(photoPath = photoPath) else it
            }
        )
    }

    /**
     * Called continuously while recording to add path points
     */
    fun updatePosition(position3d: Vector3) {
        val current = _captureState.value
        if (current.recordingState != RecordingState.RECORDING) return

        val newPoint = Vector2(position3d.x, position3d.z)
        val pathPoints = current.pathPoints

        if (pathPoints.isEmpty()) return

        val lastPoint = pathPoints.last()
        val distFromLast = distance(lastPoint, newPoint)
        val distToStart = current.startPosition?.let { distance(newPoint, it) } ?: 0f

        // Only add point if we've moved enough
        if (distFromLast >= MIN_DISTANCE_BETWEEN_POINTS) {
            val newDistance = current.distanceTraveled + distFromLast
            android.util.Log.d("FloorMap", "Added point #${pathPoints.size + 1}, dist=${"%.2f".format(distFromLast)}m, total=${"%.2f".format(newDistance)}m")

            _captureState.value = current.copy(
                pathPoints = pathPoints + newPoint,
                currentPosition = newPoint,  // Always track current position for corner marking
                currentPosition3d = position3d,  // Track 3D position for AR markers
                distanceTraveled = newDistance,
                distanceToStart = distToStart
            )
        } else {
            // Always update current position and distance to start
            _captureState.value = current.copy(
                currentPosition = newPoint,
                currentPosition3d = position3d,  // Track 3D position for AR markers
                distanceToStart = distToStart
            )
        }
    }

    /**
     * Check if user is close enough to starting point to close the loop
     */
    fun isNearStart(): Boolean {
        val current = _captureState.value
        val distToStart = current.distanceToStart ?: return false
        // Need to have walked at least some distance before allowing close
        return distToStart < CLOSE_TO_START_THRESHOLD && current.distanceTraveled > 2f
    }

    /**
     * Stop recording and finalize the path
     */
    fun stopRecording(closePath: Boolean) {
        val current = _captureState.value
        if (current.recordingState != RecordingState.RECORDING) return

        var pathPoints = current.pathPoints
        var cornerPoints = current.cornerPoints

        // Smooth the raw path for display
        pathPoints = smoothPath(pathPoints)

        // If closing, add the start point at the end
        if (closePath) {
            if (pathPoints.size >= 3) {
                pathPoints = pathPoints + pathPoints.first()
            }
            if (cornerPoints.size >= 2) {
                cornerPoints = cornerPoints + cornerPoints.first()
            }
        }

        android.util.Log.d("FloorMap", "Stopped recording: ${cornerPoints.size} corners, ${pathPoints.size} path points")

        // Convert corner points to pins (corners are the clean floor plan)
        val pins = cornerPoints.mapIndexed { index, point ->
            FloorPlanPin(
                position3d = Vector3(point.x, 0f, point.y),
                position2d = point,
                label = when (index) {
                    0 -> "Start"
                    cornerPoints.size - 1 -> if (closePath) null else "End"
                    else -> "Corner ${index}"
                }
            )
        }

        // Calculate perimeter and area based on CORNERS (clean floor plan)
        val perimeter = if (cornerPoints.size >= 2) {
            PolygonCalculator.calculatePerimeter(cornerPoints, isClosed = closePath)
        } else null

        val area = if (closePath && cornerPoints.size >= 4) {
            PolygonCalculator.calculateArea(cornerPoints.dropLast(1)) // Remove duplicate end point for area
        } else null

        _captureState.value = current.copy(
            recordingState = RecordingState.COMPLETED,
            pathPoints = pathPoints,
            cornerPoints = cornerPoints,
            currentPins = pins
        )

        // Update floor plan with corners, features, and anchors
        _currentFloorPlan.value = _currentFloorPlan.value?.copy(
            pins = pins,
            cornerPoints = cornerPoints,
            features = current.features,  // Include all marked features
            anchors = current.anchors,    // Include all placed anchors
            perimeterMeters = perimeter,
            areaSquareMeters = area,
            isClosed = closePath,
            northOffsetDegrees = compassManager.heading.value
        )
    }

    /**
     * Reset recording to start over
     */
    fun resetRecording() {
        _captureState.value = _captureState.value.copy(
            recordingState = RecordingState.IDLE,
            pathPoints = emptyList(),
            cornerPoints = emptyList(),
            cornerPoints3d = emptyList(),
            features = emptyList(),
            anchors = emptyList(),
            currentPosition = null,
            currentPosition3d = null,
            startPosition = null,
            startPosition3d = null,
            distanceTraveled = 0f,
            distanceToStart = null,
            currentPins = emptyList(),
            showFeaturePicker = false,
            anchorPlacementState = AnchorPlacementState.NONE,
            pendingAnchorLabel = null,
            relocalization = RelocalizationState(),
            pendingFeaturePhotoId = null
        )
    }

    private fun distance(a: Vector2, b: Vector2): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Simple path smoothing using moving average
     */
    private fun smoothPath(points: List<Vector2>, windowSize: Int = 3): List<Vector2> {
        if (points.size < windowSize) return points

        return points.mapIndexed { index, _ ->
            val start = maxOf(0, index - windowSize / 2)
            val end = minOf(points.size, index + windowSize / 2 + 1)
            val window = points.subList(start, end)

            Vector2(
                x = window.map { it.x }.average().toFloat(),
                y = window.map { it.y }.average().toFloat()
            )
        }
    }

    // ==================== Anchor Management (Incremental Mapping) ====================

    /**
     * Start anchor placement flow - user will walk to doorway and take a photo
     */
    fun startAnchorPlacement(label: String = "Doorway") {
        _captureState.value = _captureState.value.copy(
            anchorPlacementState = AnchorPlacementState.POSITIONING,
            pendingAnchorLabel = label
        )
    }

    /**
     * User is at anchor position, ready to take photo
     */
    fun confirmAnchorPosition() {
        _captureState.value = _captureState.value.copy(
            anchorPlacementState = AnchorPlacementState.CAPTURING
        )
    }

    /**
     * Save anchor with photo at current position
     */
    fun saveAnchor(photoPath: String) {
        val current = _captureState.value
        val pos = current.currentPosition ?: return
        val pos3d = current.currentPosition3d ?: return
        val heading = compassManager.heading.value
        val label = current.pendingAnchorLabel ?: "Anchor"

        val anchor = MapAnchor(
            position = pos,
            position3d = pos3d,
            compassHeading = heading,
            photoPath = photoPath,
            label = label
        )

        android.util.Log.d("FloorMap", "Saved anchor '$label' at $pos, heading=$heading")

        _captureState.value = current.copy(
            anchors = current.anchors + anchor,
            anchorPlacementState = AnchorPlacementState.CONFIRMED,
            pendingAnchorLabel = null
        )

        // Also add to floor plan
        _currentFloorPlan.value = _currentFloorPlan.value?.copy(
            anchors = (_currentFloorPlan.value?.anchors ?: emptyList()) + anchor
        )
    }

    /**
     * Cancel anchor placement
     */
    fun cancelAnchorPlacement() {
        _captureState.value = _captureState.value.copy(
            anchorPlacementState = AnchorPlacementState.NONE,
            pendingAnchorLabel = null
        )
    }

    /**
     * Finish anchor placement flow
     */
    fun finishAnchorPlacement() {
        _captureState.value = _captureState.value.copy(
            anchorPlacementState = AnchorPlacementState.NONE
        )
    }

    /**
     * Start relocalization - continue mapping from existing anchor
     */
    fun startRelocalization(anchor: MapAnchor) {
        android.util.Log.d("FloorMap", "Starting relocalization to anchor: ${anchor.label}")
        _captureState.value = _captureState.value.copy(
            relocalization = RelocalizationState(
                isRelocalizing = true,
                targetAnchor = anchor,
                isMatched = false
            )
        )
    }

    /**
     * User confirms they are at the anchor position - calculate coordinate offset
     */
    fun confirmRelocalization(currentPosition3d: Vector3) {
        val current = _captureState.value
        val targetAnchor = current.relocalization.targetAnchor ?: return

        // Calculate offset between current position and anchor position
        val offsetX = targetAnchor.position.x - currentPosition3d.x
        val offsetY = targetAnchor.position.y - currentPosition3d.z

        // Calculate rotation offset based on compass headings
        val currentHeading = compassManager.heading.value
        val rotationOffset = targetAnchor.compassHeading - currentHeading

        android.util.Log.d("FloorMap", "Relocalization confirmed: offset=($offsetX, $offsetY), rotation=$rotationOffset")

        _captureState.value = current.copy(
            relocalization = current.relocalization.copy(
                isMatched = true,
                coordinateOffset = Vector2(offsetX, offsetY),
                rotationOffset = rotationOffset
            )
        )
    }

    /**
     * Cancel relocalization
     */
    fun cancelRelocalization() {
        _captureState.value = _captureState.value.copy(
            relocalization = RelocalizationState()
        )
    }

    /**
     * Apply relocalization offset to a position
     */
    fun applyRelocalizationOffset(position: Vector2): Vector2 {
        val offset = _captureState.value.relocalization.coordinateOffset ?: return position
        return Vector2(position.x + offset.x, position.y + offset.y)
    }

    /**
     * Get all anchors from current floor plan
     */
    fun getAnchors(): List<MapAnchor> {
        return _currentFloorPlan.value?.anchors ?: emptyList()
    }

    // ==================== Feature Photo Capture ====================

    /**
     * Request photo capture for a feature (after selecting feature type)
     */
    fun requestFeaturePhoto(featureId: String) {
        _captureState.value = _captureState.value.copy(
            pendingFeaturePhotoId = featureId
        )
    }

    /**
     * Save photo for pending feature
     */
    fun saveFeaturePhoto(photoPath: String) {
        val current = _captureState.value
        val featureId = current.pendingFeaturePhotoId ?: return

        _captureState.value = current.copy(
            features = current.features.map {
                if (it.id == featureId) it.copy(photoPath = photoPath) else it
            },
            pendingFeaturePhotoId = null
        )

        android.util.Log.d("FloorMap", "Saved photo for feature $featureId: $photoPath")
    }

    /**
     * Cancel pending feature photo
     */
    fun cancelFeaturePhoto() {
        _captureState.value = _captureState.value.copy(
            pendingFeaturePhotoId = null
        )
    }

    // ==================== Legacy Pin Methods (kept for compatibility) ====================

    fun addPin(position3d: Vector3, isOnPlane: Boolean) {
        val pin = FloorPlanPin(
            position3d = position3d,
            isOnDetectedPlane = isOnPlane
        )

        val current = _captureState.value
        _captureState.value = current.copy(
            currentPins = current.currentPins + pin
        )
    }

    fun undoLastPin() {
        val current = _captureState.value
        if (current.currentPins.isNotEmpty()) {
            _captureState.value = current.copy(
                currentPins = current.currentPins.dropLast(1)
            )
        }
    }

    fun closePolygon() {
        val current = _captureState.value
        val pins = current.currentPins

        if (pins.size >= 3) {
            val points2d = pins.map { it.position2d }
            val perimeter = PolygonCalculator.calculatePerimeter(points2d, isClosed = true)
            val area = PolygonCalculator.calculateArea(points2d)

            val floorPlan = _currentFloorPlan.value?.copy(
                pins = pins,
                perimeterMeters = perimeter,
                areaSquareMeters = area,
                isClosed = true,
                northOffsetDegrees = compassManager.heading.value
            )

            floorPlan?.let {
                viewModelScope.launch {
                    repository.save(it)
                    _currentFloorPlan.value = it
                }
            }
        }
    }

    fun updateFloorPlanName(name: String) {
        _currentFloorPlan.value = _currentFloorPlan.value?.copy(name = name)
    }

    fun saveCurrentPlan() {
        _currentFloorPlan.value?.let { plan ->
            val pins = _captureState.value.currentPins
            val points2d = pins.map { it.position2d }

            val perimeter = if (pins.size >= 2) {
                PolygonCalculator.calculatePerimeter(points2d, isClosed = plan.isClosed)
            } else null

            val area = if (pins.size >= 3 && plan.isClosed) {
                PolygonCalculator.calculateArea(points2d)
            } else null

            viewModelScope.launch {
                repository.save(plan.copy(
                    pins = pins,
                    perimeterMeters = perimeter,
                    areaSquareMeters = area,
                    northOffsetDegrees = compassManager.heading.value
                ))
            }
        }
    }

    fun deleteFloorPlan(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun exportFloorPlan(id: String): String? {
        return repository.getById(id)?.let { repository.exportToJson(it) }
    }

    fun importFloorPlan(json: String) {
        viewModelScope.launch {
            repository.importFromJson(json)
        }
    }

    fun loadFloorPlan(id: String) {
        _currentFloorPlan.value = repository.getById(id)
    }

    fun clearCurrentPlan() {
        _currentFloorPlan.value = null
        _captureState.value = FloorMapCaptureState()
    }
}
