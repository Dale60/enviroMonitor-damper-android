package com.example.damperlocator

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.location.LocationManager
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import android.bluetooth.BluetoothManager
import com.example.damperlocator.ui.MainViewModel
import com.example.damperlocator.ui.Screen
import com.example.damperlocator.ui.screens.ScanScreen
import com.example.damperlocator.ui.screens.IdentifyScreen
import com.example.damperlocator.ui.theme.DamperLocatorTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DamperLocatorTheme {
                val screen by vm.screen.collectAsState()
                val results by vm.scanResults.collectAsState()
                val isScanning by vm.isScanning.collectAsState()
                val filterMode by vm.filterMode.collectAsState()
                val sortMode by vm.sortMode.collectAsState()
                val labels by vm.labels.collectAsState()
                val photos by vm.photos.collectAsState()
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current

                val scanPermissions = remember {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    } else {
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }

                val connectPermissions = remember {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        emptyArray()
                    }
                }

                val requestPermissions = remember {
                    scanPermissions + connectPermissions
                }

                var hasScanPermissions by remember {
                    mutableStateOf(hasAllPermissions(context, scanPermissions))
                }

                var hasConnectPermission by remember {
                    mutableStateOf(hasAllPermissions(context, connectPermissions))
                }

                val requiresLocation = remember {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                }

                var isLocationReady by remember {
                    mutableStateOf(isLocationEnabled(context))
                }

                var isBluetoothEnabled by remember {
                    mutableStateOf(isBluetoothEnabled(context))
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { _ ->
                    hasScanPermissions = hasAllPermissions(context, scanPermissions)
                    hasConnectPermission = hasAllPermissions(context, connectPermissions)
                }

                var pendingPhotoAddress by remember { mutableStateOf<String?>(null) }
                var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
                var photoError by remember { mutableStateOf<String?>(null) }

                val takePhotoLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.TakePicture()
                ) { success ->
                    val address = pendingPhotoAddress
                    val path = pendingPhotoPath
                    if (success && address != null && path != null) {
                        vm.setPhoto(address, path)
                    }
                    pendingPhotoAddress = null
                    pendingPhotoPath = null
                }

                val pickPhotoLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri ->
                    val address = pendingPhotoAddress
                    if (uri != null && address != null) {
                        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            val file = createPhotoFile(context, address)
                            if (copyUriToFile(context, uri, file)) {
                                vm.setPhoto(address, file.absolutePath)
                            } else {
                                withContext(Dispatchers.Main) {
                                    photoError = "Unable to import photo."
                                }
                            }
                        }
                    }
                    pendingPhotoAddress = null
                }

                val exportLabelsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri != null) {
                        val json = vm.exportLabelsJson()
                        writeTextToUri(context, uri, json)
                    }
                }

                val importLabelsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        val text = readTextFromUri(context, uri)
                        if (text != null) {
                            vm.importLabelsJson(text)
                        }
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasScanPermissions = hasAllPermissions(context, scanPermissions)
                            hasConnectPermission = hasAllPermissions(context, connectPermissions)
                            isLocationReady = isLocationEnabled(context)
                            isBluetoothEnabled = isBluetoothEnabled(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(screen, hasScanPermissions, isLocationReady, isBluetoothEnabled) {
                    if (
                        screen is Screen.Scan &&
                        hasScanPermissions &&
                        (isLocationReady || !requiresLocation) &&
                        isBluetoothEnabled
                    ) {
                        vm.startScanning()
                    } else {
                        vm.stopScanning()
                    }
                }

                when (val s = screen) {
                    is Screen.Scan -> ScanScreen(
                        isScanning = isScanning,
                        hasPermissions = hasScanPermissions,
                        isBluetoothEnabled = isBluetoothEnabled,
                        isLocationEnabled = isLocationReady,
                        requiresLocation = requiresLocation,
                        filterMode = filterMode,
                        sortMode = sortMode,
                        results = results,
                        onRequestPermissions = {
                            permissionLauncher.launch(requestPermissions)
                        },
                        onFilterChange = { vm.setFilterMode(it) },
                        onSortChange = { vm.setSortMode(it) },
                        onExportLabels = {
                            exportLabelsLauncher.launch("damper-labels.json")
                        },
                        onImportLabels = {
                            importLabelsLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                        onSelect = { vm.selectDevice(it) }
                    )
                    is Screen.Identify -> IdentifyScreen(
                        device = s.device.copy(
                            label = labels[s.device.address],
                            photoPath = photos[s.device.address]
                        ),
                        canIdentify = hasConnectPermission,
                        onSaveLabel = { vm.setLabel(s.device.address, it) },
                        onIdentify = { vm.identify(s.device) },
                        onTakePhoto = {
                            photoError = null
                            try {
                                val file = createPhotoFile(context, s.device.address)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                pendingPhotoAddress = s.device.address
                                pendingPhotoPath = file.absolutePath
                                takePhotoLauncher.launch(uri)
                            } catch (_: Exception) {
                                photoError = "Unable to open camera."
                            }
                        },
                        onPickPhoto = {
                            pendingPhotoAddress = s.device.address
                            pickPhotoLauncher.launch("image/*")
                        },
                        onClearPhoto = { vm.setPhoto(s.device.address, null) },
                        photoError = photoError,
                        onRequestPermissions = {
                            permissionLauncher.launch(requestPermissions)
                        },
                        onBack = { vm.backToScan() }
                    )
                }
            }
        }
    }

    private fun hasAllPermissions(context: android.content.Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun createPhotoFile(context: android.content.Context, address: String): File {
        val dir = File(context.filesDir, "device_photos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val safe = address.replace(":", "").lowercase()
        return File(dir, "device_$safe.jpg")
    }

    private fun copyUriToFile(context: android.content.Context, uri: Uri, dest: File): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use { stream ->
                FileOutputStream(dest).use { output ->
                    stream.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writeTextToUri(context: android.content.Context, uri: Uri, text: String) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(text.toByteArray())
            }
        } catch (_: Exception) {
        }
    }

    private fun readTextFromUri(context: android.content.Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isBluetoothEnabled(context: android.content.Context): Boolean {
        val manager = context.getSystemService(BluetoothManager::class.java)
        return manager?.adapter?.isEnabled == true
    }

    private fun isLocationEnabled(context: android.content.Context): Boolean {
        val manager = context.getSystemService(LocationManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager?.isLocationEnabled == true
        } else {
            val gpsEnabled = manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            val networkEnabled = manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            gpsEnabled || networkEnabled
        }
    }
}
