package rs.smobile.speak2act.feature.voicerecorder.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rs.smobile.speak2act.R
import rs.smobile.speak2act.core.theme.BackgroundDarkTop
import rs.smobile.speak2act.core.theme.Speak2ActTheme

internal val SuccessGreen = Color(0xFF22C55E)

/**
 * "✓ Payment sent to Stefan · Demo: no real money is moved" — the green banner under an executed
 * transaction. It pops in once: the check badge springs in, then the text fades and slides up.
 * Announced politely by screen readers.
 */
@Composable
fun SuccessBanner(message: String, modifier: Modifier = Modifier) {
    // Previews show the final frame; on the device both start hidden and animate in.
    val start = if (LocalInspectionMode.current) 1f else 0f
    val badgeScale = remember { Animatable(start) }
    val textReveal = remember { Animatable(start) }
    LaunchedEffect(Unit) {
        launch {
            badgeScale.animateTo(
                1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        }
        textReveal.animateTo(1f, tween(durationMillis = 400, delayMillis = 150))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp),
        color = SuccessGreen.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = badgeScale.value
                        scaleY = badgeScale.value
                    }
                    .background(SuccessGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = textReveal.value
                    translationY = (1f - textReveal.value) * 8.dp.toPx()
                }
            ) {
                Text(
                    text = message,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = stringResource(R.string.demo_no_real_money),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Preview
@Composable
private fun SuccessBannerPreview() {
    Speak2ActTheme {
        Box(
            Modifier
                .background(BackgroundDarkTop)
                .padding(24.dp)
        ) {
            SuccessBanner("Payment sent to Stefan")
        }
    }
}
