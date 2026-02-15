package rs.smobile.speak2act.feature.actionfigure.ui.draft.component

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView

class FilamentSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}