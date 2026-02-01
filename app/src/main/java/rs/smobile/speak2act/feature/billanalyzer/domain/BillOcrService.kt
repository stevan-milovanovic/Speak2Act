package rs.smobile.speak2act.feature.billanalyzer.domain

import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level abstraction for extracting bill/receipt data from an image.
 */
interface BillOcrService {
    fun ocrOnDevice(image: InputImage): Flow<Bill?>
}