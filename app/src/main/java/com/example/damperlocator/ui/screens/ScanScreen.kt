package com.example.damperlocator.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.damperlocator.ble.DamperDevice

@Composable
fun ScanScreen(
    results: List<DamperDevice>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelect: (DamperDevice) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Damper Locator")
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            Button(onClick = onStartScan) {
                Text(text = "Start scan")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onStopScan) {
                Text(text = "Stop scan")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Found: ${results.size}")
        results.firstOrNull()?.let { device ->
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onSelect(device) }) {
                Text(text = device.name ?: device.address)
            }
        }
    }
}
