package com.github.pelmenstar1.rangecalendar.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

internal class VibratorCompat(private val context: Context) {
    private var isVibratorInitialized = false
    private var vibrator: Vibrator? = null

    private var tickEffect: VibrationEffect? = null

    fun vibrateTick() {
        getOrExtractVibrator()?.let { v ->
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(getOrCreateTickEffect())
            } else {
                // The only available API on older versions.
                @Suppress("DEPRECATION")
                v.vibrate(FALLBACK_TICK_MS)
            }
        }
    }

    private fun getOrExtractVibrator(): Vibrator? {
        var value = vibrator

        if (!isVibratorInitialized) {
            value = extractVibrator()

            vibrator = value
            isVibratorInitialized = true
        }

        return value
    }

    @RequiresApi(26)
    private fun getOrCreateTickEffect(): VibrationEffect {
        return getLazyValue(tickEffect, ::createTickEffect) { tickEffect = it }
    }

    private fun extractVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= 31) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager?

            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        }
    }

    @RequiresApi(26)
    private fun createTickEffect(): VibrationEffect {
        return VibrationEffect.createOneShot(FALLBACK_TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    companion object {
        private const val FALLBACK_TICK_MS = 50L
    }
}