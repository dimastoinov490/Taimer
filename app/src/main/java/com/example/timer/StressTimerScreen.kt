package com.example.timer

import android.media.ToneGenerator
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.theme.StressCoralGlow
import com.example.ui.theme.StressCoralHighlight
import com.example.ui.theme.StressCrimsonShadow
import com.example.ui.theme.StressDeepEspresso
import com.example.ui.theme.StressDomeBottom
import com.example.ui.theme.StressDomeSurface
import com.example.ui.theme.StressObsidian
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun StressTimerRoute(
    viewModel: StressTimerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val feedback = remember(context) { HapticAudioFeedback(context) }

    DisposableEffect(feedback) {
        onDispose {
            feedback.release()
        }
    }

    LaunchedEffect(viewModel, feedback) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TimerEffect.Vibrate -> {
                    feedback.triggerVibration(
                        durationMs = effect.durationMs,
                        intensityLevel = effect.intensity
                    )
                }
                is TimerEffect.PlayTone -> {
                    if (effect.isCompletion) {
                        feedback.playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450)
                    } else {
                        feedback.playTone(ToneGenerator.TONE_PROP_BEEP, 80)
                    }
                }
                TimerEffect.SessionCompleted -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_session_completed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                TimerEffect.TimerReset -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_timer_reset),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    StressTimerScreen(
        uiState = uiState,
        onBackClick = {
            if (uiState.isVibrationEnabled) {
                feedback.triggerVibration(durationMs = 25L, intensityLevel = uiState.intensity)
            }
            Toast.makeText(
                context,
                context.getString(R.string.toast_back),
                Toast.LENGTH_SHORT
            ).show()
        },
        onResetClick = viewModel::resetTimer,
        onAdjustMinutes = viewModel::adjustMinutes,
        onSelectPresetMinutes = viewModel::setPresetMinutes,
        onStepIntensity = viewModel::stepIntensity,
        onToggleSound = viewModel::toggleSound,
        onTogglePlayPause = viewModel::togglePlayPause,
        onToggleVibration = viewModel::toggleVibration
    )
}

@Composable
fun StressTimerScreen(
    uiState: StressTimerUiState,
    onBackClick: () -> Unit,
    onResetClick: () -> Unit,
    onAdjustMinutes: (Int) -> Unit,
    onSelectPresetMinutes: (Int) -> Unit,
    onStepIntensity: (Int) -> Unit,
    onToggleSound: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleVibration: () -> Unit
) {
    var pulseBoostTarget by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(uiState.pulseTick, uiState.isRunning) {
        if (uiState.isRunning && uiState.pulseTick > 0) {
            pulseBoostTarget = 1f
            delay(180L)
            pulseBoostTarget = 0f
        } else {
            pulseBoostTarget = 0f
        }
    }

    val pulseBoost by animateFloatAsState(
        targetValue = pulseBoostTarget,
        animationSpec = tween(durationMillis = 260),
        label = "coralPulseGlow"
    )

    val animatedActiveTick by animateFloatAsState(
        targetValue = ((uiState.intensity - 1) / 9f) * 30f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dialTickRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StressObsidian)
            .drawBehind {
                val w = size.width
                val h = size.height
                val glowBoost = 0.10f * pulseBoost

                // Right flank luminous coral aura (strongest along upper-right & mid-right perimeter)
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to StressCoralHighlight.copy(alpha = (0.95f + glowBoost).coerceAtMost(1f)),
                        0.32f to StressCoralGlow.copy(alpha = 0.72f + glowBoost * 0.4f),
                        0.62f to StressCrimsonShadow.copy(alpha = 0.34f),
                        1.0f to Color.Transparent,
                        center = Offset(w * 1.10f, h * 0.34f),
                        radius = w * 0.72f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to StressCoralHighlight.copy(alpha = (0.92f + glowBoost).coerceAtMost(1f)),
                        0.34f to StressCoralGlow.copy(alpha = 0.68f + glowBoost * 0.4f),
                        0.66f to StressCrimsonShadow.copy(alpha = 0.30f),
                        1.0f to Color.Transparent,
                        center = Offset(w * 1.12f, h * 0.58f),
                        radius = w * 0.66f
                    )
                )

                // Left flank luminous coral aura (strongest around -1 min pill and mid-left shoulder)
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to StressCoralHighlight.copy(alpha = (0.92f + glowBoost).coerceAtMost(1f)),
                        0.32f to StressCoralGlow.copy(alpha = 0.66f + glowBoost * 0.4f),
                        0.65f to StressCrimsonShadow.copy(alpha = 0.30f),
                        1.0f to Color.Transparent,
                        center = Offset(-w * 0.10f, h * 0.48f),
                        radius = w * 0.68f
                    )
                )

                // Crisp left & right edge coral wash
                drawRect(
                    brush = Brush.horizontalGradient(
                        0.0f to StressCoralGlow.copy(alpha = 0.65f + glowBoost * 0.4f),
                        0.12f to StressCrimsonShadow.copy(alpha = 0.25f),
                        0.30f to Color.Transparent,
                        startX = 0f,
                        endX = w * 0.34f
                    )
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        0.0f to Color.Transparent,
                        0.68f to StressCrimsonShadow.copy(alpha = 0.30f),
                        0.90f to StressCoralGlow.copy(alpha = 0.72f + glowBoost * 0.4f),
                        1.0f to StressCoralHighlight.copy(alpha = 0.88f + glowBoost * 0.4f),
                        startX = w * 0.62f,
                        endX = w
                    )
                )

                // Bottom-left and bottom-right corner coral glow flanking the dark dome
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to StressCoralGlow.copy(alpha = 0.78f + glowBoost * 0.4f),
                        0.42f to StressCrimsonShadow.copy(alpha = 0.34f),
                        1.0f to Color.Transparent,
                        center = Offset(-w * 0.04f, h * 0.94f),
                        radius = w * 0.52f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to StressCoralGlow.copy(alpha = 0.78f + glowBoost * 0.4f),
                        0.42f to StressCrimsonShadow.copy(alpha = 0.34f),
                        1.0f to Color.Transparent,
                        center = Offset(w * 1.04f, h * 0.94f),
                        radius = w * 0.52f
                    )
                )

                // Top-left dark quiet corner (keeps top-left subtle while top-right & edges glow)
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to StressObsidian.copy(alpha = 0.88f),
                        0.55f to StressDeepEspresso.copy(alpha = 0.55f),
                        1.0f to Color.Transparent,
                        center = Offset(w * 0.18f, h * 0.08f),
                        radius = w * 0.44f
                    )
                )

                // Soft velvety warm-dark center pockets (#241A19) behind timer & intensity
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to StressObsidian.copy(alpha = 0.92f),
                        0.50f to StressDeepEspresso.copy(alpha = 0.76f),
                        0.82f to StressObsidian.copy(alpha = 0.30f),
                        1.0f to Color.Transparent,
                        center = Offset(w * 0.48f, h * 0.25f),
                        radius = w * 0.44f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to StressObsidian.copy(alpha = 0.92f),
                        0.50f to StressDeepEspresso.copy(alpha = 0.72f),
                        0.82f to StressObsidian.copy(alpha = 0.28f),
                        1.0f to Color.Transparent,
                        center = Offset(w * 0.50f, h * 0.59f),
                        radius = w * 0.40f
                    )
                )
            }
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 500.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White.copy(alpha = 0.90f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.title_stress),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                IconButton(
                    onClick = onResetClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_reset),
                        tint = Color.White.copy(alpha = 0.90f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Countdown display
            Text(
                text = uiState.formattedTime,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("timer_text")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Guidance copy
            Text(
                text = stringResource(R.string.stress_guidance),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.50f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 36.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // -1 min / +1 min large translucent glass pill buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TimeAdjustButton(
                    text = stringResource(R.string.minus_one_min),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("minus_one_min_button"),
                    onClick = { onAdjustMinutes(-1) }
                )
                TimeAdjustButton(
                    text = stringResource(R.string.plus_one_min),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("plus_one_min_button"),
                    onClick = { onAdjustMinutes(1) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Intensity header & numeric value
            Text(
                text = stringResource(R.string.intensity_label),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = Color.White.copy(alpha = 0.88f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = uiState.intensity.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("intensity_value_text")
            )

            Spacer(modifier = Modifier.height(5.dp))

            // Crisp white downward-pointing triangular pointer above the active scale tick
            Canvas(
                modifier = Modifier.size(width = 8.dp, height = 5.dp)
            ) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path = path, color = Color.White)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Semicircular dark dome, 31-tick intensity scale, presets 4 & 6, and bottom controls
            IntensityDomeSection(
                uiState = uiState,
                animatedActiveTick = animatedActiveTick,
                onSelectPresetMinutes = onSelectPresetMinutes,
                onStepIntensity = onStepIntensity,
                onToggleSound = onToggleSound,
                onTogglePlayPause = onTogglePlayPause,
                onToggleVibration = onToggleVibration
            )
        }
    }
}

@Composable
private fun IntensityDomeSection(
    uiState: StressTimerUiState,
    animatedActiveTick: Float,
    onSelectPresetMinutes: (Int) -> Unit,
    onStepIntensity: (Int) -> Unit,
    onToggleSound: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleVibration: () -> Unit
) {
    val fourActive = uiState.totalSeconds == StressTimerUiState.PRESET_FOUR_MINUTES_SECONDS
    val sixActive = uiState.totalSeconds == StressTimerUiState.PRESET_SIX_MINUTES_SECONDS

    val fourTint by animateColorAsState(
        targetValue = if (fourActive) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.36f),
        label = "presetFourTint"
    )
    val sixTint by animateColorAsState(
        targetValue = if (sixActive) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.36f),
        label = "presetSixTint"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("intensity_dial")
            .pointerInput(Unit) {
                var dragAccumulator = 0f
                val stepThresholdPx = 36f
                detectHorizontalDragGestures(
                    onDragEnd = { dragAccumulator = 0f },
                    onDragCancel = { dragAccumulator = 0f }
                ) { _, dragAmount ->
                    dragAccumulator += dragAmount
                    while (dragAccumulator >= stepThresholdPx) {
                        onStepIntensity(-1)
                        dragAccumulator -= stepThresholdPx
                    }
                    while (dragAccumulator <= -stepThresholdPx) {
                        onStepIntensity(1)
                        dragAccumulator += stepThresholdPx
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val centerX = size.width / 2f
            val domeRadius = min(size.width * 0.60f, 268.dp.toPx() * 1.16f)
            val centerY = domeRadius + 24.dp.toPx()

            // Warm translucent charcoal dome (#2C2422) matching screenshot shade
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        StressDomeSurface.copy(alpha = 0.82f),
                        StressDomeBottom.copy(alpha = 0.92f)
                    ),
                    startY = centerY - domeRadius,
                    endY = size.height
                ),
                radius = domeRadius,
                center = Offset(centerX, centerY)
            )

            // Subtle rim along the dome arc
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = domeRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.dp.toPx())
            )

            // 31 tick marks hugging the outer rim of the dome all the way across the arc
            val totalTicks = 31
            val ticksRadius = domeRadius + 6.dp.toPx()
            val angleStepDeg = 4.35

            for (i in 0 until totalTicks) {
                val angleOffsetDeg = (i - animatedActiveTick) * angleStepDeg
                if (abs(angleOffsetDeg) > 78.0) continue

                val angleRad = Math.toRadians(-90.0 + angleOffsetDeg)
                val distFromCenter = abs(i - animatedActiveTick)
                val isSelected = distFromCenter < 0.5f
                val isMajorTick = i % 3 == 0 || i == totalTicks - 1

                val tickLength = when {
                    isSelected -> 11.dp.toPx()
                    isMajorTick -> 8.5.dp.toPx()
                    else -> 7.dp.toPx()
                }
                val innerR = ticksRadius
                val outerR = ticksRadius + tickLength

                val startX = centerX + (innerR * cos(angleRad)).toFloat()
                val startY = centerY + (innerR * sin(angleRad)).toFloat()
                val endX = centerX + (outerR * cos(angleRad)).toFloat()
                val endY = centerY + (outerR * sin(angleRad)).toFloat()

                val edgeFade = (1f - (abs(angleOffsetDeg).toFloat() / 84f)).coerceIn(0.22f, 1f)
                val tickColor = if (isSelected) {
                    Color.White
                } else {
                    Color.White.copy(alpha = 0.62f * edgeFade)
                }

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (isSelected) 2.dp.toPx() else 1.15.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Subtle vertical center divider inside the dome
            drawLine(
                color = Color.White.copy(alpha = 0.09f),
                start = Offset(centerX, centerY - domeRadius + 16.dp.toPx()),
                end = Offset(centerX, centerY - domeRadius + 132.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(268.dp)
        ) {
        // Clickable 4 and 6 minute presets angled symmetrically along the upper flanks of the arc
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp)
                .padding(top = 26.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable { onSelectPresetMinutes(4) }
                    .testTag("preset_4_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.preset_four),
                    color = fourTint,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.graphicsLayer { rotationZ = -22f }
                )
            }

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable { onSelectPresetMinutes(6) }
                    .testTag("preset_6_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.preset_six),
                    color = sixTint,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.graphicsLayer { rotationZ = 22f }
                )
            }
        }

        // << and >> intensity step controls inside the dome
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 56.dp)
                .padding(bottom = 114.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onStepIntensity(-1) },
                enabled = uiState.intensity > StressTimerUiState.MIN_INTENSITY,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("decrease_intensity_button")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardDoubleArrowLeft,
                    contentDescription = stringResource(R.string.cd_decrease_intensity),
                    tint = Color.White.copy(
                        alpha = if (uiState.intensity > StressTimerUiState.MIN_INTENSITY) 0.90f else 0.30f
                    ),
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = { onStepIntensity(1) },
                enabled = uiState.intensity < StressTimerUiState.MAX_INTENSITY,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("increase_intensity_button")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardDoubleArrowRight,
                    contentDescription = stringResource(R.string.cd_increase_intensity),
                    tint = Color.White.copy(
                        alpha = if (uiState.intensity < StressTimerUiState.MAX_INTENSITY) 0.90f else 0.30f
                    ),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom Sound, Play/Pause, Vibration controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleControlButton(
                isActive = uiState.isSoundEnabled,
                size = 48.dp,
                modifier = Modifier.testTag("sound_toggle_button"),
                onClick = onToggleSound
            ) {
                Icon(
                    imageVector = if (uiState.isSoundEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
                    contentDescription = stringResource(R.string.cd_toggle_sound),
                    tint = Color.White.copy(alpha = if (uiState.isSoundEnabled) 0.95f else 0.45f),
                    modifier = Modifier.size(20.dp)
                )
            }

            CircleControlButton(
                isActive = uiState.isRunning,
                size = 56.dp,
                modifier = Modifier.testTag("play_pause_button"),
                onClick = onTogglePlayPause
            ) {
                if (uiState.isRunning) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    // Sleek hollow outlined play triangle matching the mockup
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val path = Path().apply {
                            moveTo(size.width * 0.22f, size.height * 0.12f)
                            lineTo(size.width * 0.88f, size.height * 0.50f)
                            lineTo(size.width * 0.22f, size.height * 0.88f)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = 0.92f),
                            style = Stroke(
                                width = 1.6.dp.toPx(),
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            CircleControlButton(
                isActive = uiState.isVibrationEnabled,
                size = 48.dp,
                modifier = Modifier.testTag("vibration_toggle_button"),
                onClick = onToggleVibration
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = stringResource(R.string.cd_toggle_vibration),
                        tint = Color.White.copy(alpha = if (uiState.isVibrationEnabled) 0.95f else 0.40f),
                        modifier = Modifier.size(20.dp)
                    )
                    if (!uiState.isVibrationEnabled) {
                        Canvas(modifier = Modifier.size(20.dp)) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.7f),
                                start = Offset(2.dp.toPx(), size.height - 2.dp.toPx()),
                                end = Offset(size.width - 2.dp.toPx(), 2.dp.toPx()),
                                strokeWidth = 1.6.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun TimeAdjustButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(90.dp)
            .clip(RoundedCornerShape(45.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 0.75.dp,
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(45.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = Color.White.copy(alpha = 0.90f)
        )
    }
}

@Composable
private fun CircleControlButton(
    isActive: Boolean,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.04f),
        label = "controlButtonContainer"
    )
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .border(
                width = 0.75.dp,
                color = Color.White.copy(alpha = if (isActive) 0.07f else 0.03f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
