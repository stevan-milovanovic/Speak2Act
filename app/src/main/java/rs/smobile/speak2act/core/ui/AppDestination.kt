package rs.smobile.speak2act.core.ui

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import rs.smobile.speak2act.R


sealed interface AppDestination {
    @get:StringRes
    val titleRes: Int

    @Serializable
    data object Home : AppDestination {
        override val titleRes: Int = R.string.choose_action
    }

    @Serializable
    data object Voice : AppDestination {
        override val titleRes: Int = R.string.voice_intent_action
    }

    @Serializable
    data object Bill : AppDestination {
        override val titleRes: Int = R.string.bill_summary
    }

    @Serializable
    data object ActionFigure : AppDestination {
        override val titleRes: Int = R.string.generate_action_figure
    }
}