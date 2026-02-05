package rs.smobile.speak2act.feature.actionfigure.domain

interface ActionFigureService {

    /**
     * @param imageUrl url of the image based on which action figure is created
     * @param prompt instructions for action figure generation
     * @return url of generated action figure image
     */
    suspend fun generateActionFigure(imageUrl: String, prompt: String? = null): Result<String>

}