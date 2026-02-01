package rs.smobile.speak2act.feature.billanalyzer.domain

import kotlinx.serialization.Serializable

@Serializable
data class Bill(
    val items: List<BillItem>
)