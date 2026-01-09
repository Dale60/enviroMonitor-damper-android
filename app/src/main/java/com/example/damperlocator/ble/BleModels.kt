package com.example.damperlocator.ble

data class DamperDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val lastSeenMs: Long
)
