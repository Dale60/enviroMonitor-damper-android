package com.example.damperlocator.ui

sealed class Screen {
    object Scan : Screen()
    data class Identify(val device: ScanResultUi) : Screen()

    // Floor mapping screens
    object FloorMapList : Screen()
    data class FloorMapCapture(val floorPlanId: String? = null) : Screen()
    data class FloorMapPreview(val floorPlanId: String) : Screen()
}
