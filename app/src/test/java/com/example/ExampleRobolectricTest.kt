package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.timer.StressTimerUiState
import com.example.timer.StressTimerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Stress", appName)
    }

    @Test
    fun `default timer starts at 04_00 and intensity 5`() {
        val viewModel = StressTimerViewModel()
        val state = viewModel.uiState.value
        assertEquals("04:00", state.formattedTime)
        assertEquals(240, state.totalSeconds)
        assertEquals(5, state.intensity)
        assertFalse(state.isRunning)
        assertTrue(state.isSoundEnabled)
        assertTrue(state.isVibrationEnabled)
    }

    @Test
    fun `adjustMinutes clamps between 1 and 60 minutes`() {
        val viewModel = StressTimerViewModel()
        repeat(10) { viewModel.adjustMinutes(-1) }
        assertEquals(60, viewModel.uiState.value.totalSeconds)
        assertEquals("01:00", viewModel.uiState.value.formattedTime)

        viewModel.setPresetMinutes(60)
        viewModel.adjustMinutes(1)
        assertEquals(3600, viewModel.uiState.value.totalSeconds)
        assertEquals("60:00", viewModel.uiState.value.formattedTime)
    }

    @Test
    fun `presets and reset update timer state properly`() {
        val viewModel = StressTimerViewModel()
        viewModel.setPresetMinutes(6)
        assertEquals("06:00", viewModel.uiState.value.formattedTime)

        viewModel.togglePlayPause()
        assertTrue(viewModel.uiState.value.isRunning)

        viewModel.resetTimer()
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals(StressTimerUiState.DEFAULT_DURATION_SECONDS, viewModel.uiState.value.totalSeconds)
        assertEquals("04:00", viewModel.uiState.value.formattedTime)
    }

    @Test
    fun `intensity clamps between 1 and 10 and shortens pulse interval`() {
        val viewModel = StressTimerViewModel()
        repeat(15) { viewModel.stepIntensity(-1) }
        assertEquals(1, viewModel.uiState.value.intensity)

        repeat(15) { viewModel.stepIntensity(1) }
        assertEquals(10, viewModel.uiState.value.intensity)

        assertTrue(
            StressTimerViewModel.pulseIntervalMs(10) <
                StressTimerViewModel.pulseIntervalMs(5)
        )
        assertTrue(
            StressTimerViewModel.pulseIntervalMs(5) <
                StressTimerViewModel.pulseIntervalMs(1)
        )
    }
}
