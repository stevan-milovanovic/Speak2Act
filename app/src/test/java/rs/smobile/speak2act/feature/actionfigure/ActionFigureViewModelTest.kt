package rs.smobile.speak2act.feature.actionfigure

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import rs.smobile.speak2act.feature.actionfigure.data.ImageUploadState
import rs.smobile.speak2act.feature.actionfigure.domain.ActionFigureService
import rs.smobile.speak2act.feature.actionfigure.domain.ImageUploadService
import rs.smobile.speak2act.feature.actionfigure.ui.ActionFigureUiState
import rs.smobile.speak2act.feature.actionfigure.ui.ActionFigureViewModel
import rs.smobile.speak2act.feature.actionfigure.ui.result.ActionFigureGenerationState

@OptIn(ExperimentalCoroutinesApi::class)
class ActionFigureViewModelTest {

    private companion object {
        private val IMAGE_URI = Uri.parse("https://example.com/dummy.jpg")
        private const val IMAGE_PUBLIC_URL = "https://demo.com/image/upload/test.jpg"
        private const val ACTION_FIGURE_URL = "https://demo.com/image/action-figure-result.jpg"
    }

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uploadImage_updatesUiState_andEmitsSuccess() = runTest {
        val fakeUpload = FakeImageUploadService()
        val fakeAction = FakeActionFigureService()
        val vm = ActionFigureViewModel(fakeAction, fakeUpload)

        // initial state
        assertEquals(
            ActionFigureUiState.PickImage::class.simpleName,
            vm.uiState.value::class.simpleName
        )

        vm.uploadImage(IMAGE_URI)

        // advance coroutines to run upload flow
        testScheduler.advanceUntilIdle()

        assertEquals(
            ActionFigureUiState.UploadImage::class.simpleName,
            vm.uiState.value::class.simpleName
        )
        val imgState = vm.imageUploadState.value
        require(imgState is ImageUploadState.Success)
        assertEquals(IMAGE_PUBLIC_URL, imgState.url)
    }

    @Test
    fun generate_withoutImage_setsError() = runTest {
        val fakeUpload = FakeImageUploadService()
        val fakeAction = FakeActionFigureService()
        val vm = ActionFigureViewModel(fakeAction, fakeUpload)

        vm.generate()
        testScheduler.advanceUntilIdle()

        val genState = vm.actionFigureGenerationState.value
        require(genState is ActionFigureGenerationState.Error)
    }

    @Test
    fun generate_withImage_callsService_andProducesSuccess() = runTest {
        val fakeUpload = FakeImageUploadService()
        val fakeAction = FakeActionFigureService()
        val vm = ActionFigureViewModel(fakeAction, fakeUpload)

        // first upload to populate imageUploadState
        vm.uploadImage(IMAGE_URI)
        testScheduler.advanceUntilIdle()

        // now generate
        vm.generate(prompt = "heroic pose")
        testScheduler.advanceUntilIdle()

        val genState = vm.actionFigureGenerationState.value
        require(genState is ActionFigureGenerationState.Success)
        assertEquals(ACTION_FIGURE_URL, genState.imageUrl)
    }

    private class FakeImageUploadService : ImageUploadService {
        override fun uploadImage(uri: Uri): Flow<ImageUploadState> = flow {
            emit(ImageUploadState.Started(uri))
            emit(ImageUploadState.Progress(uri, 50))
            emit(
                ImageUploadState.Success(
                    uri,
                    IMAGE_PUBLIC_URL
                )
            )
        }
    }

    private class FakeActionFigureService : ActionFigureService {
        override suspend fun generateActionFigure(
            imageUrl: String,
            prompt: String?
        ): Result<String> = Result.success(ACTION_FIGURE_URL)
    }
}
