package rs.smobile.speak2act.feature.actionfigure.ui.result

sealed class ActionFigureGenerationState(open val url: String?) {
    data class Initial(override val url: String? = null) : ActionFigureGenerationState(url)
    data class Loading(override val url: String?) : ActionFigureGenerationState(url)
    data class Success(override val url: String?, val imageUrl: String) :
        ActionFigureGenerationState(url)

    data class Error(override val url: String? = null, val message: String) :
        ActionFigureGenerationState(url)
}