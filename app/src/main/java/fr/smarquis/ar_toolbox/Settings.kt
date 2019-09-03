package fr.smarquis.ar_toolbox

import android.content.Context
import android.content.SharedPreferences
import android.view.MenuItem
import androidx.preference.PreferenceManager
import com.google.ar.sceneform.ArSceneView
import java.util.concurrent.atomic.AtomicBoolean

class Settings(context: Context) {

    companion object : Singleton<Settings, Context>(::Settings)

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    val sunlight = Sunlight(true, "sunlight", prefs)
    val shadows = Shadows(true, "shadows", prefs)
    val planes = Planes(true, "planes", prefs)
    val selection = Selection(true, "selection", prefs)

    open class AtomicBooleanPref(defaultValue: Boolean, private val key: String, private val prefs: SharedPreferences) {

        private val value: AtomicBoolean = AtomicBoolean(prefs.getBoolean(key, defaultValue))

        fun get() = value.get()

        fun toggle() {
            val newValue = get().not()
            value.set(newValue)
            prefs.edit().putBoolean(key, newValue).apply()
        }

    }

    class Sunlight(defaultValue: Boolean, key: String, prefs: SharedPreferences) : AtomicBooleanPref(defaultValue, key, prefs) {

        fun toggle(menuItem: MenuItem, arSceneView: ArSceneView) {
            toggle()
            applyTo(menuItem)
            applyTo(arSceneView)
        }

        fun applyTo(arSceneView: ArSceneView) {
            arSceneView.scene?.sunlight?.isEnabled = get()
        }

        fun applyTo(menuItem: MenuItem) {
            menuItem.isChecked = get()
        }

    }

    class Shadows(defaultValue: Boolean, key: String, prefs: SharedPreferences) : AtomicBooleanPref(defaultValue, key, prefs) {

        fun toggle(menuItem: MenuItem, arSceneView: ArSceneView) {
            toggle()
            applyTo(menuItem)
            applyTo(arSceneView)
        }

        fun applyTo(arSceneView: ArSceneView) {
            val value = get()
            arSceneView.scene?.callOnHierarchy {
                it.renderable?.apply {
                    isShadowCaster = value
                    isShadowReceiver = value
                }
            }
        }

        fun applyTo(menuItem: MenuItem) {
            menuItem.isChecked = get()
        }

    }

    class Planes(defaultValue: Boolean, key: String, prefs: SharedPreferences) : AtomicBooleanPref(defaultValue, key, prefs) {

        fun toggle(menuItem: MenuItem, arSceneView: ArSceneView) {
            toggle()
            applyTo(menuItem)
            applyTo(arSceneView)
        }

        fun applyTo(arSceneView: ArSceneView) {
            arSceneView.planeRenderer?.isEnabled = get()
        }

        fun applyTo(menuItem: MenuItem) {
            menuItem.isChecked = get()
        }

    }

    class Selection(defaultValue: Boolean, key: String, prefs: SharedPreferences) : AtomicBooleanPref(defaultValue, key, prefs) {

        fun toggle(menuItem: MenuItem, selectionVisualizer: Footprint) {
            toggle()
            applyTo(menuItem)
            applyTo(selectionVisualizer)
        }

        fun applyTo(selectionVisualizer: Footprint) {
            selectionVisualizer.isEnabled = get()
        }

        fun applyTo(menuItem: MenuItem) {
            menuItem.isChecked = get()
        }

    }

}