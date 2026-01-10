package com.example.damperlocator

import android.Manifest
import android.os.Build
import android.os.Bundle
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
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current

                val requiredPermissions = remember {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                    } else {
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }

                var hasPermissions by remember {
                    mutableStateOf(hasAllPermissions(context, requiredPermissions))
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { resultsMap ->
                    hasPermissions = resultsMap.values.all { it }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasPermissions = hasAllPermissions(context, requiredPermissions)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(screen, hasPermissions) {
                    if (screen is Screen.Scan && hasPermissions) {
                        vm.startScanning()
                    } else {
                        vm.stopScanning()
                    }
                }

                when (val s = screen) {
                    is Screen.Scan -> ScanScreen(
                        isScanning = isScanning,
                        hasPermissions = hasPermissions,
                        results = results,
                        onRequestPermissions = {
                            permissionLauncher.launch(requiredPermissions)
                        },
                        onSelect = { vm.selectDevice(it) }
                    )
                    is Screen.Identify -> IdentifyScreen(
                        device = s.device,
                        onIdentify = { vm.identify(s.device) },
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
}
