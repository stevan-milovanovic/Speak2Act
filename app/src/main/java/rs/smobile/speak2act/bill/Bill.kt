package rs.smobile.speak2act.bill

import kotlinx.serialization.Serializable

@Serializable
data class Bill(
    val items: List<BillItem>
)