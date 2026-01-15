package com.example.damperlocator.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CompassManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading.asStateFlow()

    private val _pitch = MutableStateFlow(0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    fun start() {
        val hasAccel = accelerometer != null
        val hasMag = magnetometer != null
        _isAvailable.value = hasAccel && hasMag

        if (_isAvailable.value) {
            sensorManager.registerListener(
                this, accelerometer, SensorManager.SENSOR_DELAY_UI
            )
            sensorManager.registerListener(
                this, magnetometer, SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lowPassFilter(event.values, gravity)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lowPassFilter(event.values, geomagnetic)
            }
        }

        if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
            SensorManager.getOrientation(rotationMatrix, orientation)

            // Azimuth: rotation around Z axis (compass heading)
            val azimuthRadians = orientation[0]
            var azimuthDegrees = Math.toDegrees(azimuthRadians.toDouble()).toFloat()
            if (azimuthDegrees < 0) azimuthDegrees += 360f
            _heading.value = azimuthDegrees

            // Pitch: rotation around X axis (forward/backward tilt)
            _pitch.value = Math.toDegrees(orientation[1].toDouble()).toFloat()

            // Roll: rotation around Y axis (left/right tilt)
            _roll.value = Math.toDegrees(orientation[2].toDouble()).toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Could warn user if magnetometer accuracy is low
    }

    private fun lowPassFilter(input: FloatArray, output: FloatArray) {
        val alpha = 0.25f
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
    }
}
