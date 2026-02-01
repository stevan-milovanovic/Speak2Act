package rs.smobile.speak2act.ui.intro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import rs.smobile.speak2act.R
import rs.smobile.speak2act.ui.theme.Speak2ActTheme


@Composable
fun ActionSelectionScreen(
    onSpeechClick: () -> Unit, onBillClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ActionCard(
                title = stringResource(R.string.speech_to_transaction),
                description = stringResource(R.string.convert_voice_to_transaction_instruction),
                icon = Icons.Default.Mic,
                gradient = listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)),
                onClick = onSpeechClick
            )

            Spacer(modifier = Modifier.height(20.dp))

            ActionCard(
                title = stringResource(R.string.bill_analyser),
                description = stringResource(R.string.extract_items_from_bill_image),
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                gradient = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
                onClick = onBillClick
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Brush.linearGradient(gradient), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Column(modifier = Modifier.padding(start = 20.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Preview
@Composable
private fun ActionSelectionScreenPreview() {
    Speak2ActTheme {
        ActionSelectionScreen(onSpeechClick = {}) { }
    }
}