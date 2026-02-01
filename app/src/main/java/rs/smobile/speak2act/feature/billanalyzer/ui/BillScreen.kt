package rs.smobile.speak2act.feature.billanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import rs.smobile.speak2act.R
import rs.smobile.speak2act.feature.billanalyzer.domain.Bill
import rs.smobile.speak2act.feature.billanalyzer.domain.BillItem
import rs.smobile.speak2act.core.theme.BackgroundDarkBottom
import rs.smobile.speak2act.core.theme.BackgroundDarkTop
import rs.smobile.speak2act.core.theme.Speak2ActTheme


@Composable
fun BillScreen(
    modifier: Modifier = Modifier,
    innerPadding: PaddingValues,
    bill: Bill
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(innerPadding)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(bill.items) {
                BillItemRow(it)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        val total = bill.items.sumOf { it.price }
        if (total > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.total_amount),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.amount_format, total),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BillScreenPreview() {
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(
            BackgroundDarkTop,
            BackgroundDarkBottom
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )
    Speak2ActTheme {
        BillScreen(
            modifier = Modifier.background(backgroundGradient),
            innerPadding = PaddingValues(16.dp),
            Bill(
                items = listOf(
                    BillItem(1, "Pizza", 12.5),
                    BillItem(2, "Coca Cola", 6.0),
                    BillItem(2, "Tiramisu", 8.0)
                )
            )
        )
    }
}