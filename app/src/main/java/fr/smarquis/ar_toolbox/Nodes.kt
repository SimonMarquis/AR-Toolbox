package fr.smarquis.ar_toolbox

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.assets.RenderableSource
import com.google.ar.sceneform.assets.RenderableSource.SourceType.GLB
import com.google.ar.sceneform.assets.RenderableSource.SourceType.GLTF2
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.rendering.MaterialFactory.*
import com.google.ar.sceneform.rendering.Renderable.RENDER_PRIORITY_FIRST
import com.google.ar.sceneform.ux.TransformableNode
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

sealed class Nodes(
    name: String,
    renderable: Renderable?,
    coordinator: Coordinator
) : TransformableNode(coordinator) {

    companion object {

        private val IDS: MutableMap<KClass<*>, AtomicLong> = mutableMapOf()

        fun Any.newId(): Long = IDS.getOrElse(this::class, { AtomicLong().also { IDS[this::class] = it } }).incrementAndGet()

    }

    init {
        this.name = "$name #${newId()}"
        this.renderable = renderable
        scaleController.apply {
            minScale = 0.25F
            maxScale = 5F
        }
    }

    var onNodeUpdate: ((Nodes) -> Any)? = null

    override fun setRenderable(renderable: Renderable?) {
        super.setRenderable(renderable?.apply {
            isShadowCaster = Settings.Shadows.get()
            isShadowReceiver = Settings.Shadows.get()
        })
    }

    override fun onUpdate(frameTime: FrameTime) {
        onNodeUpdate?.invoke(this)
    }

}

sealed class MaterialNode(
    name: String,
    renderable: Renderable?,
    properties: MaterialProperties,
    coordinator: Coordinator
) : Nodes(name, renderable, coordinator) {

    @ColorInt
    var color: Int = properties.color
        set(value) {
            field = value
            renderable?.material?.setFloat3(MATERIAL_COLOR, color.toArColor())
        }

    @IntRange(from = 0, to = 100)
    var metallic: Int = properties.metallic
        set(value) {
            field = value
            renderable?.material?.setFloat(MATERIAL_METALLIC, value / 100F)
        }

    @IntRange(from = 0, to = 100)
    var roughness: Int = properties.roughness
        set(value) {
            field = value
            renderable?.material?.setFloat(MATERIAL_ROUGHNESS, value / 100F)
        }

    @IntRange(from = 0, to = 100)
    var reflectance: Int = properties.reflectance
        set(value) {
            field = value
            renderable?.material?.setFloat(MATERIAL_REFLECTANCE, value / 100F)
        }

    init {
        applyMaterialProperties()
    }

    internal fun applyMaterialProperties() {
        this.color = color
        this.metallic = metallic
        this.roughness = roughness
        this.reflectance = reflectance
    }

}

class Sphere(
    material: Material,
    properties: MaterialProperties,
    coordinator: Coordinator
) : MaterialNode("Sphere", ShapeFactory.makeSphere(RADIUS, CENTER, material), properties, coordinator) {

    companion object {

        private const val RADIUS = 0.05F
        private val CENTER = Vector3(0F, RADIUS, 0F)

        fun create(context: Context, properties: MaterialProperties, coordinator: Coordinator, block: (Sphere) -> Unit) {
            makeOpaqueWithColor(context, properties.color.toArColor()).thenAccept { material ->
                block(Sphere(material, properties, coordinator))
            }
        }
    }

}

class Cylinder(
    material: Material,
    properties: MaterialProperties,
    coordinator: Coordinator
) : MaterialNode("Cylinder", ShapeFactory.makeCylinder(RADIUS, HEIGHT, CENTER, material), properties, coordinator) {

    companion object {

        const val RADIUS = 0.05F
        const val HEIGHT = RADIUS * 2
        val CENTER = Vector3(0F, HEIGHT / 2, 0F)

        fun create(context: Context, properties: MaterialProperties, coordinator: Coordinator, block: (Cylinder) -> Unit) {
            makeOpaqueWithColor(context, properties.color.toArColor()).thenAccept { material ->
                block(Cylinder(material, properties, coordinator))
            }
        }
    }
}

class Cube(
    material: Material,
    properties: MaterialProperties,
    coordinator: Coordinator
) : MaterialNode("Cube", ShapeFactory.makeCube(Vector3.one().scaled(SIZE), CENTER, material), properties, coordinator) {

    companion object {

        private const val SIZE = 0.1F
        private val CENTER = Vector3(0F, SIZE / 2, 0F)

        fun create(context: Context, properties: MaterialProperties, coordinator: Coordinator, block: (Cube) -> Unit) {
            makeOpaqueWithColor(context, properties.color.toArColor()).thenAccept { material ->
                block(Cube(material, properties, coordinator))
            }
        }
    }

}

class Layout(
    renderable: ViewRenderable,
    coordinator: Coordinator
) : Nodes("Layout", renderable, coordinator) {
    companion object {

        private const val HEIGHT = 0.3F

        fun create(context: Context, coordinator: Coordinator, block: (Layout) -> Unit) {
            ViewRenderable.builder()
                .setView(context, R.layout.view_renderable_layout)
                .setSizer(FixedHeightViewSizer(HEIGHT))
                .build().thenAccept { block(Layout(it, coordinator)) }
        }

    }
}

class Andy(
    renderable: ModelRenderable,
    coordinator: Coordinator
) : Nodes("Andy", renderable, coordinator) {
    companion object {
        fun create(context: Context, coordinator: Coordinator, block: (Andy) -> Unit) {
            ModelRenderable.builder().setSource(context, R.raw.andy).build().thenAccept {
                it.renderPriority = RENDER_PRIORITY_FIRST
                block(Andy(it, coordinator))
            }
        }
    }
}

class Link(
    renderable: ModelRenderable,
    coordinator: Coordinator
) : Nodes("Link", renderable, coordinator) {

    companion object {

        fun warmup(context: Context, uri: Uri): CompletableFuture<ModelRenderable> {
            return ModelRenderable.builder().apply {
                when {
                    uri.toString().endsWith("GLTF", ignoreCase = true) -> {
                        setSource(context, RenderableSource.builder().setSource(context, uri, GLTF2).build())
                    }
                    uri.toString().endsWith("GLB", ignoreCase = true) -> {
                        setSource(context, RenderableSource.builder().setSource(context, uri, GLB).build())
                    }
                    else -> setSource(context, uri)
                }
            }
                .setRegistryId(uri.toString())
                .build()
                .exceptionally {
                    Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                    Log.e("Link", "create", it)
                    null
                }
        }

        fun create(context: Context, uri: Uri, coordinator: Coordinator, block: (Link) -> Unit) {
            warmup(context, uri).thenAccept { block(Link(it, coordinator)) }
        }
    }
}
