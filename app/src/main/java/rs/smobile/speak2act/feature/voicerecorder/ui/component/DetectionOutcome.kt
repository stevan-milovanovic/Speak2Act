package rs.smobile.speak2act.feature.voicerecorder.ui.component

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import rs.smobile.speak2act.R
import rs.smobile.speak2act.core.theme.BrandBlue
import rs.smobile.speak2act.core.theme.Speak2ActTheme
import rs.smobile.speak2act.feature.voicerecorder.domain.InferenceRoute
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction
import rs.smobile.speak2act.feature.voicerecorder.domain.TransactionAction
import rs.smobile.speak2act.feature.voicerecorder.ui.VoiceRecorderUiState
import java.math.RoundingMode
import java.util.Locale
import kotlin.time.Duration

private val EdgeTeal = Color(0xFF2DD4BF)
private val CloudIndigo = Color(0xFF818CF8)
private val CloudAudioBlue = Color(0xFF60A5FA)

@Composable
fun DetectionOutcome(
    modifier: Modifier = Modifier,
    uiState: VoiceRecorderUiState,
    onConfirmClick: () -> Unit = {},
    onCancelClick: () -> Unit = {}
) {
    when (uiState) {
        VoiceRecorderUiState.Loading -> Box(
            modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter
        ) {
            CircularProgressIndicator()
        }

        is VoiceRecorderUiState.DownloadingModel -> Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(progress = { uiState.progress.coerceIn(0f, 1f) })
            Text(
                text = stringResource(
                    R.string.downloading_model,
                    (uiState.progress * 100f).toInt()
                ),
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        is VoiceRecorderUiState.Error -> Column(modifier = modifier.fillMaxWidth()) {
            Text(
                text = uiState.response,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            HeardLine(uiState.heard)
        }

        VoiceRecorderUiState.Initial -> Unit

        is VoiceRecorderUiState.Success -> Column(modifier = modifier) {
            TransactionCard(uiState.transaction, uiState.route, uiState.latency)
            HeardLine(uiState.heard)
            Spacer(Modifier.size(16.dp))
            ConfirmCancelButtons(
                onConfirmClick = onConfirmClick,
                onCancelClick = onCancelClick
            )
        }

        is VoiceRecorderUiState.Executing -> Column(modifier = modifier) {
            TransactionCard(uiState.transaction, uiState.route, uiState.latency)
            Spacer(Modifier.size(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = BrandBlue)
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.executing_transaction),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.size(16.dp))
        }

        is VoiceRecorderUiState.Executed -> Column(modifier = modifier) {
            TransactionCard(uiState.transaction, uiState.route, uiState.latency)
            Spacer(Modifier.size(16.dp))
            Text(
                text = successMessage(uiState.transaction),
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                color = BrandBlue
            )
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: Transaction,
    route: InferenceRoute? = null,
    latency: Duration? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            1.dp, Color.White.copy(alpha = 0.6f)
        )
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.detected_transaction),
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (route != null) {
                RouteBadge(route, latency)
                Spacer(Modifier.size(8.dp))
            }
            HorizontalDivider()
            Spacer(Modifier.size(16.dp))
            OutcomeText(R.string.action, transaction.action.displayLabel())
            Spacer(Modifier.size(16.dp))
            OutcomeText(
                R.string.amount,
                transaction.amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
            )
            Spacer(Modifier.size(16.dp))
            OutcomeText(R.string.currency, transaction.currency)
            Spacer(Modifier.size(16.dp))
            OutcomeText(R.string.recipient, transaction.receiverName)
            Spacer(Modifier.size(16.dp))
            OutcomeText(R.string.purpose, transaction.message)
            Spacer(Modifier.size(16.dp))
        }
    }
}

/** "📱 On-device · 0.8 s" (teal) or "☁️ Cloud fallback · 1.9 s" / "☁️ Cloud (audio) · 2.4 s" (indigo). */
@Composable
private fun RouteBadge(route: InferenceRoute, latency: Duration?) {
    val (label, color) = when (route) {
        InferenceRoute.EDGE -> stringResource(R.string.route_on_device) to EdgeTeal
        InferenceRoute.CLOUD_TEXT -> stringResource(R.string.route_cloud_fallback) to CloudIndigo
        InferenceRoute.CLOUD_AUDIO -> stringResource(R.string.route_cloud_audio) to CloudAudioBlue
    }
    val text = latency?.let {
        "$label · ${String.format(Locale.US, "%.1f", it.inWholeMilliseconds / 1000.0)} s"
    } ?: label
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

/** Muted italic "Heard: “…”" line with the on-device transcript. */
@Composable
private fun HeardLine(heard: String?) {
    if (heard == null) return
    Spacer(Modifier.size(8.dp))
    Text(
        text = stringResource(R.string.heard, heard),
        modifier = Modifier.fillMaxWidth(),
        fontStyle = FontStyle.Italic,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    )
}

@Composable
private fun ConfirmCancelButtons(
    onConfirmClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            onClick = onCancelClick
        ) {
            Text(stringResource(R.string.cancel))
        }
        Button(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandBlue,
                contentColor = Color.White
            ),
            onClick = onConfirmClick
        ) {
            Text(stringResource(R.string.confirm))
        }
    }
}

@Composable
private fun successMessage(transaction: Transaction): String = when (transaction.action) {
    TransactionAction.SEND -> stringResource(R.string.payment_sent, transaction.receiverName)
    TransactionAction.REQUEST -> stringResource(R.string.money_requested, transaction.receiverName)
    TransactionAction.SPLIT -> stringResource(R.string.split_request_sent, transaction.receiverName)
}

@Composable
private fun TransactionAction.displayLabel(): String = stringResource(
    when (this) {
        TransactionAction.SEND -> R.string.transaction_action_send
        TransactionAction.REQUEST -> R.string.transaction_action_request
        TransactionAction.SPLIT -> R.string.transaction_action_split
    }
)

@Preview
@Composable
private fun RouteBadgePreview() {
    Speak2ActTheme {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.onBackground)
                .padding(vertical = 16.dp, horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RouteBadge(
                InferenceRoute.EDGE,
                Duration.parse("2.3s")
            )
            RouteBadge(
                InferenceRoute.CLOUD_TEXT,
                Duration.parse("3.4s")
            )
            RouteBadge(
                InferenceRoute.CLOUD_AUDIO,
                Duration.parse("4.1s")
            )
        }
    }
}