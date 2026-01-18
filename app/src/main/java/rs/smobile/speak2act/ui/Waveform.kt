package rs.smobile.speak2act.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.random.Random


@Composable
fun Waveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    barWidth: Dp = 6.dp,
    barSpacing: Dp = 3.dp,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val animatedAmps = amplitudes.map { amplitude ->
        animateFloatAsState(
            targetValue = amplitude.coerceIn(0f, 1f),
            animationSpec = tween(
                durationMillis = 120,
                easing = LinearOutSlowInEasing
            ),
            label = "waveformBar"
        ).value
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.95f)
    ) {
        val centerY = size.height / 2f
        val maxBarHeight = size.height / 2f

        val barWidthPx = barWidth.toPx()
        val barSpacingPx = barSpacing.toPx()
        val barCount = max(1, (size.width / (barWidthPx + barSpacingPx)).toInt())
        val visible = animatedAmps.takeLast(barCount)

        visible.forEachIndexed { index, amp ->
            val x = index * (barWidthPx + barSpacingPx)
            if (x > size.width) return@forEachIndexed
            val barHeight = amp * maxBarHeight
            drawLine(
                color = barColor,
                start = Offset(x, centerY - barHeight),
                end = Offset(x, centerY + barHeight),
                strokeWidth = barWidthPx,
                cap = StrokeCap.Round
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WaveformAnimationPreview() {
    val barCount = 60
    var values by remember { mutableStateOf(List(barCount) { 1f }) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(120)
            val next = Random.nextFloat()
            values = values
                .drop(1)
                .plus(next)
        }
    }

    MaterialTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Red),
            contentAlignment = Alignment.Center
        ) {
            Waveform(
                amplitudes = values,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
