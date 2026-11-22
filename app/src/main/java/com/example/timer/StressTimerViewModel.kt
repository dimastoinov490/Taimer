package com.example.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class StressTimerUiState(
    val totalSeconds: Int = DEFAULT_DURATION_SECONDS,
    val isRunning: Boolean = false,
    val intensity: Int = DEFAULT_INTENSITY,
    val isSoundEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val pulseTick: Int = 0
) {
    val formattedTime: String
        get() {
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }

    companion object {
        const val DEFAULT_DURATION_SECONDS = 4 * 60
        const val PRESET_FOUR_MINUTES_SECONDS = 4 * 60
        const val PRESET_SIX_MINUTES_SECONDS = 6 * 60
        const val MIN_DURATION_SECONDS = 60
        const val MAX_DURATION_SECONDS = 60 * 60
        const val MIN_INTENSITY = 1
        const val MAX_INTENSITY = 10
        const val DEFAULT_INTENSITY = 5
    }
}

sealed interface TimerEffect {
    data class Vibrate(val durationMs: Long, val intensity: Int) : TimerEffect
    data class PlayTone(val isCompletion: Boolean = false) : TimerEffect
    data object SessionCompleted : TimerEffect
    data object TimerReset : TimerEffect
}

class StressTimerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(StressTimerUiState())
    val uiState: StateFlow<StressTimerUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TimerEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<TimerEffect> = _effects.asSharedFlow()

    private var countdownJob: Job? = null
    private var pulseJob: Job? = null

    fun togglePlayPause() {
        val current = _uiState.value
        if (current.isRunning) {
            pauseTimer()
            emitTapFeedback(durationMs = 35L)
        } else {
            if (current.totalSeconds <= 0) {
                _uiState.update { it.copy(totalSeconds = StressTimerUiState.DEFAULT_DURATION_SECONDS) }
            }
            startTimer()
            emitTapFeedback(durationMs = 40L)
        }
    }

    fun resetTimer() {
        stopJobs()
        _uiState.update {
            it.copy(
                totalSeconds = StressTimerUiState.DEFAULT_DURATION_SECONDS,
                isRunning = false
            )
        }
        if (_uiState.value.isVibrationEnabled) {
            _effects.tryEmit(TimerEffect.Vibrate(durationMs = 30L, intensity = _uiState.value.intensity))
        }
        _effects.tryEmit(TimerEffect.TimerReset)
    }

    fun adjustMinutes(deltaMinutes: Int) {
        _uiState.update { state ->
            val updated = (state.totalSeconds + deltaMinutes * 60)
                .coerceIn(
                    StressTimerUiState.MIN_DURATION_SECONDS,
                    StressTimerUiState.MAX_DURATION_SECONDS
                )
            state.copy(totalSeconds = updated)
        }
        emitTapFeedback(durationMs = 25L)
    }

    fun setPresetMinutes(minutes: Int) {
        val targetSeconds = (minutes * 60).coerceIn(
            StressTimerUiState.MIN_DURATION_SECONDS,
            StressTimerUiState.MAX_DURATION_SECONDS
        )
        _uiState.update { it.copy(totalSeconds = targetSeconds) }
        emitTapFeedback(durationMs = 25L)
    }

    fun setIntensity(newIntensity: Int, playAudio: Boolean = false) {
        val clamped = newIntensity.coerceIn(
            StressTimerUiState.MIN_INTENSITY,
            StressTimerUiState.MAX_INTENSITY
        )
        if (clamped == _uiState.value.intensity) return

        _uiState.update { it.copy(intensity = clamped) }
        if (_uiState.value.isVibrationEnabled) {
            _effects.tryEmit(TimerEffect.Vibrate(durationMs = 18L, intensity = clamped))
        }
        if (playAudio && _uiState.value.isSoundEnabled) {
            _effects.tryEmit(TimerEffect.PlayTone(isCompletion = false))
        }
        if (_uiState.value.isRunning) {
            restartPulseLoop()
        }
    }

    fun stepIntensity(delta: Int) {
        setIntensity(_uiState.value.intensity + delta, playAudio = true)
    }

    fun toggleSound() {
        val next = !_uiState.value.isSoundEnabled
        _uiState.update { it.copy(isSoundEnabled = next) }
        if (_uiState.value.isVibrationEnabled) {
            _effects.tryEmit(TimerEffect.Vibrate(durationMs = 25L, intensity = _uiState.value.intensity))
        }
        if (next) {
            _effects.tryEmit(TimerEffect.PlayTone(isCompletion = false))
        }
    }

    fun toggleVibration() {
        val next = !_uiState.value.isVibrationEnabled
        _uiState.update { it.copy(isVibrationEnabled = next) }
        if (next) {
            _effects.tryEmit(TimerEffect.Vibrate(durationMs = 40L, intensity = _uiState.value.intensity))
        }
    }

    private fun startTimer() {
        _uiState.update { it.copy(isRunning = true) }
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRunning) {
                delay(1000L)
                if (!_uiState.value.isRunning) break
                val nextSeconds = (_uiState.value.totalSeconds - 1).coerceAtLeast(0)
                if (nextSeconds == 0) {
                    _uiState.update { it.copy(totalSeconds = 0, isRunning = false) }
                    stopPulseJob()
                    if (_uiState.value.isVibrationEnabled) {
                        _effects.tryEmit(TimerEffect.Vibrate(durationMs = 400L, intensity = 10))
                    }
                    if (_uiState.value.isSoundEnabled) {
                        _effects.tryEmit(TimerEffect.PlayTone(isCompletion = true))
                    }
                    _effects.tryEmit(TimerEffect.SessionCompleted)
                    break
                } else {
                    _uiState.update { it.copy(totalSeconds = nextSeconds) }
                }
            }
        }
        restartPulseLoop()
    }

    private fun pauseTimer() {
        stopJobs()
        _uiState.update { it.copy(isRunning = false) }
    }

    private fun restartPulseLoop() {
        pulseJob?.cancel()
        pulseJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRunning) {
                val currentIntensity = _uiState.value.intensity
                delay(pulseIntervalMs(currentIntensity))
                if (!_uiState.value.isRunning) break
                _uiState.update { it.copy(pulseTick = it.pulseTick + 1) }
                if (_uiState.value.isVibrationEnabled) {
                    val pulseDuration = 18L + (currentIntensity * 2L)
                    _effects.tryEmit(TimerEffect.Vibrate(durationMs = pulseDuration, intensity = currentIntensity))
                }
            }
        }
    }

    private fun stopJobs() {
        countdownJob?.cancel()
        countdownJob = null
        stopPulseJob()
    }

    private fun stopPulseJob() {
        pulseJob?.cancel()
        pulseJob = null
    }

    private fun emitTapFeedback(durationMs: Long) {
        val state = _uiState.value
        if (state.isVibrationEnabled) {
            _effects.tryEmit(TimerEffect.Vibrate(durationMs = durationMs, intensity = state.intensity))
        }
        if (state.isSoundEnabled) {
            _effects.tryEmit(TimerEffect.PlayTone(isCompletion = false))
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopJobs()
    }

    companion object {
        fun pulseIntervalMs(intensity: Int): Long {
            val clamped = intensity.coerceIn(
                StressTimerUiState.MIN_INTENSITY,
                StressTimerUiState.MAX_INTENSITY
            )
            // Maps intensity 1..10 smoothly from slow 3000ms pulses to brisk 450ms pulses
            return when (clamped) {
                1 -> 3000L
                2 -> 2600L
                3 -> 2250L
                4 -> 1900L
                5 -> 1600L
                6 -> 1300L
                7 -> 1050L
                8 -> 850L
                9 -> 650L
                else -> 450L
            }
        }
    }
}
