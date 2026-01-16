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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.damperlocator.floorplan.AnchorPlacementState
import com.example.damperlocator.floorplan.ArTrackingState
import com.example.damperlocator.floorplan.FeatureType
import com.example.damperlocator.floorplan.FloorMapCaptureState
import com.example.damperlocator.floorplan.FloorPlan
import com.example.damperlocator.floorplan.RecordingState
import com.example.damperlocator.floorplan.Vector2
import com.example.damperlocator.floorplan.Vector3
import com.example.damperlocator.ui.components.FeedbackHelper
import com.example.damperlocator.ui.components.FirstTimeOverlay
import com.example.damperlocator.ui.components.PathMinimap
import com.example.damperlocator.ui.components.hasSeenTutorial
import com.example.damperlocator.ui.components.markTutorialSeen
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
    onMarkCorner: () -> Unit,
    onShowFeaturePicker: () -> Unit,
    onHideFeaturePicker: () -> Unit,
    onAddFeature: (FeatureType, String?) -> Unit,
    onStopRecording: (Boolean) -> Unit,
    onResetRecording: () -> Unit,
    isNearStart: Boolean,
    onUpdateName: (String) -> Unit,
    onTrackingStateChanged: (ArTrackingState, Boolean) -> Unit,
    // Anchor placement callbacks
    onStartAnchorPlacement: (String) -> Unit,
    onConfirmAnchorPosition: () -> Unit,
    onTakeAnchorPhoto: () -> Unit,
    onCancelAnchorPlacement: () -> Unit,
    onFinishAnchorPlacement: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // First-time tutorial overlay
    var showTutorial by remember { mutableStateOf(!hasSeenTutorial(context)) }

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

    // Collapsible instructions panel state
    var instructionsExpanded by remember { mutableStateOf(true) }

    // Anchor placement state
    var showAnchorDialog by remember { mutableStateOf(false) }
    var anchorLabel by remember { mutableStateOf("Doorway") }

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
                // Build AR markers from capture state
                val arMarkers = remember(
                    captureState.startPosition3d,
                    captureState.cornerPoints3d,
                    captureState.features
                ) {
                    ArMarkerData(
                        startPosition = captureState.startPosition3d,
                        cornerPositions = captureState.cornerPoints3d.drop(1),  // Skip first (it's the start)
                        featurePositions = captureState.features.mapNotNull { feature ->
                            feature.position3d?.let { pos ->
                                val color = when (feature.type) {
                                    FeatureType.DAMPER -> com.example.damperlocator.ar.MarkerRenderer.COLOR_DAMPER
                                    FeatureType.HVAC_VENT -> com.example.damperlocator.ar.MarkerRenderer.COLOR_VENT
                                    FeatureType.DOOR -> com.example.damperlocator.ar.MarkerRenderer.COLOR_DOOR
                                    FeatureType.BEACON -> com.example.damperlocator.ar.MarkerRenderer.COLOR_BEACON
                                    else -> com.example.damperlocator.ar.MarkerRenderer.COLOR_OTHER
                                }
                                pos to color
                            }
                        }
                    )
                }

                // AR Camera View with continuous position tracking
                ArCameraView(
                    isRecording = captureState.recordingState == RecordingState.RECORDING,
                    markers = arMarkers,
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
                            cornerPoints = captureState.cornerPoints,
                            features = captureState.features,
                            currentPosition = currentCameraPosition?.let { Vector2(it.x, it.z) },
                            isRecording = captureState.recordingState == RecordingState.RECORDING,
                            distanceTraveled = captureState.distanceTraveled
                        )

                        // Collapsible Instructions panel
                        CollapsibleInstructionsPanel(
                            recordingState = captureState.recordingState,
                            distanceTraveled = captureState.distanceTraveled,
                            distanceToStart = captureState.distanceToStart,
                            cornerCount = captureState.cornerPoints.size,
                            featureCount = captureState.features.size,
                            isNearStart = isNearStart,
                            expanded = instructionsExpanded,
                            onToggle = { instructionsExpanded = !instructionsExpanded }
                        )
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
                            // Corner count display
                            Text(
                                text = "Corners marked: ${captureState.cornerPoints.size}",
                                color = Color.Yellow,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // MARK CORNER and ADD FEATURE buttons side by side
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // MARK WALL CORNER button
                                Button(
                                    onClick = onMarkCorner,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(72.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2196F3)  // Blue
                                    )
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "MARK WALL",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "CORNER",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "At each corner",
                                            fontSize = 9.sp,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                // ADD DAMPER/VENT button
                                Button(
                                    onClick = onShowFeaturePicker,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(72.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF9C27B0)  // Purple
                                    )
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "ADD DAMPER",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "/ VENT",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Mark HVAC equipment",
                                            fontSize = 9.sp,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                            // Feature and anchor count display
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (captureState.features.isNotEmpty()) {
                                    Text(
                                        text = "Features: ${captureState.features.size}",
                                        color = Color(0xFF9C27B0),
                                        fontSize = 12.sp
                                    )
                                }
                                if (captureState.anchors.isNotEmpty()) {
                                    Text(
                                        text = "Anchors: ${captureState.anchors.size}",
                                        color = Color(0xFFFF9800),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // ADD ANCHOR button
                            OutlinedButton(
                                onClick = { showAnchorDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFFF9800)
                                )
                            ) {
                                Text(
                                    text = "ADD ANCHOR (for incremental mapping)",
                                    fontSize = 12.sp
                                )
                            }

                            // Cancel and Finish row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onResetRecording,
                                    modifier = Modifier.weight(1f).height(56.dp)
                                ) {
                                    Text(text = "CANCEL", fontSize = 14.sp)
                                }

                                Button(
                                    onClick = { onStopRecording(isNearStart) },
                                    modifier = Modifier.weight(2f).height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isNearStart) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                    )
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (isNearStart) "FINISH & CLOSE" else "FINISH EARLY",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (!isNearStart) {
                                            Text(
                                                text = "(path won't be closed)",
                                                fontSize = 9.sp,
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

        // Feature picker dialog
        if (captureState.showFeaturePicker) {
            FeaturePickerDialog(
                onDismiss = onHideFeaturePicker,
                onSelectFeature = { type ->
                    onAddFeature(type, null)
                }
            )
        }

        // First-time tutorial overlay
        if (showTutorial) {
            FirstTimeOverlay(
                onDismiss = {
                    markTutorialSeen(context)
                    showTutorial = false
                }
            )
        }

        // Anchor label input dialog
        if (showAnchorDialog) {
            AnchorLabelDialog(
                label = anchorLabel,
                onLabelChange = { anchorLabel = it },
                onConfirm = {
                    showAnchorDialog = false
                    onStartAnchorPlacement(anchorLabel)
                },
                onDismiss = { showAnchorDialog = false }
            )
        }

        // Anchor placement overlay (shown when in positioning/capturing state)
        if (captureState.anchorPlacementState != AnchorPlacementState.NONE) {
            AnchorPlacementOverlay(
                state = captureState.anchorPlacementState,
                label = captureState.pendingAnchorLabel ?: "Anchor",
                onConfirmPosition = onConfirmAnchorPosition,
                onTakePhoto = onTakeAnchorPhoto,
                onCancel = onCancelAnchorPlacement,
                onDone = onFinishAnchorPlacement
            )
        }
    }
}

@Composable
private fun FeaturePickerDialog(
    onDismiss: () -> Unit,
    onSelectFeature: (FeatureType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Feature",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "What would you like to mark at this location?",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    items(FeatureType.entries.toList()) { type ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectFeature(type) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = type.icon,
                                    fontSize = 32.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = type.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CollapsibleInstructionsPanel(
    recordingState: RecordingState,
    distanceTraveled: Float,
    distanceToStart: Float?,
    cornerCount: Int,
    featureCount: Int,
    isNearStart: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(12.dp)
            .width(if (expanded) 180.dp else 140.dp)
    ) {
        // Header - always visible
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            val (statusText, statusColor) = when (recordingState) {
                RecordingState.IDLE -> "READY" to Color.Yellow
                RecordingState.RECORDING -> {
                    when {
                        isNearStart && distanceTraveled > 2f -> "FINISH!" to Color.Green
                        distanceTraveled < 0.5f -> "REC" to Color.Red
                        else -> "MAPPING" to Color.Cyan
                    }
                }
                RecordingState.COMPLETED -> "DONE" to Color.Green
            }
            Text(
                text = statusText,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = if (expanded) "▲" else "▼",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        // Collapsed summary - key stats
        if (!expanded) {
            if (recordingState == RecordingState.RECORDING) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📍 $cornerCount corners",
                    color = Color.White,
                    fontSize = 11.sp
                )
                distanceToStart?.let { dist ->
                    Text(
                        text = "→ %.1fm to start".format(dist),
                        color = if (dist < 1.5f) Color.Yellow else Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Expanded content
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))

            when (recordingState) {
                RecordingState.IDLE -> {
                    Text(
                        text = "Hold phone upright, point at floor",
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Stand in corner\n2. Tap START\n3. Walk edges\n4. Return to start",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
                RecordingState.RECORDING -> {
                    when {
                        distanceTraveled < 0.5f -> {
                            Text(
                                text = "Walk to first corner, then tap MARK WALL CORNER",
                                color = Color.White,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        isNearStart && distanceTraveled > 2f -> {
                            Text(
                                text = "You're back! Tap FINISH below",
                                color = Color.Green,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (featureCount == 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "TIP: Mark dampers first!",
                                    color = Color(0xFF9C27B0),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        cornerCount == 0 && distanceTraveled > 1.5f -> {
                            Text(
                                text = "Don't forget to mark corners!",
                                color = Color.Yellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        else -> {
                            Text(
                                text = "Walk walls, mark corners",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "📍 $cornerCount corners",
                                color = Color(0xFF2196F3),
                                fontSize = 11.sp
                            )
                            if (featureCount > 0) {
                                Text(
                                    text = "🌀 $featureCount features",
                                    color = Color(0xFF9C27B0),
                                    fontSize = 11.sp
                                )
                            }
                            distanceToStart?.let { dist ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "→ %.1fm to start".format(dist),
                                    color = if (dist < 1.5f) Color.Yellow else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = if (dist < 1.5f) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                RecordingState.COMPLETED -> {
                    Text(
                        text = "Map complete!",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Tap SAVE or TRY AGAIN",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AnchorLabelDialog(
    label: String,
    onLabelChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Anchor Point",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Anchors let you continue mapping later from this position. Walk to a doorway or notable feature.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                TextField(
                    value = label,
                    onValueChange = onLabelChange,
                    label = { Text("Anchor Label") },
                    placeholder = { Text("e.g., Doorway to Kitchen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Start Placement")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AnchorPlacementOverlay(
    state: AnchorPlacementState,
    label: String,
    onConfirmPosition: () -> Unit,
    onTakePhoto: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status indicator
            val (statusIcon, statusText, statusColor) = when (state) {
                AnchorPlacementState.POSITIONING -> Triple("", "Position yourself at the anchor location", Color.Yellow)
                AnchorPlacementState.CAPTURING -> Triple("", "Take a reference photo", Color.Cyan)
                AnchorPlacementState.CONFIRMED -> Triple("", "Anchor saved!", Color.Green)
                AnchorPlacementState.NONE -> Triple("", "", Color.Gray)
            }

            Text(
                text = statusIcon,
                fontSize = 48.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Anchor: $label",
                color = Color(0xFFFF9800),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = statusText,
                color = statusColor,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            when (state) {
                AnchorPlacementState.POSITIONING -> {
                    Text(
                        text = "Stand at a doorway or connection point.\nThis will help you continue mapping later.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color.White)
                        }

                        Button(
                            onClick = onConfirmPosition,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9800)
                            )
                        ) {
                            Text("I'm Here")
                        }
                    }
                }

                AnchorPlacementState.CAPTURING -> {
                    Text(
                        text = "Take a photo looking into the connected room.\nThis helps you find this spot again.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color.White)
                        }

                        Button(
                            onClick = onTakePhoto,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text("Take Photo")
                        }
                    }
                }

                AnchorPlacementState.CONFIRMED -> {
                    Text(
                        text = "Anchor saved successfully!\nYou can continue mapping or add more anchors.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Continue Mapping")
                    }
                }

                AnchorPlacementState.NONE -> { /* Not shown */ }
            }
        }
    }
}

/**
 * Data class to hold AR markers that need to be rendered
 */
data class ArMarkerData(
    val startPosition: Vector3? = null,
    val cornerPositions: List<Vector3> = emptyList(),
    val featurePositions: List<Pair<Vector3, Int>> = emptyList()  // Position and color
)

@Composable
private fun ArCameraView(
    isRecording: Boolean,
    markers: ArMarkerData,
    onFrameUpdate: (Frame, Session, Boolean) -> Unit,
    onPositionUpdate: (Vector3) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Thread-safe marker holder
    val markerHolder = remember { ArMarkerHolder() }

    // Update markers when they change
    LaunchedEffect(markers) {
        markerHolder.update(markers)
    }

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
                val markerRenderer = com.example.damperlocator.ar.MarkerRenderer()
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
                        markerRenderer.createOnGlThread()

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

                                // Get view and projection matrices for marker rendering
                                val viewMatrix = FloatArray(16)
                                val projMatrix = FloatArray(16)
                                camera.getViewMatrix(viewMatrix, 0)
                                camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f)

                                // Render AR markers
                                val currentMarkers = markerHolder.get()

                                // Draw start marker (green)
                                currentMarkers.startPosition?.let { startPos ->
                                    markerRenderer.drawMarker(
                                        startPos,
                                        com.example.damperlocator.ar.MarkerRenderer.COLOR_START,
                                        viewMatrix,
                                        projMatrix,
                                        0.15f
                                    )
                                }

                                // Draw corner markers (orange)
                                currentMarkers.cornerPositions.forEach { cornerPos ->
                                    markerRenderer.drawMarker(
                                        cornerPos,
                                        com.example.damperlocator.ar.MarkerRenderer.COLOR_CORNER,
                                        viewMatrix,
                                        projMatrix,
                                        0.12f
                                    )
                                }

                                // Draw feature markers (various colors)
                                currentMarkers.featurePositions.forEach { (pos, color) ->
                                    markerRenderer.drawMarker(
                                        pos,
                                        color,
                                        viewMatrix,
                                        projMatrix,
                                        0.1f
                                    )
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

/**
 * Thread-safe holder for AR markers
 */
private class ArMarkerHolder {
    @Volatile
    private var markers: ArMarkerData = ArMarkerData()

    fun update(newMarkers: ArMarkerData) {
        markers = newMarkers
    }

    fun get(): ArMarkerData = markers
}
