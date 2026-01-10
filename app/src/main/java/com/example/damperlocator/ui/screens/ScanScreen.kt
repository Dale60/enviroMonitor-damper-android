package com.example.damperlocator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.damperlocator.ui.ScanResultUi

@Composable
fun ScanScreen(
    isScanning: Boolean,
    hasPermissions: Boolean,
    results: List<ScanResultUi>,
    onRequestPermissions: () -> Unit,
    onSelect: (ScanResultUi) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Damper Locator")
        Spacer(modifier = Modifier.height(12.dp))

        if (!hasPermissions) {
            Text(text = "Bluetooth permissions are required to scan for beacons.")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRequestPermissions) {
                Text(text = "Grant permissions")
            }
            return
        }

        if (isScanning) {
            Text(text = "Scanning...")
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (results.isEmpty()) {
            Text(text = "No beacons detected yet.")
            return
        }

        Text(text = "Top devices")
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { device ->
                DeviceRow(device = device, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun DeviceRow(device: ScanResultUi, onSelect: (ScanResultUi) -> Unit) {
    val label = device.name ?: device.address
    val bars = rssiBars(device.averageRssi)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(device) }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label)
            Text(
                text = "RSSI $bars ${device.averageRssi} dBm",
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun rssiBars(rssi: Int): String {
    val clamped = rssi.coerceIn(-100, -40)
    val level = ((clamped + 100) / 12).coerceIn(0, 5)
    return "|".repeat(level).padEnd(5, ' ')
}
