package com.example.damperlocator.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.damperlocator.ui.ScanResultUi

@Composable
fun IdentifyScreen(
    device: ScanResultUi,
    canIdentify: Boolean,
    onSaveLabel: (String) -> Unit,
    onIdentify: () -> Unit,
    onRequestPermissions: () -> Unit,
    onBack: () -> Unit
) {
    var labelText by remember(device.address, device.label) {
        mutableStateOf(device.label ?: "")
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Identify device")
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = device.label ?: device.name ?: device.address)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = device.address)
        Spacer(modifier = Modifier.height(12.dp))
        TextField(
            value = labelText,
            onValueChange = { labelText = it },
            label = { Text(text = "Label") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = { onSaveLabel(labelText) }) {
            Text(text = "Save label")
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (!canIdentify) {
            Text(text = "Bluetooth connect permission is required to identify.")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRequestPermissions) {
                Text(text = "Grant permissions")
            }
        } else {
            Button(onClick = onIdentify) {
                Text(text = "Identify")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack) {
            Text(text = "Back")
        }
    }
}
