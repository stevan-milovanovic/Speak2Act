package rs.smobile.speak2act.feature.voicerecorder.domain

data class Transaction(
    val action: String,
    val amount: Double,
    val currency: String,
    val person: String,
    val description: String
)