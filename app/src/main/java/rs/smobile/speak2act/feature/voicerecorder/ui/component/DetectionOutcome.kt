package rs.smobile.speak2act.feature.voicerecorder.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rs.smobile.speak2act.R
import rs.smobile.speak2act.feature.voicerecorder.domain.VoiceTransactionAction
import rs.smobile.speak2act.feature.voicerecorder.ui.VoiceRecorderUiState


@Composable
fun DetectionOutcome(
    modifier: Modifier = Modifier,
    uiState: VoiceRecorderUiState
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

        is VoiceRecorderUiState.Error -> Text(
            text = uiState.response,
            modifier = modifier.fillMaxWidth(),
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        VoiceRecorderUiState.Initial -> Unit
        is VoiceRecorderUiState.Success -> {
            val transaction = uiState.transaction
            Surface(
                modifier = modifier,
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
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    HorizontalDivider()
                    Spacer(Modifier.size(16.dp))
                    OutcomeText(R.string.action, transaction?.action?.displayLabel())
                    transaction?.amount?.let { amount ->
                        Spacer(Modifier.size(16.dp))
                        OutcomeText(
                            R.string.amount,
                            stringResource(R.string.amount_format, amount.toDouble())
                        )
                    }
                    Spacer(Modifier.size(16.dp))
                    OutcomeText(R.string.currency, transaction?.currency)
                    Spacer(Modifier.size(16.dp))
                    OutcomeText(R.string.recipient, transaction?.receiverName)
                    Spacer(Modifier.size(16.dp))
                    OutcomeText(R.string.purpose, transaction?.message)
                    Spacer(Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun VoiceTransactionAction.displayLabel(): String = stringResource(
    when (this) {
        VoiceTransactionAction.SEND -> R.string.transaction_action_send
        VoiceTransactionAction.REQUEST -> R.string.transaction_action_request
    }
)
