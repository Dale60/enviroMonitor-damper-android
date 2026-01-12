package com.example.damperlocator.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.damperlocator.ui.ScanResultUi

@Composable
fun IdentifyScreen(
    device: ScanResultUi,
    canIdentify: Boolean,
    onSaveLabel: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    photoError: String?,
    onIdentify: () -> Unit,
    onRequestPermissions: () -> Unit,
    onBack: () -> Unit
) {
    var labelText by remember(device.address, device.label) {
        mutableStateOf(device.label ?: "")
    }
    val photoBitmap = remember(device.photoPath) {
        device.photoPath?.let { BitmapFactory.decodeFile(it) }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Identify device")
        Spacer(modifier = Modifier.height(12.dp))
        val displayLabel = if (labelText.isNotBlank()) {
            labelText
        } else {
            device.name ?: device.address
        }
        Text(text = displayLabel)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = device.address)
        Spacer(modifier = Modifier.height(12.dp))
        if (photoBitmap != null) {
            Image(
                bitmap = photoBitmap.asImageBitmap(),
                contentDescription = "Device photo",
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .width(220.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onTakePhoto) {
            Text(text = "Take photo")
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(onClick = onPickPhoto) {
            Text(text = "Choose photo")
        }
        if (photoBitmap != null) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(onClick = onClearPhoto) {
                Text(text = "Remove photo")
            }
        }
        photoError?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = it)
        }
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
