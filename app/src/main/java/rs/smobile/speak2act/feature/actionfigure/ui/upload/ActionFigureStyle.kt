package rs.smobile.speak2act.feature.actionfigure.ui.upload

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.ui.graphics.vector.ImageVector

enum class ActionFigureStyle(
    val title: String,
    val icon: ImageVector
) {
    MEDIEVAL_KNIGHT("Medieval Knight", KnightIcon),
    PRO_ATHLETE("Pro Athlete", AthleteIcon),
    SCI_FI_OPERATIVE("Sci-Fi Operative", SciFiIcon),
    SECRET_AGENT("Secret Agent", AgentIcon)
}

val AgentIcon = Icons.Filled.Accessibility
val KnightIcon = Icons.Filled.AccessibilityNew
val AthleteIcon = Icons.Filled.SettingsAccessibility
val SciFiIcon = Icons.Filled.Science
