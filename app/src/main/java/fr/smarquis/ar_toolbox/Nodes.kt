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
import com.google.ar.sceneform.rendering.Renderable.RENDER_PRIORITY_FIRST
import com.google.ar.sceneform.ux.TransformableNode
import com.google.ar.sceneform.ux.TransformationSystem
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

sealed class Nodes(
    name: String,
    renderable: Renderable,
    system: TransformationSystem
) : TransformableNode(system) {

    companion object {
        private val IDS: MutableMap<KClass<*>, AtomicLong> = mutableMapOf()
        fun Any.newId(): Long =
            IDS.getOrElse(this::class, { AtomicLong().also { IDS[this::class] = it } }).incrementAndGet()
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

    override fun onUpdate(frameTime: FrameTime) {
        onNodeUpdate?.invoke(this)
    }

}

sealed class Shape(
    name: String,
    system: TransformationSystem,
    model: Renderable,
    color: Int,
    metallic: Int,
    roughness: Int,
    reflectance: Int
) : Nodes(name, model, system) {

    @ColorInt
    var color: Int = color
        set(value) {
            field = value
            renderable?.material?.setFloat3(MaterialFactory.MATERIAL_COLOR, Color(color))
        }

    @IntRange(from = 0, to = 100)
    var metallic: Int = metallic
        set(value) {
            field = value
            renderable?.material?.setFloat(MaterialFactory.MATERIAL_METALLIC, value / 100F)
        }

    @IntRange(from = 0, to = 100)
    var roughness: Int = roughness
        set(value) {
            field = value
            renderable?.material?.setFloat(MaterialFactory.MATERIAL_ROUGHNESS, value / 100F)
        }

    @IntRange(from = 0, to = 100)
    var reflectance: Int = reflectance
        set(value) {
            field = value
            renderable?.material?.setFloat(MaterialFactory.MATERIAL_REFLECTANCE, value / 100F)
        }

    init {
        this.metallic = metallic
        this.roughness = roughness
        this.reflectance = reflectance
    }

}


class Sphere(
    system: TransformationSystem,
    material: Material,
    color: Int,
    metallic: Int,
    roughness: Int,
    reflectance: Int
) :
    Shape(
        "Sphere",
        system,
        ShapeFactory.makeSphere(RADIUS, CENTER, material),
        color,
        metallic,
        roughness,
        reflectance
    ) {

    companion object {

        private const val RADIUS = 0.05F
        private val CENTER = Vector3(0F, RADIUS, 0F)

        fun create(
            context: Context,
            transformationSystem: TransformationSystem,
            color: Int,
            metallic: Int,
            roughness: Int,
            reflectance: Int,
            block: (Sphere) -> Unit
        ) {
            MaterialFactory.makeOpaqueWithColor(context, Color(color)).thenAccept { material ->
                block(Sphere(transformationSystem, material, color, metallic, roughness, reflectance))
            }
        }
    }

}


class Cylinder(
    system: TransformationSystem, material: Material, color: Int,
    metallic: Int,
    roughness: Int,
    reflectance: Int
) :
    Shape(
        "Cylinder",
        system,
        ShapeFactory.makeCylinder(
            RADIUS,
            HEIGHT,
            CENTER, material
        ),
        color,
        metallic,
        roughness,
        reflectance
    ) {

    companion object {

        const val RADIUS = 0.05F
        const val HEIGHT = RADIUS * 2
        val CENTER = Vector3(0F, HEIGHT / 2, 0F)

        fun create(
            context: Context,
            transformationSystem: TransformationSystem,
            color: Int,
            metallic: Int,
            roughness: Int,
            reflectance: Int,
            block: (Cylinder) -> Unit
        ) {
            MaterialFactory.makeOpaqueWithColor(context, Color(color)).thenAccept { material ->
                block(Cylinder(transformationSystem, material, color, metallic, roughness, reflectance))
            }
        }
    }
}

class Cube(
    system: TransformationSystem,
    material: Material,
    color: Int,
    metallic: Int,
    roughness: Int,
    reflectance: Int
) :
    Shape(
        "Cube",
        system,
        ShapeFactory.makeCube(
            Vector3.one().scaled(SIZE),
            CENTER, material
        ),
        color,
        metallic,
        roughness,
        reflectance
    ) {

    companion object {
        private const val SIZE = 0.1F
        private val CENTER = Vector3(0F, SIZE / 2, 0F)

        fun create(
            context: Context,
            transformationSystem: TransformationSystem,
            color: Int,
            metallic: Int,
            roughness: Int,
            reflectance: Int,
            block: (Cube) -> Unit
        ) {
            MaterialFactory.makeOpaqueWithColor(context, Color(color)).thenAccept { material ->
                block(Cube(transformationSystem, material, color, metallic, roughness, reflectance))
            }
        }
    }

}

class Layout(
    system: TransformationSystem,
    model: ViewRenderable
) : Nodes(
    "Layout",
    model,
    system
) {
    companion object {
        fun create(context: Context, transformationSystem: TransformationSystem, block: (Layout) -> Unit) {
            ViewRenderable.builder().setView(context, R.layout.view_renderable_layout).build().thenAccept {
                block(Layout(transformationSystem, it))
            }
        }
    }
}

class Andy(
    system: TransformationSystem,
    model: ModelRenderable
) : Nodes(
    "Andy",
    model,
    system
) {
    companion object {
        fun create(context: Context, transformationSystem: TransformationSystem, block: (Andy) -> Unit) {
            ModelRenderable.builder().setSource(context, R.raw.andy).build().thenAccept {
                it.renderPriority = RENDER_PRIORITY_FIRST
                block(Andy(transformationSystem, it))
            }
        }
    }
}

class Link(
    system: TransformationSystem,
    model: ModelRenderable
) : Nodes(
    "Link",
    model,
    system
) {

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

        fun create(context: Context, uri: Uri, transformationSystem: TransformationSystem, block: (Link) -> Unit) {
            warmup(context, uri).thenAccept { block(Link(it, transformationSystem)) }
        }
    }
}
