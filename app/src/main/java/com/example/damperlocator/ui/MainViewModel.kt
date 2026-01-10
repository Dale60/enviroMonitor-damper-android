package com.example.damperlocator.ui

import androidx.lifecycle.ViewModel
import com.example.damperlocator.ble.DamperDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Scan)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _scanResults = MutableStateFlow<List<DamperDevice>>(emptyList())
    val scanResults: StateFlow<List<DamperDevice>> = _scanResults.asStateFlow()

    fun startScanning() {
    }

    fun stopScanning() {
    }

    fun selectDevice(device: DamperDevice) {
        _screen.value = Screen.Identify(device)
    }

    fun identify(device: DamperDevice) {
    }

    fun backToScan() {
        _screen.value = Screen.Scan
    }
}
