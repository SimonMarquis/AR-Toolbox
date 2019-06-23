package fr.smarquis.ar_toolbox

import com.google.ar.sceneform.ArSceneView
import java.util.concurrent.atomic.AtomicBoolean

fun AtomicBoolean.toggle() = set(!get())

sealed class Settings {

    object Sunlight : AtomicBoolean(true) {

        fun update(arSceneView: ArSceneView) {
            arSceneView.scene.sunlight?.isEnabled = get()
        }
    }

    object Shadows : AtomicBoolean(true) {
        fun update(arSceneView: ArSceneView) {
            val get = get()
            arSceneView.scene.callOnHierarchy {
                it.renderable?.apply {
                    isShadowCaster = get
                    isShadowReceiver = get
                }
            }
        }
    }

    object Planes : AtomicBoolean(true) {
        fun update(arSceneView: ArSceneView) {
            arSceneView.planeRenderer.isEnabled = get()
        }
    }


    object Selection : AtomicBoolean(true) {

        fun update(selectionVisualizer: Footprint) {
            selectionVisualizer.isEnabled = get()
        }
    }


}