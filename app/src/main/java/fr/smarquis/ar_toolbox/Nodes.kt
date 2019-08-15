package fr.smarquis.ar_toolbox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.Layout
import android.text.style.AlignmentSpan
import android.util.Log
import android.view.ContextThemeWrapper
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.google.ar.core.*
import com.google.ar.core.Anchor.CloudAnchorState.*
import com.google.ar.core.AugmentedImage.TrackingMethod.FULL_TRACKING
import com.google.ar.core.TrackingState.*
import com.google.ar.sceneform.*
import com.google.ar.sceneform.Camera
import com.google.ar.sceneform.assets.RenderableSource
import com.google.ar.sceneform.assets.RenderableSource.SourceType.GLB
import com.google.ar.sceneform.assets.RenderableSource.SourceType.GLTF2
import com.google.ar.sceneform.collision.RayHit
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.rendering.MaterialFactory.makeOpaqueWithColor
import com.google.ar.sceneform.ux.TransformableNode
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

sealed class Nodes(
    name: String,
    coordinator: Coordinator
) : TransformableNode(coordinator) {

    companion object {

        private const val PLANE_ANCHORING_DISTANCE = 2F
        private const val DEFAULT_POSE_DISTANCE = 2F

        private val IDS: MutableMap<KClass<*>, AtomicLong> = mutableMapOf()

        fun Any.newId(): Long = IDS.getOrElse(this::class, { AtomicLong().also { IDS[this::class] = it } }).incrementAndGet()

        fun defaultPose(ar: ArSceneView): Pose {
            val centerX = ar.width / 2F
            val centerY = ar.height / 2F
            val hits = ar.arFrame?.hitTest(centerX, centerY)
            val planeHitPose = hits?.firstOrNull {
                (it.trackable as? Plane)?.isPoseInPolygon(it.hitPose) == true && it.distance <= PLANE_ANCHORING_DISTANCE
            }?.hitPose
            if (planeHitPose != null) return planeHitPose
            val ray = ar.scene.camera.screenPointToRay(centerX, centerY)
            val point = ray.getPoint(DEFAULT_POSE_DISTANCE)
            return Pose.makeTranslation(point.x, point.y, point.z)
        }
    }

    init {
        this.name = "$name #${newId()}"
        scaleController.apply {
            minScale = 0.25F
            maxScale = 5F
        }
    }

    var onNodeUpdate: ((Nodes) -> Any)? = null

    internal fun anchor(): Anchor? = (parent as? AnchorNode)?.anchor

    override fun getTransformationSystem(): Coordinator = super.getTransformationSystem() as Coordinator

    override fun setRenderable(renderable: Renderable?) {
        super.setRenderable(renderable?.apply {
            isShadowCaster = Settings.Shadows.get()
            isShadowReceiver = Settings.Shadows.get()
        })
    }

    override fun onUpdate(frameTime: FrameTime) {
        onNodeUpdate?.invoke(this)
    }

    open fun attach(anchor: Anchor, scene: Scene, select: Boolean = false) {
        setParent(AnchorNode(anchor).apply { setParent(scene) })
        if (select) transformationSystem.selectNode(this)
    }

    open fun detach() {
        if (this == transformationSystem.selectedNode) {
            transformationSystem.selectNode(null)
        }
        (parent as? AnchorNode)?.anchor?.detach()
        setParent(null)
    }

    open fun statusIcon(): Int = if (isActive && isEnabled && (parent as? AnchorNode)?.isTracking == true)
        android.R.drawable.presence_online
    else
        android.R.drawable.presence_invisible

}

sealed class MaterialNode(
    name: String,
    val properties: MaterialProperties,
    coordinator: Coordinator
) : Nodes(name, coordinator) {

    init {
        update()
    }

    fun update(block: (MaterialProperties.() -> Unit) = {}) {
        block(properties)
        properties.applyTo(renderable?.material ?: return)
    }

}

class Sphere(
    context: Context,
    properties: MaterialProperties,
    coordinator: Coordinator
) : MaterialNode("Sphere", properties, coordinator) {

    companion object {
        private const val RADIUS = 0.05F
        private val CENTER = Vector3(0F, RADIUS, 0F)
    }

    init {
        val color = properties.color.toArColor()
        makeOpaqueWithColor(context, color)
            .thenAccept { renderable = ShapeFactory.makeSphere(RADIUS, CENTER, it) }
    }

}

class Cylinder(
    context: Context,
    properties: MaterialProperties,
    coordinator: Coordinator
) : MaterialNode("Cylinder", properties, coordinator) {

    companion object {
        const val RADIUS = 0.05F
        const val HEIGHT = RADIUS * 2
        val CENTER = Vector3(0F, HEIGHT / 2, 0F)
    }

    init {
        val color = properties.color.toArColor()
        makeOpaqueWithColor(context, color)
            .thenAccept { renderable = ShapeFactory.makeCylinder(RADIUS, HEIGHT, CENTER, it) }
    }

}

class Cube(
    context: Context,
    properties: MaterialProperties,
    coordinator: Coordinator
) : MaterialNode("Cube", properties, coordinator) {

    companion object {
        private const val SIZE = 0.1F
        private val CENTER = Vector3(0F, SIZE / 2, 0F)
    }

    init {
        val color = properties.color.toArColor()
        makeOpaqueWithColor(context, color)
            .thenAccept { renderable = ShapeFactory.makeCube(Vector3.one().scaled(SIZE), CENTER, it) }
    }

}

class Layout(
    context: Context,
    coordinator: Coordinator
) : Nodes("Layout", coordinator) {

    companion object {
        private const val HEIGHT = 0.3F
    }

    init {
        ViewRenderable.builder()
            .setView(ContextThemeWrapper(context.applicationContext, R.style.Theme_MaterialComponents), R.layout.view_renderable_layout)
            .setSizer(FixedHeightViewSizer(HEIGHT)).build()
            .thenAccept { renderable = it }
    }

    override fun setRenderable(renderable: Renderable?) {
        super.setRenderable(renderable)
        renderable?.apply {
            isShadowCaster = false
            isShadowReceiver = false
        }
    }

}

class Andy(
    context: Context,
    coordinator: Coordinator
) : Nodes("Andy", coordinator) {

    init {
        ModelRenderable.builder()
            .setSource(context, R.raw.andy)
            .build()
            .thenAccept { renderable = it }
    }

}

typealias CollisionPlane = com.google.ar.sceneform.collision.Plane

class Drawing(
    val isFromTouch: Boolean,
    private val plane: CollisionPlane?,
    properties: MaterialProperties,
    coordinator: Coordinator
) : MaterialNode("Drawing", properties, coordinator) {

    companion object {

        private const val RADIUS = 0.005F
        private const val PLANE_ANCHORING_DISTANCE = 2F
        private const val DEFAULT_DRAWING_DISTANCE = 0.5F

        private fun hit(frame: Frame, x: Float, y: Float): HitResult? {
            return frame.hitTest(x, y).firstOrNull {
                (it.trackable as? Plane)?.isPoseInPolygon(it.hitPose) == true && it.distance <= PLANE_ANCHORING_DISTANCE
            }
        }

        private fun pose(camera: Camera, x: Float, y: Float): Pose {
            val ray = camera.screenPointToRay(x, y)
            val point = ray.getPoint(DEFAULT_DRAWING_DISTANCE)
            return Pose.makeTranslation(point.x, point.y, point.z)
        }

        private fun plane(hitResult: HitResult?): CollisionPlane? {
            return (hitResult?.trackable as? Plane)?.let {
                val pose = it.centerPose
                val normal = Quaternion.rotateVector(pose.rotation(), Vector3.up())
                CollisionPlane(pose.translation(), normal)
            }
        }

        fun create(x: Float, y: Float, fromTouch: Boolean, properties: MaterialProperties, ar: ArSceneView, coordinator: Coordinator): Drawing? {
            val context = ar.context
            val session = ar.session ?: return null
            val scene = ar.scene ?: return null
            val frame = ar.arFrame ?: return null
            if (frame.camera.trackingState != TRACKING) return null

            val hit = hit(frame, x, y)
            val pose = hit?.hitPose ?: pose(scene.camera, x, y)
            val plane = plane(hit)
            val anchor = hit?.createAnchor() ?: session.createAnchor(pose)

            return Drawing(fromTouch, plane, properties, coordinator).apply {
                makeOpaqueWithColor(context, properties.color.toArColor()).thenAccept { material = it }
                attach(anchor, scene)
                extend(x, y)
            }
        }
    }

    private val line = LineSimplifier()
    private var material: Material? = null
        set(value) {
            field = value?.apply { properties.applyTo(this) }
            render()
        }

    private fun append(pointInWorld: Vector3) {
        val pointInLocal = (parent as AnchorNode).worldToLocalPoint(pointInWorld)
        line.append(pointInLocal)
        render()
    }

    private fun render() {
        val definition = ExtrudedCylinder.makeExtrudedCylinder(RADIUS, line.points, material ?: return) ?: return
        if (renderable == null) {
            renderable = ModelRenderable.builder().setSource(definition).build().join()
        } else {
            renderable?.updateFromDefinition(definition)
        }
    }

    fun extend(x: Float, y: Float) {
        val ray = scene?.camera?.screenPointToRay(x, y) ?: return
        if (plane != null) {
            val rayHit = RayHit()
            if (plane.rayIntersection(ray, rayHit)) {
                append(rayHit.point)
            }
        } else {
            append(ray.getPoint(DEFAULT_DRAWING_DISTANCE))
        }
    }

    fun deleteIfEmpty() = if (line.points.size < 2) detach() else Unit

}

class Link(
    context: Context,
    uri: Uri,
    coordinator: Coordinator
) : Nodes("Link", coordinator) {

    companion object {

        fun warmup(_context: Context, uri: Uri): CompletableFuture<ModelRenderable> {
            val context = _context.applicationContext
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
    }

    init {
        warmup(context, uri).thenAccept { renderable = it }
    }
}

class Augmented(
    context: Context,
    private val image: AugmentedImage,
    coordinator: Coordinator
) : Nodes("Augmented image", coordinator) {

    companion object {

        private val references: MutableMap<AugmentedImage, Nodes> = mutableMapOf()

        fun target(context: Context) = try {
            context.assets.open("augmented_image_target.png")
        } catch (e: Exception) {
            null
        }?.let { BitmapFactory.decodeStream(it) }

        fun update(context: Context, image: AugmentedImage, coordinator: Coordinator): Augmented? {
            val node = references[image]
            when (image.trackingState) {
                TRACKING -> if (node == null && image.trackingMethod == FULL_TRACKING) {
                    return Augmented(context, image, coordinator)
                }
                STOPPED -> node?.detach()
                PAUSED -> Unit
                else -> Unit
            }
            return null
        }

    }

    init {
        ModelRenderable.builder()
            .setSource(context, R.raw.rocket)
            .build()
            .thenAccept {
                renderable = it
            }
    }

    override fun attach(anchor: Anchor, scene: Scene, select: Boolean) {
        super.attach(anchor, scene, select)
        references[image] = this
    }

    override fun detach() {
        super.detach()
        references.remove(image)
    }

}

class CloudAnchor(
    context: Context,
    private val session: Session,
    coordinator: Coordinator
) : Nodes("Cloud Anchor", coordinator) {

    private var lastState: Anchor.CloudAnchorState? = null

    companion object {

        fun resolve(id: String, context: Context, ar: ArSceneView, coordinator: Coordinator): CloudAnchor? {
            if (ar.arFrame?.camera?.trackingState != TRACKING) return null
            val session = ar.session ?: return null
            val anchor = session.resolveCloudAnchor(id)
            return CloudAnchor(context, session, coordinator).also { it.attach(anchor, ar.scene) }
        }

    }

    init {
        translationController.isEnabled = false
        rotationController.isEnabled = false
        scaleController.isEnabled = false

        ViewRenderable.builder()
            .setView(context.applicationContext, R.layout.view_renderable_cloud_anchor)
            .build()
            .thenAccept { renderable = it }
    }

    fun id(): String? = anchor()?.cloudAnchorId.takeUnless { it.isNullOrBlank() }

    fun state() = anchor()?.cloudAnchorState

    override fun attach(anchor: Anchor, scene: Scene, select: Boolean) {
        super.attach(anchor, scene, select)
        if (anchor.cloudAnchorState == NONE) {
            (parent as? AnchorNode)?.apply {
                this.anchor?.detach()
                this.anchor = session.hostCloudAnchor(anchor)
            }
        }
    }

    override fun onUpdate(frameTime: FrameTime) {
        super.onUpdate(frameTime)
        state()?.let {
            if (it != lastState) {
                lastState = it
                update(renderable)
            }
        }
    }

    override fun setRenderable(renderable: Renderable?) {
        super.setRenderable(renderable)
        renderable?.apply {
            update(this)
            isShadowCaster = false
            isShadowReceiver = false
        }
    }

    private fun update(renderable: Renderable?) {
        ((renderable as? ViewRenderable)?.view as? ImageView)?.setImageResource(state().icon())
    }

    override fun statusIcon(): Int = when (val state = state()) {
        NONE -> android.R.drawable.presence_invisible
        TASK_IN_PROGRESS -> android.R.drawable.presence_away
        SUCCESS -> super.statusIcon()
        else -> if (state?.isError == true) android.R.drawable.presence_busy else android.R.drawable.presence_invisible
    }

    private fun Anchor.CloudAnchorState?.icon(): Int = when (this) {
        NONE -> R.drawable.ic_cloud_anchor
        TASK_IN_PROGRESS -> R.drawable.ic_cloud_anchor_sync
        SUCCESS -> R.drawable.ic_cloud_anchor_success
        else -> if (this?.isError == true) R.drawable.ic_cloud_anchor_error else R.drawable.ic_cloud_anchor_unknown
    }

    fun copyToClipboard(context: Context) {
        val clip = ClipData.newPlainText(context.getString(R.string.cloud_anchor_id_label), id() ?: return)
        context.getSystemService<ClipboardManager>()?.setPrimaryClip(clip)
        val message = buildSpannedString {
            inSpans(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER)) {
                append(context.getText(R.string.cloud_anchor_id_copied_to_clipboard))
                append("\n")
                bold { append(id()) }
            }
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

}