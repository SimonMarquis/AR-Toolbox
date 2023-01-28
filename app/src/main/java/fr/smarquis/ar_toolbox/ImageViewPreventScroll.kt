package fr.smarquis.ar_toolbox

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import androidx.appcompat.widget.AppCompatImageView

class ImageViewPreventScroll @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    var requestDisallowInterceptTouchEvent = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == ACTION_DOWN && requestDisallowInterceptTouchEvent) {
            parent.requestDisallowInterceptTouchEvent(true)
        }
        return super.onTouchEvent(event)
    }
}
