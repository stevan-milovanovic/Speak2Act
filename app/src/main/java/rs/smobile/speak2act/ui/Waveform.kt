package rs.smobile.speak2act.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rs.smobile.speak2act.ui.theme.Speak2ActTheme

/**
 * Maximum amplitude value for 16-bit PCM audio
 * Most Android microphone recordings are PCM 16-bit
 * A signed 16-bit integer ranges from -32768 to 32767
 */
private const val MAX_AMPLITUDE = 32768f

@Composable
fun Waveform(
    amplitudes: SnapshotStateList<Int>,
    recordingSession: Int
) {
    val contentColor = MaterialTheme.colorScheme.onPrimary
    val animatables = remember(recordingSession) {
        mutableStateListOf<Animatable<Float, AnimationVector1D>>()
    }
    LaunchedEffect(recordingSession) {
        snapshotFlow { amplitudes.size }
            .collect { size ->
                while (animatables.size < size) {
                    val index = animatables.size
                    val normalized = (amplitudes[index] / MAX_AMPLITUDE).coerceIn(0f, 1f)
                    val anim = Animatable(0f)
                    animatables.add(anim)

                    launch {
                        delay(index * 30L)
                        anim.animateTo(
                            targetValue = normalized,
                            animationSpec = tween()
                        )
                        anim.animateTo(
                            targetValue = normalized * 0.95f,
                            animationSpec = tween(
                                durationMillis = 140,
                                easing = LinearOutSlowInEasing
                            )
                        )
                    }
                }
            }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        val lineWidth = 8.dp.toPx()
        val spacing = 4.dp.toPx()
        val maxHeight = size.height

        val totalWidth = animatables.size * (lineWidth + spacing)
        val startX = (size.width - totalWidth) / 2f

        animatables.forEachIndexed { index, anim ->
            val height = anim.value * maxHeight
            val x = startX + index * (lineWidth + spacing)
            drawLine(
                color = contentColor,
                start = Offset(x, maxHeight),
                end = Offset(x, maxHeight - height),
                strokeWidth = lineWidth
            )
        }
    }
}

@Preview
@Composable
fun WaveformAnimationPreview() {
    val amplitudes = remember { mutableStateListOf<Int>() }
    val maxSamples = 30        // total values
    val durationMs = 15_000L   // 15 seconds
    val intervalMs = durationMs / maxSamples

    LaunchedEffect(Unit) {
        repeat(maxSamples) {
            val newAmplitude = (Math.random() * MAX_AMPLITUDE).toInt()
            amplitudes.add(newAmplitude)

            if (amplitudes.size > maxSamples) {
                amplitudes.removeAt(0)
            }

            delay(intervalMs)
        }
    }

    Speak2ActTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.secondary),
            verticalArrangement = Arrangement.Center
        ) {
            Waveform(amplitudes = amplitudes, recordingSession = 1)
        }
    }
}
