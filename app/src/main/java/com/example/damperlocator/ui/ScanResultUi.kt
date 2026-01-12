package com.example.damperlocator.ui

data class ScanResultUi(
    val address: String,
    val name: String?,
    val label: String?,
    val photoPath: String?,
    val averageRssi: Int,
    val lastSeenMs: Long
)
