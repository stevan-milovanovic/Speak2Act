package rs.smobile.speak2act.bill

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BillItem(
    val quantity: Int = 1,
    @JsonNames("description", "name", "article")
    val description: String,
    @JsonNames("price", "amount", "total_price")
    val price: Double
)
