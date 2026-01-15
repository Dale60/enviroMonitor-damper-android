package com.example.damperlocator.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.damperlocator.floorplan.ArTrackingState
import com.example.damperlocator.floorplan.FloorMapCaptureState
import com.example.damperlocator.floorplan.FloorPlan
import com.example.damperlocator.floorplan.FloorPlanPin
import com.example.damperlocator.floorplan.FloorPlanRepository
import com.example.damperlocator.floorplan.PolygonCalculator
import com.example.damperlocator.floorplan.RecordingState
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
     * Start recording the path
     */
    fun startRecording(initialPosition: Vector3) {
        val startPos = Vector2(initialPosition.x, initialPosition.z)
        android.util.Log.d("FloorMap", "Started recording at position: $startPos")
        _captureState.value = _captureState.value.copy(
            recordingState = RecordingState.RECORDING,
            pathPoints = listOf(startPos),
            startPosition = startPos,
            distanceTraveled = 0f,
            distanceToStart = 0f
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
                distanceTraveled = newDistance,
                distanceToStart = distToStart
            )
        } else {
            // Always update distance to start for UI feedback
            _captureState.value = current.copy(distanceToStart = distToStart)
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

        // Smooth the path to reduce jitter
        pathPoints = smoothPath(pathPoints)

        // If closing, add the start point at the end
        if (closePath && pathPoints.size >= 3) {
            pathPoints = pathPoints + pathPoints.first()
        }

        // Convert path points to pins for storage
        val pins = pathPoints.mapIndexed { index, point ->
            FloorPlanPin(
                position3d = Vector3(point.x, 0f, point.y),
                position2d = point,
                label = if (index == 0) "Start" else null
            )
        }

        val perimeter = PolygonCalculator.calculatePerimeter(pathPoints, isClosed = closePath)
        val area = if (closePath && pathPoints.size >= 4) {
            PolygonCalculator.calculateArea(pathPoints.dropLast(1)) // Remove duplicate end point for area
        } else null

        _captureState.value = current.copy(
            recordingState = RecordingState.COMPLETED,
            pathPoints = pathPoints,
            currentPins = pins
        )

        // Update floor plan
        _currentFloorPlan.value = _currentFloorPlan.value?.copy(
            pins = pins,
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
            startPosition = null,
            distanceTraveled = 0f,
            distanceToStart = null,
            currentPins = emptyList()
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
