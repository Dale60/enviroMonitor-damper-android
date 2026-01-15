package com.example.damperlocator.ui.components

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Helper class to provide audio and haptic feedback during mapping
 */
class FeedbackHelper(private val context: Context) {

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50) // 50% volume
        } catch (_: Exception) {}

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Quick tick - called when a new path point is recorded
     */
    fun onPointRecorded() {
        // Short vibration
        vibrate(30)
        // Short beep
        playTone(ToneGenerator.TONE_PROP_BEEP, 50)
    }

    /**
     * Medium feedback - called when getting close to start (within 2m)
     */
    fun onApproachingStart() {
        vibrate(100)
        playTone(ToneGenerator.TONE_PROP_BEEP2, 100)
    }

    /**
     * Strong feedback - called when very close to start (within 0.5m)
     */
    fun onNearStart() {
        vibrate(200)
        // Two beeps
        playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
    }

    /**
     * Success feedback - called when mapping is completed
     */
    fun onMappingComplete() {
        // Pattern vibration
        vibratePattern(longArrayOf(0, 100, 100, 100, 100, 200))
        // Success tone
        playTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 300)
    }

    /**
     * Warning feedback - tracking lost
     */
    fun onTrackingLost() {
        vibrate(300)
        playTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 200)
    }

    private fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    private fun vibratePattern(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        try {
            toneGenerator?.startTone(toneType, durationMs)
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
    }
}
