package com.example.damperlocator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

                when (val s = screen) {
                    is Screen.Scan -> ScanScreen(results, { vm.startScanning() }, { vm.stopScanning() }) {
                        vm.selectDevice(it)
                    }
                    is Screen.Identify -> IdentifyScreen(s.device, { vm.identify(s.device) }) {
                        vm.backToScan()
                    }
                }
            }
        }
    }
}
