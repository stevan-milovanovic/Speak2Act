package rs.smobile.speak2act.domain

data class Transaction(
    val action: String,
    val amount: Double,
    val currency: String,
    val person: String,
    val description: String
)
