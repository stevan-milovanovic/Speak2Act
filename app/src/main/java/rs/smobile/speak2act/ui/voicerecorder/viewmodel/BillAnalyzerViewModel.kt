package rs.smobile.speak2act.ui.voicerecorder.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rs.smobile.speak2act.bill.Bill
import rs.smobile.speak2act.ui.billanalyzer.BillAnalyzer
import javax.inject.Inject

@HiltViewModel
class BillAnalyzerViewModel @Inject constructor(
    private val billAnalyzer: BillAnalyzer
) : ViewModel() {

    private companion object {
        private const val TAG = "BillAnalyzerViewModel"
    }

    private val _bill = MutableStateFlow<Bill?>(null)
    val bill = _bill.asStateFlow()

    fun analyzeBill(inputImage: InputImage) {
        viewModelScope.launch {
            billAnalyzer.ocrOnDevice(inputImage).collect { bill ->
                _bill.value = bill
            }
        }
    }

    fun clearData() {
        _bill.value = null
    }

}