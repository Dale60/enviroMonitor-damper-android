package com.example.damperlocator.ar

import android.app.Activity
import android.content.Context
import com.example.damperlocator.floorplan.ArTrackingState
import com.example.damperlocator.floorplan.Vector3
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ArSessionManager(private val context: Context) {

    private var session: Session? = null

    private val _trackingState = MutableStateFlow(ArTrackingState.NOT_AVAILABLE)
    val trackingState: StateFlow<ArTrackingState> = _trackingState.asStateFlow()

    private val _isPlaneDetected = MutableStateFlow(false)
    val isPlaneDetected: StateFlow<Boolean> = _isPlaneDetected.asStateFlow()

    private val _estimatedFloorY = MutableStateFlow(0f)
    val estimatedFloorY: StateFlow<Float> = _estimatedFloorY.asStateFlow()

    /**
     * Check if ARCore is available and up to date
     */
    fun checkArAvailability(): ArAvailabilityResult {
        return when (ArCoreApk.getInstance().checkAvailability(context)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArAvailabilityResult.Ready
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArAvailabilityResult.NeedsInstall
            else -> ArAvailabilityResult.NotSupported
        }
    }

    /**
     * Request ARCore installation if needed
     */
    fun requestInstall(activity: Activity): Boolean {
        return try {
            ArCoreApk.getInstance().requestInstall(
                activity,
                true
            ) == ArCoreApk.InstallStatus.INSTALLED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Initialize AR session - call after permissions granted
     */
    fun createSession(activity: Activity): Boolean {
        if (session != null) return true

        return try {
            session = Session(activity).apply {
                val config = Config(this).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    focusMode = Config.FocusMode.AUTO
                }
                configure(config)
            }
            _trackingState.value = ArTrackingState.PAUSED
            true
        } catch (_: Exception) {
            _trackingState.value = ArTrackingState.NOT_AVAILABLE
            false
        }
    }

    /**
     * Resume AR session - call in onResume
     */
    fun resume(): Boolean {
        return try {
            session?.resume()
            _trackingState.value = ArTrackingState.TRACKING
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Pause AR session - call in onPause
     */
    fun pause() {
        try {
            session?.pause()
            _trackingState.value = ArTrackingState.PAUSED
        } catch (_: Exception) {
            // Ignore
        }
    }

    /**
     * Destroy session - call in onDestroy
     */
    fun destroy() {
        try {
            session?.close()
        } catch (_: Exception) {
            // Ignore
        }
        session = null
        _trackingState.value = ArTrackingState.NOT_AVAILABLE
    }

    /**
     * Update frame and return current tracking data
     */
    fun update(): Frame? {
        val session = this.session ?: return null

        return try {
            val frame = session.update()

            // Update planes list
            val planes = session.getAllTrackables(Plane::class.java)
                .filter { it.trackingState == TrackingState.TRACKING }
                .filter { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }

            _isPlaneDetected.value = planes.isNotEmpty()

            // Update estimated floor Y from detected planes
            if (planes.isNotEmpty()) {
                val avgY = planes.map { it.centerPose.ty() }.average().toFloat()
                _estimatedFloorY.value = avgY
            }

            frame
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Raycast from screen tap to detected plane
     * Returns 3D hit position or null if no plane hit
     */
    fun hitTest(frame: Frame, screenX: Float, screenY: Float): Vector3? {
        val hits = frame.hitTest(screenX, screenY)

        // Find first horizontal plane hit
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane &&
                trackable.isPoseInPolygon(hit.hitPose) &&
                trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING) {

                val pose = hit.hitPose
                return Vector3(
                    x = pose.tx(),
                    y = pose.ty(),
                    z = pose.tz()
                )
            }
        }
        return null
    }

    /**
     * Get camera pose for fallback projection
     */
    fun getCameraPose(frame: Frame): CameraPose? {
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) return null

        val pose = camera.pose
        return CameraPose(
            x = pose.tx(),
            y = pose.ty(),
            z = pose.tz(),
            qx = pose.qx(),
            qy = pose.qy(),
            qz = pose.qz(),
            qw = pose.qw()
        )
    }

    fun getSession(): Session? = session
}

sealed class ArAvailabilityResult {
    object Ready : ArAvailabilityResult()
    object NeedsInstall : ArAvailabilityResult()
    object NotSupported : ArAvailabilityResult()
}

data class CameraPose(
    val x: Float,
    val y: Float,
    val z: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val qw: Float
)
