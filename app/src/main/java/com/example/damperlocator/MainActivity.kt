package com.example.damperlocator

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.location.LocationManager
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
                val favorites by vm.favoriteResults.collectAsState()
                val isScanning by vm.isScanning.collectAsState()
                val filterMode by vm.filterMode.collectAsState()
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
                ) { resultsMap ->
                    hasScanPermissions = hasAllPermissions(context, scanPermissions)
                    hasConnectPermission = hasAllPermissions(context, connectPermissions)
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
                        favorites = favorites,
                        results = results,
                        onRequestPermissions = {
                            permissionLauncher.launch(requestPermissions)
                        },
                        onFilterChange = { vm.setFilterMode(it) },
                        onSelect = { vm.selectDevice(it) }
                    )
                    is Screen.Identify -> IdentifyScreen(
                        device = s.device,
                        canIdentify = hasConnectPermission,
                        onSaveLabel = { vm.setLabel(s.device.address, it) },
                        onIdentify = { vm.identify(s.device) },
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
