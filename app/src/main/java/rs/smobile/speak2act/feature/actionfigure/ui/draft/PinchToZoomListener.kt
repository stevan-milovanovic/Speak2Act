package rs.smobile.speak2act.feature.actionfigure.ui.draft

import android.view.ScaleGestureDetector
import com.google.android.filament.Camera

class PinchToZoomListener(
    private val camera: Camera
) : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        // Adjust camera distance by scale factor
        val currentDistance = camera.focusDistance
        val scaleFactor = detector.scaleFactor

        // Clamp distance to avoid flipping / clipping
        val newDistance = (currentDistance / scaleFactor).coerceIn(0.5f, 20f)
        camera.focusDistance = newDistance
        return true
    }
}