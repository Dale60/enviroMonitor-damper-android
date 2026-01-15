package com.example.damperlocator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.damperlocator.ui.FilterMode
import com.example.damperlocator.ui.SortMode
import com.example.damperlocator.ui.ScanResultUi

@Composable
fun ScanScreen(
    isScanning: Boolean,
    hasPermissions: Boolean,
    isBluetoothEnabled: Boolean,
    isLocationEnabled: Boolean,
    requiresLocation: Boolean,
    filterMode: FilterMode,
    sortMode: SortMode,
    bestCandidate: ScanResultUi?,
    results: List<ScanResultUi>,
    onRequestPermissions: () -> Unit,
    onFilterChange: (FilterMode) -> Unit,
    onSortChange: (SortMode) -> Unit,
    onExportLabels: () -> Unit,
    onImportLabels: () -> Unit,
    onMapFloor: () -> Unit,
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

        if (!isBluetoothEnabled) {
            Text(text = "Bluetooth is off. Turn it on to scan.")
            return
        }

        if (!isLocationEnabled && requiresLocation) {
            Text(text = "Location services are off. Enable location to scan.")
            return
        }

        if (!isLocationEnabled && !requiresLocation) {
            Text(text = "Location services are off. Some devices may not scan.")
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isScanning) {
            Text(text = "Scanning...")
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(text = "Filter")
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterButton(
                label = "All",
                selected = filterMode == FilterMode.ALL,
                onClick = { onFilterChange(FilterMode.ALL) }
            )
            FilterButton(
                label = "DAMP",
                selected = filterMode == FilterMode.BEACONS,
                onClick = { onFilterChange(FilterMode.BEACONS) }
            )
            FilterButton(
                label = "Nordic",
                selected = filterMode == FilterMode.NORDIC,
                onClick = { onFilterChange(FilterMode.NORDIC) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Sort")
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterButton(
                label = "Signal",
                selected = sortMode == SortMode.SIGNAL,
                onClick = { onSortChange(SortMode.SIGNAL) }
            )
            FilterButton(
                label = "Label",
                selected = sortMode == SortMode.LABEL,
                onClick = { onSortChange(SortMode.LABEL) }
            )
            FilterButton(
                label = "Address",
                selected = sortMode == SortMode.ADDRESS,
                onClick = { onSortChange(SortMode.ADDRESS) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Labels")
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onExportLabels) {
                Text(text = "Export")
            }
            OutlinedButton(onClick = onImportLabels) {
                Text(text = "Import")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onMapFloor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Map Floor")
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (bestCandidate != null) {
            val label = bestCandidate.label ?: bestCandidate.name ?: bestCandidate.address
            val bars = rssiBars(bestCandidate.averageRssi)
            Text(text = "Best signal (smoothed)")
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = label)
            Text(text = bestCandidate.address, fontFamily = FontFamily.Monospace)
            Text(
                text = "RSSI $bars ${bestCandidate.averageRssi} dBm",
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Text(text = "Best signal (smoothed): none yet.")
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (results.isEmpty()) {
            Text(text = "No devices detected yet.")
            return
        }

        Text(text = "Top devices")
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { device ->
                DeviceRow(device = device, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun DeviceRow(device: ScanResultUi, onSelect: (ScanResultUi) -> Unit) {
    val label = device.label ?: device.name ?: device.address
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
            Text(text = device.address, fontFamily = FontFamily.Monospace)
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

@Composable
private fun FilterButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) {
            Text(text = label)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(text = label)
        }
    }
}
