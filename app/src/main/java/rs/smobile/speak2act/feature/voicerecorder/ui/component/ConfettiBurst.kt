package rs.smobile.speak2act.feature.voicerecorder.ui.component

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import rs.smobile.speak2act.core.theme.BackgroundDarkTop
import rs.smobile.speak2act.core.theme.BrandBlue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** The app's brand and route colours, plus amber and the success green. */
private val ConfettiColors = listOf(
    BrandBlue,
    Color(0xFF2DD4BF),   // on-device teal
    Color(0xFF818CF8),   // cloud indigo
    OnDeviceLlmPink,
    Color(0xFFFBBF24),   // amber
    SuccessGreen,
)

private const val PARTICLE_COUNT = 150
private const val DURATION_SECONDS = 3f
private const val FADE_SECONDS = 0.8f

/** Where the burst starts, as a fraction of the overlay's width and height. */
private const val ORIGIN_X = 0.5f
private const val ORIGIN_Y = 0.35f

/** Air drag (1/s) and the falling speed it settles at (dp/s): paper flutters down instead of dropping. */
private const val DRAG = 3f
private const val TERMINAL_SPEED = 250f

/**
 * One-shot confetti burst, played once when a transaction has been executed. Purely decorative:
 * it draws on a single canvas without recomposing, has no semantics, is skipped when the system's
 * animations are turned off, and isn't replayed after a configuration change.
 */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animationsOn = remember { !context.animationsDisabled() }
    var played by rememberSaveable { mutableStateOf(false) }
    if (!animationsOn || played || LocalInspectionMode.current) return

    val particles = remember { List(PARTICLE_COUNT) { ConfettiParticle.random(Random.Default) } }
    val elapsed = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        elapsed.animateTo(
            DURATION_SECONDS, tween(durationMillis = (DURATION_SECONDS * 1000).toInt(), easing = LinearEasing)
        )
        played = true
    }
    Spacer(modifier.drawBehind { drawConfetti(particles, elapsed.value) })
}

/**
 * A paper piece: a ribbon (a tumbling rectangle) or a dot. Speeds are in dp/s, angles in degrees
 * and radians as named.
 */
private class ConfettiParticle(
    val color: Color,
    val isDot: Boolean,
    val widthDp: Float,
    val heightDp: Float,
    val vx: Float,
    val vy: Float,
    val rotation: Float,
    val spinDegreesPerSecond: Float,
    val phase: Float,
    val flipRadiansPerSecond: Float,
    val swayDp: Float,
    val swayRadiansPerSecond: Float,
) {
    companion object {
        fun random(random: Random): ConfettiParticle {
            // A fan from up-left to up-right: screen y grows downwards, so "up" is a negative angle.
            val angle = random.range(-160f, -20f) * (PI.toFloat() / 180f)
            val speed = random.range(400f, 1_100f)
            return ConfettiParticle(
                color = ConfettiColors[random.nextInt(ConfettiColors.size)],
                isDot = random.nextFloat() < 0.3f,
                widthDp = random.range(6f, 10f),
                heightDp = random.range(10f, 16f),
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                rotation = random.range(0f, 360f),
                spinDegreesPerSecond = random.range(-360f, 360f),
                phase = random.range(0f, 2 * PI.toFloat()),
                flipRadiansPerSecond = random.range(6f, 14f),
                swayDp = random.range(4f, 12f),
                swayRadiansPerSecond = random.range(3f, 6f),
            )
        }

        private fun Random.range(from: Float, until: Float) = from + nextFloat() * (until - from)
    }
}

/**
 * Draws every particle [t] seconds after the burst. Motion has a closed form, so no state is kept
 * between frames: linear air drag on both axes, gravity balanced by drag at [TERMINAL_SPEED], a
 * small sideways sway, and a fade-out over the last [FADE_SECONDS].
 */
private fun DrawScope.drawConfetti(particles: List<ConfettiParticle>, t: Float) {
    val alpha = ((DURATION_SECONDS - t) / FADE_SECONDS).coerceIn(0f, 1f)
    if (alpha <= 0f) return
    val travel = (1f - exp(-DRAG * t)) / DRAG          // ∫₀ᵗ e^(−DRAG·s) ds
    val originX = size.width * ORIGIN_X
    val originY = size.height * ORIGIN_Y
    for (p in particles) {
        val x = originX + (p.vx * travel + p.swayDp * sin(p.phase + p.swayRadiansPerSecond * t)) * density
        val y = originY + ((p.vy - TERMINAL_SPEED) * travel + TERMINAL_SPEED * t) * density
        val width = p.widthDp * density
        withTransform({
            translate(x, y)
            rotate(p.rotation + p.spinDegreesPerSecond * t, pivot = Offset.Zero)
            // Tumbling paper: the piece's height shrinks and grows as it turns over.
            scale(1f, cos(p.phase + p.flipRadiansPerSecond * t), pivot = Offset.Zero)
        }) {
            if (p.isDot) {
                drawCircle(p.color, radius = width / 2, center = Offset.Zero, alpha = alpha)
            } else {
                val height = p.heightDp * density
                drawRect(p.color, Offset(-width / 2, -height / 2), Size(width, height), alpha = alpha)
            }
        }
    }
}

/** True when the user turned animations off (Developer options or accessibility "Remove animations"). */
private fun Context.animationsDisabled(): Boolean =
    runCatching {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

@Preview
@Composable
private fun ConfettiBurstPreview() {
    val particles = remember {
        val seeded = Random(42)
        List(PARTICLE_COUNT) { ConfettiParticle.random(seeded) }
    }
    Spacer(
        Modifier
            .size(360.dp, 640.dp)
            .background(BackgroundDarkTop)
            .drawBehind { drawConfetti(particles, t = 0.8f) }
    )
}
