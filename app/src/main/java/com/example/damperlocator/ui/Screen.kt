package com.example.damperlocator.ui

import com.example.damperlocator.ble.DamperDevice

sealed class Screen {
    object Scan : Screen()
    data class Identify(val device: DamperDevice) : Screen()
}
