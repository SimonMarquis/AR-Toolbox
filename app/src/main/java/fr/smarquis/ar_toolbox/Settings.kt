package fr.smarquis.ar_toolbox

import android.view.MenuItem
import com.google.ar.sceneform.ArSceneView
import java.util.concurrent.atomic.AtomicBoolean

fun AtomicBoolean.toggle() = set(!get())

class Settings {

    object Sunlight : AtomicBoolean(true) {

        fun applyTo(arSceneView: ArSceneView? = null, menuItem: MenuItem? = null) {
            with(get()) {
                arSceneView?.scene?.sunlight?.isEnabled = this
                menuItem?.isChecked = this
            }
        }

    }

    object Shadows : AtomicBoolean(true) {

        fun applyTo(arSceneView: ArSceneView? = null, menuItem: MenuItem? = null) {
            with(get()) {
                arSceneView?.scene?.callOnHierarchy {
                    it.renderable?.apply {
                        isShadowCaster = this@with
                        isShadowReceiver = this@with
                    }
                }
                menuItem?.isChecked = this
            }
        }

    }

    object Planes : AtomicBoolean(true) {

        fun applyTo(arSceneView: ArSceneView? = null, menuItem: MenuItem? = null) {
            with(get()) {
                arSceneView?.planeRenderer?.isEnabled = this
                menuItem?.isChecked = this
            }
        }

    }

    object Selection : AtomicBoolean(true) {

        fun applyTo(selectionVisualizer: Footprint? = null, menuItem: MenuItem? = null) {
            with(get()) {
                selectionVisualizer?.isEnabled = this
                menuItem?.isChecked = this
            }
        }

    }

}