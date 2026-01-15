package com.example.damperlocator.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.damperlocator.ar.ArAvailabilityResult
import com.example.damperlocator.ar.BackgroundRenderer
import com.example.damperlocator.floorplan.ArTrackingState
import com.example.damperlocator.floorplan.FloorMapCaptureState
import com.example.damperlocator.floorplan.FloorPlan
import com.example.damperlocator.floorplan.RecordingState
import com.example.damperlocator.floorplan.Vector2
import com.example.damperlocator.floorplan.Vector3
import com.example.damperlocator.ui.components.FeedbackHelper
import com.example.damperlocator.ui.components.PathMinimap
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Composable
fun FloorMapCaptureScreen(
    floorPlan: FloorPlan?,
    captureState: FloorMapCaptureState,
    compassHeading: Float,
    devicePitch: Float,
    deviceRoll: Float,
    onStartRecording: (Vector3) -> Unit,
    onUpdatePosition: (Vector3) -> Unit,
    onStopRecording: (Boolean) -> Unit,
    onResetRecording: () -> Unit,
    isNearStart: Boolean,
    onUpdateName: (String) -> Unit,
    onTrackingStateChanged: (ArTrackingState, Boolean) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var arAvailability by remember { mutableStateOf<ArAvailabilityResult?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var currentCameraPosition by remember { mutableStateOf<Vector3?>(null) }

    // Use rememberUpdatedState to ensure callbacks always have latest values
    val currentRecordingState by rememberUpdatedState(captureState.recordingState)
    val currentOnUpdatePosition by rememberUpdatedState(onUpdatePosition)

    // Feedback helper for audio/haptic
    val feedbackHelper = remember { FeedbackHelper(context) }
    var lastPointCount by remember { mutableStateOf(0) }
    var wasNearStart by remember { mutableStateOf(false) }
    var wasApproaching by remember { mutableStateOf(false) }

    // Cleanup feedback helper
    DisposableEffect(Unit) {
        onDispose { feedbackHelper.release() }
    }

    // Trigger feedback when path points increase (new point recorded)
    LaunchedEffect(captureState.pathPoints.size) {
        val currentCount = captureState.pathPoints.size
        if (currentCount > lastPointCount && currentCount > 1) {
            feedbackHelper.onPointRecorded()
        }
        lastPointCount = currentCount
    }

    // Trigger feedback when approaching or reaching start
    LaunchedEffect(captureState.distanceToStart, captureState.distanceTraveled) {
        val dist = captureState.distanceToStart
        val walked = captureState.distanceTraveled

        if (dist != null && walked > 2f) {
            when {
                dist < 0.5f && !wasNearStart -> {
                    feedbackHelper.onNearStart()
                    wasNearStart = true
                }
                dist < 2f && dist >= 0.5f && !wasApproaching -> {
                    feedbackHelper.onApproachingStart()
                    wasApproaching = true
                }
                dist >= 2f -> {
                    wasApproaching = false
                    wasNearStart = false
                }
            }
        }
    }

    // Trigger feedback on recording state changes
    LaunchedEffect(captureState.recordingState) {
        if (captureState.recordingState == RecordingState.COMPLETED) {
            feedbackHelper.onMappingComplete()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        arAvailability = when (availability) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArAvailabilityResult.Ready
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArAvailabilityResult.NeedsInstall
            else -> ArAvailabilityResult.NotSupported
        }
        onDispose { }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val screenState = when {
            !hasCameraPermission -> "no_permission"
            arAvailability == ArAvailabilityResult.NotSupported -> "not_supported"
            arAvailability == ArAvailabilityResult.NeedsInstall -> "needs_install"
            else -> "ready"
        }

        when (screenState) {
            "no_permission" -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Camera permission is required for AR floor mapping")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(text = "Grant Camera Permission")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onBack) { Text(text = "Go Back") }
                }
            }
            "not_supported" -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "ARCore is not supported on this device")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text(text = "Go Back") }
                }
            }
            "needs_install" -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "ARCore needs to be installed")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text(text = "Go Back") }
                }
            }
            else -> {
                // AR Camera View with continuous position tracking
                ArCameraView(
                    isRecording = captureState.recordingState == RecordingState.RECORDING,
                    onFrameUpdate = { frame, session, isPlaneDetected ->
                        onTrackingStateChanged(
                            if (frame.camera.trackingState == TrackingState.TRACKING)
                                ArTrackingState.TRACKING else ArTrackingState.PAUSED,
                            isPlaneDetected
                        )
                    },
                    onPositionUpdate = { position ->
                        currentCameraPosition = position
                        // Use rememberUpdatedState values to ensure we have latest state
                        if (currentRecordingState == RecordingState.RECORDING) {
                            android.util.Log.d("FloorMap", "Sending position update: $position")
                            currentOnUpdatePosition(position)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // HUD Overlay
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    // Top bar - Back button and plan name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        ) {
                            Text(text = "Back", color = Color.White)
                        }

                        TextField(
                            value = floorPlan?.name ?: "",
                            onValueChange = onUpdateName,
                            label = { Text("Plan Name") },
                            singleLine = true,
                            modifier = Modifier.width(180.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Minimap in top-left area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PathMinimap(
                            pathPoints = captureState.pathPoints,
                            currentPosition = currentCameraPosition?.let { Vector2(it.x, it.z) },
                            isRecording = captureState.recordingState == RecordingState.RECORDING,
                            distanceTraveled = captureState.distanceTraveled
                        )

                        // Instructions panel - large and clear
                        Column(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                                .width(190.dp)
                        ) {
                            when (captureState.recordingState) {
                                RecordingState.IDLE -> {
                                    // Phone orientation guidance
                                    Text(
                                        text = "HOW TO HOLD PHONE",
                                        color = Color.Yellow,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Hold phone UPRIGHT (vertical) and point camera at the FLOOR ahead of you",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        lineHeight = 15.sp
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "STEPS",
                                        color = Color.Yellow,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "1. Stand in a corner",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "2. Tap START below",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "3. Walk the room edges",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "4. Return to start",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "The map on the left will draw your path as you walk!",
                                        color = Color.Cyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 14.sp
                                    )
                                }
                                RecordingState.RECORDING -> {
                                    // Show different message based on progress
                                    val distanceWalked = captureState.distanceTraveled

                                    if (distanceWalked < 0.5f) {
                                        // Just started - not moved yet
                                        Text(
                                            text = "RECORDING!",
                                            color = Color.Red,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "NOW WALK!",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Walk along the walls of the room. Keep the phone pointing at the floor.",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            lineHeight = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Watch the blue line appear on the map!",
                                            color = Color.Cyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else if (isNearStart && distanceWalked > 2f) {
                                        // Back at start!
                                        Text(
                                            text = "YOU'RE BACK!",
                                            color = Color.Green,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Great! You've returned to the start point.",
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Tap the green FINISH button below!",
                                            color = Color.Green,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            lineHeight = 18.sp
                                        )
                                    } else {
                                        // In progress - walking
                                        Text(
                                            text = "GOOD! KEEP GOING",
                                            color = Color.Cyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Walk along the walls. The blue line on the map shows your path.",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            lineHeight = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "GOAL:",
                                            color = Color.Yellow,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Walk back to the GREEN dot (your start)",
                                            color = Color.Green,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        captureState.distanceToStart?.let { dist ->
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Distance to start: %.1fm".format(dist),
                                                color = if (dist < 1.5f) Color.Yellow else Color.Gray,
                                                fontSize = 13.sp,
                                                fontWeight = if (dist < 1.5f) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                                RecordingState.COMPLETED -> {
                                    Text(
                                        text = "DONE!",
                                        color = Color.Green,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Your floor map is ready!",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tap SAVE to keep it, or RESET to try again.",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Status bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Distance: %.1fm".format(captureState.distanceTraveled),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            captureState.distanceToStart?.let { dist ->
                                Text(
                                    text = "To start: %.1fm".format(dist),
                                    color = if (dist < 0.5f && captureState.distanceTraveled > 2f)
                                        Color.Green else Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        val trackingText = when (captureState.trackingState) {
                            ArTrackingState.TRACKING -> "Tracking"
                            ArTrackingState.PAUSED -> "Paused"
                            ArTrackingState.STOPPED -> "Stopped"
                            ArTrackingState.NOT_AVAILABLE -> "N/A"
                        }
                        val trackingColor = when (captureState.trackingState) {
                            ArTrackingState.TRACKING -> Color.Green
                            else -> Color.Yellow
                        }
                        Text(
                            text = trackingText,
                            color = trackingColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Control buttons - big and clear
                    when (captureState.recordingState) {
                        RecordingState.IDLE -> {
                            Column {
                                Button(
                                    onClick = {
                                        currentCameraPosition?.let { onStartRecording(it) }
                                    },
                                    enabled = captureState.trackingState == ArTrackingState.TRACKING,
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Text(
                                        text = if (captureState.trackingState == ArTrackingState.TRACKING)
                                            "START MAPPING" else "Please wait...",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (captureState.trackingState != ArTrackingState.TRACKING) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Point camera at the floor and move slowly until tracking starts",
                                        color = Color.Yellow,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        RecordingState.RECORDING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onResetRecording,
                                    modifier = Modifier.weight(1f).height(64.dp)
                                ) {
                                    Text(text = "CANCEL", fontSize = 14.sp)
                                }

                                Button(
                                    onClick = { onStopRecording(isNearStart) },
                                    modifier = Modifier.weight(2f).height(64.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isNearStart) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                    )
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (isNearStart) "FINISH & CLOSE" else "FINISH EARLY",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (!isNearStart) {
                                            Text(
                                                text = "(path won't be closed)",
                                                fontSize = 10.sp,
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        RecordingState.COMPLETED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onResetRecording,
                                    modifier = Modifier.weight(1f).height(64.dp)
                                ) {
                                    Text(text = "TRY AGAIN", fontSize = 14.sp)
                                }

                                Button(
                                    onClick = onSave,
                                    modifier = Modifier.weight(2f).height(64.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2196F3)
                                    )
                                ) {
                                    Text(
                                        text = "SAVE MAP",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArCameraView(
    isRecording: Boolean,
    onFrameUpdate: (Frame, Session, Boolean) -> Unit,
    onPositionUpdate: (Vector3) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var glSurfaceView by remember { mutableStateOf<GLSurfaceView?>(null) }
    var arSession by remember { mutableStateOf<Session?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    glSurfaceView?.onResume()
                    try { arSession?.resume() } catch (_: Exception) {}
                }
                Lifecycle.Event.ON_PAUSE -> {
                    glSurfaceView?.onPause()
                    try { arSession?.pause() } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { arSession?.close() } catch (_: Exception) {}
            arSession = null
        }
    }

    AndroidView(
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                glSurfaceView = this
                preserveEGLContextOnPause = true
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 0, 16, 0)
                holder.setFormat(android.graphics.PixelFormat.OPAQUE)

                val backgroundRenderer = BackgroundRenderer()
                var session: Session? = null
                var viewWidth = 0
                var viewHeight = 0

                fun getDisplayRotation(): Int {
                    val windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    return windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
                }

                setRenderer(object : GLSurfaceView.Renderer {
                    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                        Log.d("ArCameraView", "onSurfaceCreated")
                        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

                        val textureId = backgroundRenderer.createOnGlThread()

                        try {
                            val activity = ctx as? android.app.Activity ?: return
                            session = Session(activity)
                            arSession = session

                            val arConfig = Config(session).apply {
                                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                                focusMode = Config.FocusMode.AUTO
                            }
                            session?.configure(arConfig)
                            session?.setCameraTextureName(textureId)

                            if (viewWidth > 0 && viewHeight > 0) {
                                session?.setDisplayGeometry(getDisplayRotation(), viewWidth, viewHeight)
                            }

                            session?.resume()
                        } catch (e: Exception) {
                            Log.e("ArCameraView", "Error creating session", e)
                        }
                    }

                    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                        viewWidth = width
                        viewHeight = height
                        GLES20.glViewport(0, 0, width, height)
                        try {
                            session?.setDisplayGeometry(getDisplayRotation(), width, height)
                        } catch (_: Exception) {}
                    }

                    private var frameCount = 0

                    override fun onDrawFrame(gl: GL10?) {
                        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

                        val sess = session ?: return

                        try {
                            val frame = sess.update()
                            backgroundRenderer.draw(frame)

                            val camera = frame.camera
                            if (camera.trackingState == TrackingState.TRACKING) {
                                val pose = camera.pose
                                // Report camera position (project to floor level)
                                val position = Vector3(pose.tx(), pose.ty(), pose.tz())

                                // Update position every ~5 frames to reduce overhead
                                if (frameCount % 5 == 0) {
                                    (ctx as? android.app.Activity)?.runOnUiThread {
                                        onPositionUpdate(position)
                                    }
                                }
                            }

                            frameCount++

                            val planes = sess.getAllTrackables(Plane::class.java)
                                .filter { it.trackingState == TrackingState.TRACKING }
                                .filter { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }

                            (ctx as? android.app.Activity)?.runOnUiThread {
                                onFrameUpdate(frame, sess, planes.isNotEmpty())
                            }
                        } catch (_: Exception) {}
                    }
                })

                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        },
        modifier = modifier
    )
}
