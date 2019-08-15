package fr.smarquis.ar_toolbox

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import com.google.ar.sceneform.HitTestResult
import com.google.ar.sceneform.ux.BaseTransformableNode
import com.google.ar.sceneform.ux.SelectionVisualizer
import com.google.ar.sceneform.ux.TransformationSystem

class Coordinator(
    context: Context,
    private val onArTap: (MotionEvent) -> Unit,
    private val onNodeSelected: (old: Nodes?, new: Nodes?) -> Unit
) : TransformationSystem(
    context.resources.displayMetrics, Footprint(context)
) {

    override fun getSelectedNode(): Nodes? = super.getSelectedNode() as? Nodes

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(motionEvent: MotionEvent): Boolean {
            onArTap(motionEvent)
            return true
        }
    })

    override fun getSelectionVisualizer(): Footprint {
        return super.getSelectionVisualizer() as Footprint
    }

    override fun setSelectionVisualizer(selectionVisualizer: SelectionVisualizer?) {
        // Prevent changing the selection visualizer
    }

    override fun onTouch(hitTestResult: HitTestResult?, motionEvent: MotionEvent?) {
        super.onTouch(hitTestResult, motionEvent)
        if (hitTestResult?.node == null) {
            gestureDetector.onTouchEvent(motionEvent)
        }
    }

    override fun selectNode(node: BaseTransformableNode?): Boolean {
        val old = selectedNode
        when (node) {
            is Nodes -> {
                return super.selectNode(node).also { selected ->
                    if (selected) {
                        onNodeSelected(old, node)
                    }
                }
            }
            null -> {
                return super.selectNode(node).also {
                    onNodeSelected(old, node)
                }
            }
        }
        return false
    }

}
