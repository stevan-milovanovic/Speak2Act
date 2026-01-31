package rs.smobile.speak2act.ui.voicerecorder.component

import androidx.compose.foundation.BorderStroke
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
import rs.smobile.speak2act.ui.voicerecorder.VoiceRecorderUiState


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

        is VoiceRecorderUiState.Error -> Text(
            text = uiState.response,
            modifier = modifier.fillMaxWidth(),
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        VoiceRecorderUiState.Initial -> Unit
        is VoiceRecorderUiState.Success -> {
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
                    OutcomeText(R.string.action, uiState.action)
                    Spacer(Modifier.size(16.dp))
                    OutcomeText(R.string.amount, uiState.amount.toString())
                    Spacer(Modifier.size(16.dp))
                    OutcomeText(R.string.currency, uiState.currency)
                    Spacer(Modifier.size(16.dp))
                    OutcomeText(R.string.recipient, uiState.person)
                    Spacer(Modifier.size(16.dp))
                    OutcomeText(R.string.purpose, uiState.description)
                    Spacer(Modifier.size(16.dp))
                }
            }
        }
    }
}