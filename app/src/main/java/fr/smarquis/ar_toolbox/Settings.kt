package fr.smarquis.ar_toolbox

import android.content.Context
import android.content.SharedPreferences
import android.view.MenuItem
import androidx.preference.PreferenceManager
import com.google.ar.core.DepthPoint
import com.google.ar.core.Plane
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Material
import com.google.ar.sceneform.rendering.MaterialFactory.makeOpaqueWithColor
import com.google.ar.sceneform.rendering.ModelRenderable
import fr.smarquis.ar_toolbox.PointCloud.makePointCloud
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

class Settings(context: Context) {

    companion object : Singleton<Settings, Context>(::Settings)

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    val sunlight = Sunlight(true, "sunlight", prefs)
    val shadows = Shadows(true, "shadows", prefs)
    val planes = Planes(true, "planes", prefs)
    val selection = Selection(true, "selection", prefs)
    val reticle = Reticle(false, "reticle", prefs)
    val pointCloud = PointCloud(false, "pointCloud", prefs)
    val faceRegions = FaceRegions(true, "faceRegions", prefs)
    val faceMesh = FaceMesh(true, "faceMesh", prefs)

    open class AtomicBooleanPref(
        defaultValue: Boolean,
        private val key: String,
        private val prefs: SharedPreferences,
    ) {

        private val value: AtomicBoolean = AtomicBoolean(prefs.getBoolean(key, defaultValue))

        fun get() = value.get()

        fun toggle() {
            val newValue = get().not()
            value.set(newValue)
            prefs.edit().putBoolean(key, newValue).apply()
        }
    }

    class Sunlight(
        defaultValue: Boolean,
        key: String,
        prefs: SharedPreferences,
    ) : AtomicBooleanPref(defaultValue, key, prefs) {

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

    class Shadows(
        defaultValue: Boolean,
        key: String,
        prefs: SharedPreferences,
    ) : AtomicBooleanPref(defaultValue, key, prefs) {

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

    class Planes(
        defaultValue: Boolean,
        key: String,
        prefs: SharedPreferences,
    ) : AtomicBooleanPref(defaultValue, key, prefs) {

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

    class Selection(
        defaultValue: Boolean,
        key: String,
        prefs: SharedPreferences,
    ) : AtomicBooleanPref(defaultValue, key, prefs) {

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

    class Reticle(
        defaultValue: Boolean,
        key: String,
        prefs: SharedPreferences,
    ) : AtomicBooleanPref(defaultValue, key, prefs) {

        class Node(context: Context) : com.google.ar.sceneform.Node() {

            companion object {
                val INVISIBLE_SCALE: Vector3 = Vector3.zero()
                val VISIBLE_DEFAULT_SCALE: Vector3 = Vector3.one()
                val VISIBLE_PLANE_SCALE: Vector3 = Vector3.one()
                val VISIBLE_DEPTH_POINT_SCALE: Vector3 = Vector3.one().scaled(0.5F)
            }

            private var properties: MaterialProperties = MaterialProperties()

            init {
                val completableModelRenderable = ModelRenderable.builder()
                    .setSource(context.applicationContext, com.google.ar.sceneform.ux.R.raw.sceneform_footprint)
                    .build()
                val completableMaterial = makeOpaqueWithColor(context.applicationContext, properties.color.toArColor())
                CompletableFuture.allOf(completableModelRenderable, completableMaterial).thenAccept {
                    val modelRenderable = completableModelRenderable.join()
                    val material = completableMaterial.join()
                    renderable = modelRenderable.apply { collisionShape = null }
                    modelRenderable.material = material.apply { properties.update(this) }
                }
            }

            override fun onUpdate(frameTime: FrameTime?) {
                super.onUpdate(frameTime)
                val ar = scene?.view as? ArSceneView ?: return
                val frame = ar.arFrame ?: return
                val hit = frame.hitTest(ar.width * 0.5F, ar.height * 0.5F).firstOrNull {
                    val trackable = it.trackable
                    when {
                        trackable is Plane && trackable.isPoseInPolygon(it.hitPose) -> true
                        trackable is DepthPoint -> true
                        else -> false
                    }
                }
                hit?.hitPose?.let {
                    worldPosition = it.translation()
                    worldRotation = it.rotation()
                }
                localScale = when (hit?.trackable) {
                    is Plane -> VISIBLE_PLANE_SCALE
                    is DepthPoint -> VISIBLE_DEPTH_POINT_SCALE
                    null -> INVISIBLE_SCALE
                    else -> VISIBLE_DEFAULT_SCALE
                }
            }

            fun properties(block: (MaterialProperties.() -> Unit) = {}) = properties.update(renderable?.material, block)
        }

        private fun ArSceneView.findMyNode() = findNode<Node>()

        fun initAndApplyTo(arSceneView: ArSceneView) {
            if (arSceneView.findMyNode() == null) {
                Node(arSceneView.context).apply { setParent(arSceneView.scene) }
            }
            arSceneView.update()
        }

        fun updateMaterial(arSceneView: ArSceneView, block: (MaterialProperties.() -> Unit)) = arSceneView.findMyNode()?.properties(block)

        fun toggle(menuItem: MenuItem, arSceneView: ArSceneView) {
            toggle()
            applyTo(menuItem)
            arSceneView.update()
        }

        fun applyTo(menuItem: MenuItem) {
            menuItem.isChecked = get()
        }

        private fun ArSceneView.update() {
            findNode<Node>()?.isEnabled = get()
        }
    }

    class PointCloud(
        defaultValue: Boolean,
        key: String,
        prefs: SharedPreferences,
    ) : AtomicBooleanPref(defaultValue, key, prefs) {

        class Node(context: Context) : com.google.ar.sceneform.Node() {

            private var timestamp: Long = 0
            private var properties: MaterialProperties = MaterialProperties(metallic = 100, roughness = 100, reflectance = 0)
            private var material: Material? = null
                set(value) {
                    field = value?.apply { properties.update(this) }
                }

            init {
                makeOpaqueWithColor(context.applicationContext, properties.color.toArColor()).thenAccept { material = it }
            }

            fun properties(block: (MaterialProperties.() -> Unit) = {}) {
                properties.update(renderable?.material, block)
            }

            override fun onUpdate(frameTime: FrameTime?) {
                super.onUpdate(frameTime)
                if (!isEnabled) return
                val ar = scene?.view as? ArSceneView ?: return
                val frame = ar.arFrame ?: return
                frame.acquirePointCloud().use {
                    render(it)
                }
            }

            private fun render(pointCloud: com.google.ar.core.PointCloud) {
                timestamp = pointCloud.timestamp.takeIf { it != timestamp } ?: return
                val material = material ?: return
                val definition = makePointCloud(pointCloud, material) ?: return // reset renderable?
                when (val render = renderable) {
                    null -> ModelRenderable.builder().setSource(definition).build().thenAccept {
                        renderable = it.apply {
                            properties.update(material)
                            isShadowCaster = false
                            isShadowReceiver = false
                            collisionShape = null
                        }
                    }
                    else -> render.updateFromDefinition(definition).also {
                        renderable?.collisionShape = null
                    }
                }
            }
        }

        fun initAndApplyTo(arSceneView: ArSceneView) {
            if (arSceneView.findNode<Node>() == null) {
                Node(arSceneView.context).apply { setParent(arSceneView.scene) }
            }
            arSceneView.update()
        }

        fun toggle(menuItem: MenuItem, arSceneView: ArSceneView) {
            toggle()
            applyTo(menuItem)
            arSceneView.update()
        }

        fun applyTo(menuItem: MenuItem) {
            menuItem.isChecked = get()
        }

        private fun ArSceneView.update() {
            findNode<Node>()?.isEnabled = get()
        }

        fun updateMaterial(arSceneView: ArSceneView, block: (MaterialProperties.() -> Unit)) {
            arSceneView.findNode<Node>()?.properties(block)
        }
    }

    class FaceRegions(
        defaultValue: Boolean,
        key: String,
        prefs: SharedPreferences,
    ) : AtomicBooleanPref(defaultValue, key, prefs) {

        fun toggle(menuItem: MenuItem, arSceneView: ArSceneView) {
            toggle()
            applyTo(menuItem)
            applyTo(arSceneView)
        }

        fun applyTo(arSceneView: ArSceneView) {
            arSceneView.scene?.callOnHierarchy {
                (it as? FaceActivity.FaceNode)?.apply(this)
            }
        }

        fun applyTo(menuItem: MenuItem) {
            menuItem.isChecked = get()
        }
    }

    class FaceMesh(
        defaultValue: Boolean,
        key: String,
        prefs: SharedPreferences,
    ) : AtomicBooleanPref(defaultValue, key, prefs) {

        fun toggle(menuItem: MenuItem, arSceneView: ArSceneView) {
            toggle()
            applyTo(menuItem)
            applyTo(arSceneView)
        }

        fun applyTo(arSceneView: ArSceneView) {
            arSceneView.scene?.callOnHierarchy {
                (it as? FaceActivity.FaceNode)?.apply(this)
            }
        }

        fun applyTo(menuItem: MenuItem) {
            menuItem.isChecked = get()
        }
    }
}
