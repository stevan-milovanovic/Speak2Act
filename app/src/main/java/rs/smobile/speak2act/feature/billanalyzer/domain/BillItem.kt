package rs.smobile.speak2act.feature.billanalyzer.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BillItem(
    val quantity: Int = 1,
    @JsonNames("description", "name", "article", "item", "product")
    val description: String,
    @JsonNames("price", "amount", "total_price")
    val price: Double
)
