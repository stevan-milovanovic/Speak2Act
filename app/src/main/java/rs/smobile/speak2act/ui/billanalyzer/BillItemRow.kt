package rs.smobile.speak2act.ui.billanalyzer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import rs.smobile.speak2act.R
import rs.smobile.speak2act.bill.BillItem
import rs.smobile.speak2act.ui.theme.BackgroundDarkBottom
import rs.smobile.speak2act.ui.theme.BackgroundDarkTop
import rs.smobile.speak2act.ui.theme.Speak2ActTheme


@Composable
fun BillItemRow(item: BillItem) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF4FACFE),
                                Color(0xFF00F2FE)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.quantity, item.quantity),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Text(
                text = item.description,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
                color = Color.White.copy(alpha = 0.9f)
            )

            Text(
                text = stringResource(R.string.amount_format, item.price),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun BillItemPreview() {
    val items = listOf(
        BillItem(1, "Pizza", 13.5),
        BillItem(2, "Coca Cola", 6.0),
        BillItem(1, "Cheesecake", 8.0),
    )
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(
            BackgroundDarkTop,
            BackgroundDarkBottom
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )
    Speak2ActTheme {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(top = 64.dp, start = 16.dp, end = 16.dp)
        ) {
            items(items) {
                BillItemRow(it)
            }
        }
    }

}