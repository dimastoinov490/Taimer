package com.example.timer

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticAudioFeedback(context: Context) {
    private val appContext = context.applicationContext

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private var toneGenerator: ToneGenerator? = null

    private fun getOrCreateToneGenerator(): ToneGenerator? {
        if (toneGenerator == null) {
            toneGenerator = runCatching {
                ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            }.getOrNull()
        }
        return toneGenerator
    }

    fun triggerVibration(durationMs: Long = 28L, intensityLevel: Int = 5) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val amplitude = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && v.hasAmplitudeControl()) {
            (75 + (intensityLevel.coerceIn(1, 10) * 18)).coerceIn(1, 255)
        } else {
            VibrationEffect.DEFAULT_AMPLITUDE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }

    fun playTone(toneType: Int = ToneGenerator.TONE_PROP_BEEP, durationMs: Int = 90) {
        runCatching {
            getOrCreateToneGenerator()?.startTone(toneType, durationMs)
        }
    }

    fun release() {
        runCatching {
            toneGenerator?.release()
        }
        toneGenerator = null
    }
}
