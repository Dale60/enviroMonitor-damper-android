package com.example.damperlocator.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.damperlocator.ble.DamperDevice

@Composable
fun IdentifyScreen(
    device: DamperDevice,
    onIdentify: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Identify device")
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = device.name ?: device.address)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onIdentify) {
            Text(text = "Identify")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack) {
            Text(text = "Back")
        }
    }
}
