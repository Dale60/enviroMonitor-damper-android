package com.example.damperlocator.ui

sealed class Screen {
    object Scan : Screen()
    data class Identify(val device: ScanResultUi) : Screen()
}
