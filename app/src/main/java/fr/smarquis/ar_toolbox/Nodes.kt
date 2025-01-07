package fr.smarquis.ar_toolbox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.text.Layout
import android.text.style.AlignmentSpan
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.google.ar.core.Anchor
import com.google.ar.core.Anchor.CloudAnchorState.NONE
import com.google.ar.core.Anchor.CloudAnchorState.SUCCESS
import com.google.ar.core.Anchor.CloudAnchorState.TASK_IN_PROGRESS
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImage.TrackingMethod.FULL_TRACKING
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState.PAUSED
import com.google.ar.core.TrackingState.STOPPED
import com.google.ar.core.TrackingState.TRACKING
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Camera
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.HitTestResult
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.NodeParent
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.assets.RenderableSource
import com.google.ar.sceneform.assets.RenderableSource.SourceType.GLB
import com.google.ar.sceneform.assets.RenderableSource.SourceType.GLTF2
import com.google.ar.sceneform.collision.RayHit
import com.google.ar.sceneform.collision.Sphere
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.ExternalTexture
import com.google.ar.sceneform.rendering.FixedHeightViewSizer
import com.google.ar.sceneform.rendering.Material
import com.google.ar.sceneform.rendering.MaterialFactory.makeOpaqueWithColor
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.BaseTransformableNode
import com.google.ar.sceneform.ux.TransformableNode
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass
import kotlin.text.Typography.leftGuillemet
import kotlin.text.Typography.rightGuillemet

sealed class Nodes(
    name: String,
    coordinator: Coordinator,
    private val settings: Settings,
) : TransformableNode(coordinator) {

    interface FacingCamera

    companion object {

        private const val PLANE_ANCHORING_DISTANCE = 2F
        private const val DEFAULT_POSE_DISTANCE = 2F

        private val IDS: MutableMap<KClass<*>, AtomicLong> = mutableMapOf()

        fun Any.newId(): Long = IDS.getOrElse(this::class) { AtomicLong().also { IDS[this::class] = it } }.incrementAndGet()

        fun defaultPose(ar: ArSceneView): Pose {
            val centerX = ar.width / 2F
            val centerY = ar.height / 2F
            val hits = ar.arFrame?.hitTest(centerX, centerY)
            val planeHitPose = hits?.firstOrNull {
                when (val trackable = it.trackable) {
                    is Plane -> trackable.isPoseInPolygon(it.hitPose) && it.distance <= PLANE_ANCHORING_DISTANCE
                    is DepthPoint, is Point -> it.distance <= DEFAULT_POSE_DISTANCE
                    else -> false
                }
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
        @Suppress("LeakingThis")
        if (this is FacingCamera) rotationController.isEnabled = false
    }

    var onNodeUpdate: ((Nodes) -> Any)? = null

    internal fun anchor(): Anchor? = (parent as? AnchorNode)?.anchor

    override fun getTransformationSystem(): Coordinator = super.getTransformationSystem() as Coordinator

    override fun setRenderable(renderable: Renderable?) {
        super.setRenderable(
            renderable?.apply {
                isShadowCaster = settings.shadows.get()
                isShadowReceiver = settings.shadows.get()
            },
        )
    }

    override fun onUpdate(frameTime: FrameTime) {
        onNodeUpdate?.invoke(this)
        if (this is FacingCamera) {
            facingCamera()
        }
    }

    private fun facingCamera() {
        // Buggy when dragging because TranslationController already handles it's own rotation on each update.
        if (isTransforming) return // Prevent infinite loop
        val camera = scene?.camera ?: return
        val direction = Vector3.subtract(camera.worldPosition, worldPosition)
        worldRotation = Quaternion.lookRotation(direction, Vector3.up())
    }

    open fun attach(anchor: Anchor, scene: Scene, focus: Boolean = false) {
        setParent(AnchorNode(anchor).apply { setParent(scene) })
        if (focus) {
            transformationSystem.focusNode(this)
        }
    }

    open fun detach() {
        if (this == transformationSystem.selectedNode) {
            transformationSystem.selectNode(selectionContinuation())
        }
        (parent as? AnchorNode)?.anchor?.detach()
        setParent(null)
    }

    open fun selectionContinuation(): BaseTransformableNode? = null

    open fun statusIcon(): Int = if (isActive && isEnabled && (parent as? AnchorNode)?.isTracking == true) {
        android.R.drawable.presence_online
    } else {
        android.R.drawable.presence_invisible
    }

    override fun onTap(hitTestResult: HitTestResult?, motionEvent: MotionEvent?) {
        super.onTap(hitTestResult, motionEvent)
        if (isTransforming) return // ignored when dragging over a small distance
        transformationSystem.focusNode(this)
    }
}

sealed class MaterialNode(
    name: String,
    val properties: MaterialProperties,
    coordinator: Coordinator,
    settings: Settings,
) : Nodes(name, coordinator, settings) {

    init {
        update()
    }

    fun update(block: (MaterialProperties.() -> Unit) = {}) {
        properties.update(renderable?.material, block)
    }
}

class Sphere(
    context: Context,
    properties: MaterialProperties,
    coordinator: Coordinator,
    settings: Settings,
) : MaterialNode("Sphere", properties, coordinator, settings) {

    companion object {
        private const val RADIUS = 0.05F
        private val CENTER = Vector3(0F, RADIUS, 0F)
    }

    init {
        val color = properties.color.toArColor()
        makeOpaqueWithColor(context.applicationContext, color)
            .thenAccept { renderable = ShapeFactory.makeSphere(RADIUS, CENTER, it) }
    }
}

class Cylinder(
    context: Context,
    properties: MaterialProperties,
    coordinator: Coordinator,
    settings: Settings,
) : MaterialNode("Cylinder", properties, coordinator, settings) {

    companion object {
        const val RADIUS = 0.05F
        const val HEIGHT = RADIUS * 2
        val CENTER = Vector3(0F, HEIGHT / 2, 0F)
    }

    init {
        val color = properties.color.toArColor()
        makeOpaqueWithColor(context.applicationContext, color)
            .thenAccept { renderable = ShapeFactory.makeCylinder(RADIUS, HEIGHT, CENTER, it) }
    }
}

class Cube(
    context: Context,
    properties: MaterialProperties,
    coordinator: Coordinator,
    settings: Settings,
) : MaterialNode("Cube", properties, coordinator, settings) {

    companion object {
        private const val SIZE = 0.1F
        private val CENTER = Vector3(0F, SIZE / 2, 0F)
    }

    init {
        val color = properties.color.toArColor()
        makeOpaqueWithColor(context.applicationContext, color)
            .thenAccept { renderable = ShapeFactory.makeCube(Vector3.one().scaled(SIZE), CENTER, it) }
    }
}

class Measure(
    private val context: Context,
    properties: MaterialProperties,
    coordinator: Coordinator,
    settings: Settings,
) : MaterialNode("Measure", properties, coordinator, settings) {

    companion object {
        private const val SPHERE_RADIUS = 0.01f
        private const val SPHERE_COLLISION_RADIUS = SPHERE_RADIUS * 5
        private const val CYLINDER_RADIUS = SPHERE_RADIUS * 0.5F
    }

    private var previous: Measure? = null
    private var next: Measure? = null
    private var join: Join? = null

    init {
        rotationController.isEnabled = false
        scaleController.isEnabled = false
        makeOpaqueWithColor(context.applicationContext, properties.color.toArColor()).thenAccept {
            renderable = ShapeFactory.makeSphere(SPHERE_RADIUS, Vector3.zero(), it).apply { collisionShape = Sphere(SPHERE_COLLISION_RADIUS, Vector3.zero()) }
            join?.applyMaterial(it)
        }
        linkTo(lastSelected())
    }

    private fun linkTo(to: Measure?) {
        if (to == null) return
        join?.let { removeChild(it) }
        previous = to.apply { next = this@Measure }
        join = Join(to).apply {
            this@Measure.addChild(this)
            this@Measure.renderable?.material?.let { applyMaterial(it) }
        }
    }

    private fun unlink() {
        previous?.next = null
        next?.run {
            previous = null
            removeChild(join)
        }
    }

    private fun last(): Measure = next?.last() ?: this

    private fun lastSelected(): Measure? = (transformationSystem.selectedNode as? Measure)?.last()

    override fun selectionContinuation(): BaseTransformableNode? = previous ?: next

    override fun setParent(parent: NodeParent?) {
        super.setParent(parent)
        if (parent == null) unlink()
    }

    override fun attach(anchor: Anchor, scene: Scene, focus: Boolean) {
        super.attach(anchor, scene, focus)
        transformationSystem.selectNode(this)
    }

    override fun detach(): Unit = when (val last = last()) {
        /* detach() self and propagate to the previous */
        this -> super.detach().also { previous?.detach() }
        /* Run detach() on the last */
        else -> last.detach()
    }

    fun undo(): Unit = super.detach().also { next?.linkTo(previous) }

    fun formatMeasure(): String = when {
        previous == null && next == null -> "…"
        previous == null && next?.next == null -> context.getString(R.string.format_measure_single, "", formatNextDistance())
        next == null && previous?.previous == null -> context.getString(R.string.format_measure_single, formatPreviousDistance(), "")
        else -> context.getString(R.string.format_measure_multiple, formatPreviousDistance(), formatNextDistance(), formatDistance(context, totalMeasurePrevious() + totalMeasureNext()))
    }

    private fun totalMeasurePrevious(): Double = previous?.let { it.totalMeasurePrevious() + distance(this, it) } ?: .0

    private fun totalMeasureNext(): Double = next?.let { distance(this, it) + it.totalMeasureNext() } ?: .0

    private fun formatPreviousDistance(): String = previous?.let { it.formatPreviousDistance() + formatDistance(context, this, it) + " $leftGuillemet " }.orEmpty()

    private fun formatNextDistance(): String = next?.let { " $rightGuillemet " + formatDistance(context, this, it) + it.formatNextDistance() }.orEmpty()

    private inner class Join(private val previous: Node) : Node() {

        private val scale = Vector3.one()

        init {
            setOnTapListener { _, _ ->
                Toast.makeText(context, formatDistance(context, this, previous), Toast.LENGTH_SHORT).apply { setGravity(Gravity.CENTER, 0, 0) }.show()
            }
        }

        override fun onUpdate(frameTime: FrameTime) {
            super.onUpdate(frameTime)
            val start: Vector3 = this@Measure.worldPosition
            val end: Vector3 = previous.worldPosition
            localScale = scale.apply {
                y = distance(start, end).toFloat()
            }
            worldPosition = Vector3.lerp(start, end, 0.5f)
            val direction = Vector3.subtract(start, end).normalized()
            val quaternion = Quaternion.lookRotation(direction, Vector3.up())
            worldRotation = Quaternion.multiply(quaternion, Quaternion.axisAngle(Vector3.right(), 90f))
        }

        fun applyMaterial(material: Material?) {
            renderable = ShapeFactory.makeCylinder(CYLINDER_RADIUS, 1F, Vector3.zero(), material)
        }
    }
}

class Layout(
    context: Context,
    coordinator: Coordinator,
    settings: Settings,
) : Nodes("Layout", coordinator, settings),
    Footprint.Invisible,
    Nodes.FacingCamera {

    companion object {
        private const val HEIGHT = 0.3F
    }

    init {
        ViewRenderable.builder()
            .setView(ContextThemeWrapper(context.applicationContext, com.google.android.material.R.style.Theme_MaterialComponents), R.layout.view_renderable_layout)
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
    coordinator: Coordinator,
    settings: Settings,
) : Nodes("Andy", coordinator, settings) {

    init {
        ModelRenderable.builder()
            .setSource(context.applicationContext, R.raw.andy)
            .build()
            .thenAccept { renderable = it }
    }
}

typealias CollisionPlane = com.google.ar.sceneform.collision.Plane

class Drawing(
    val isFromTouch: Boolean,
    private val plane: CollisionPlane?,
    properties: MaterialProperties,
    coordinator: Coordinator,
    settings: Settings,
) : MaterialNode("Drawing", properties, coordinator, settings) {

    companion object {

        private const val RADIUS = 0.005F
        private const val PLANE_ANCHORING_DISTANCE = 2F
        private const val DEFAULT_DRAWING_DISTANCE = 0.5F

        private fun hit(frame: Frame, x: Float, y: Float): HitResult? = frame.hitTest(x, y).firstOrNull {
            (it.trackable as? Plane)?.isPoseInPolygon(it.hitPose) == true && it.distance <= PLANE_ANCHORING_DISTANCE
        }

        private fun pose(camera: Camera, x: Float, y: Float): Pose {
            val ray = camera.screenPointToRay(x, y)
            val point = ray.getPoint(DEFAULT_DRAWING_DISTANCE)
            return Pose.makeTranslation(point.x, point.y, point.z)
        }

        private fun plane(hitResult: HitResult?): CollisionPlane? = (hitResult?.trackable as? Plane)?.let {
            val pose = it.centerPose
            val normal = Quaternion.rotateVector(pose.rotation(), Vector3.up())
            CollisionPlane(pose.translation(), normal)
        }

        fun create(x: Float, y: Float, fromTouch: Boolean, properties: MaterialProperties, ar: ArSceneView, coordinator: Coordinator, settings: Settings): Drawing? {
            val context = ar.context
            val session = ar.session ?: return null
            val scene = ar.scene ?: return null
            val frame = ar.arFrame ?: return null
            if (frame.camera.trackingState != TRACKING) return null

            val hit = hit(frame, x, y)
            val pose = hit?.hitPose ?: pose(scene.camera, x, y)
            val plane = plane(hit)
            val anchor = hit?.createAnchor() ?: session.createAnchor(pose)

            return Drawing(fromTouch, plane, properties, coordinator, settings).apply {
                makeOpaqueWithColor(context.applicationContext, properties.color.toArColor()).thenAccept { material = it }
                attach(anchor, scene)
                extend(x, y)
            }
        }
    }

    private val line = LineSimplifier()
    private var material: Material? = null
        set(value) {
            field = value?.apply { properties.update(this) }
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
            ModelRenderable.builder().setSource(definition).build().thenAccept { renderable = it }
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
    coordinator: Coordinator,
    settings: Settings,
) : Nodes("Link", coordinator, settings) {

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
    coordinator: Coordinator,
    settings: Settings,
) : Nodes("Augmented image", coordinator, settings) {

    companion object {

        private val references: MutableMap<AugmentedImage, Nodes> = mutableMapOf()

        fun target(context: Context) = try {
            context.applicationContext.assets.open("augmented_image_target.jpg")
        } catch (e: Exception) {
            null
        }?.let { BitmapFactory.decodeStream(it) }

        fun update(context: Context, image: AugmentedImage, coordinator: Coordinator, settings: Settings): Augmented? {
            val node = references[image]
            when (image.trackingState) {
                TRACKING -> if (node == null && image.trackingMethod == FULL_TRACKING) {
                    return Augmented(context.applicationContext, image, coordinator, settings)
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
            .setSource(context.applicationContext, R.raw.rocket)
            .build()
            .thenAccept {
                renderable = it
            }
    }

    override fun attach(anchor: Anchor, scene: Scene, focus: Boolean) {
        super.attach(anchor, scene, focus)
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
    coordinator: Coordinator,
    settings: Settings,
) : Nodes("Cloud Anchor", coordinator, settings) {

    private var lastState: Anchor.CloudAnchorState? = null

    companion object {

        fun resolve(id: String, context: Context, ar: ArSceneView, coordinator: Coordinator, settings: Settings): CloudAnchor? {
            if (ar.arFrame?.camera?.trackingState != TRACKING) return null
            val session = ar.session ?: return null
            val anchor = session.resolveCloudAnchor(id)
            return CloudAnchor(context.applicationContext, session, coordinator, settings).also { it.attach(anchor, ar.scene) }
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

    override fun attach(anchor: Anchor, scene: Scene, focus: Boolean) {
        super.attach(anchor, scene, focus)
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

class Video(
    val context: Context,
    coordinator: Coordinator,
    settings: Settings,
) : Nodes("Video", coordinator, settings),
    MediaPlayer.OnVideoSizeChangedListener {

    private var mediaPlayer: MediaPlayer? = null
    private val texture = ExternalTexture()

    /* Use a child node to keep the video dimensions independent of scaling */
    private val video: Node = Node().apply { setParent(this@Video) }

    init {
        ModelRenderable.builder()
            .setSource(context.applicationContext, R.raw.chroma_key_video)
            .build()
            .thenAccept {
                it.material.setExternalTexture("videoTexture", texture)
                it.material.setFloat4("keyColor", Color(0.1843f, 1.0f, 0.098f))
                video.renderable = it
            }
    }

    override fun onActivate() {
        mediaPlayer = MediaPlayer.create(context.applicationContext, R.raw.video).apply {
            isLooping = true
            setSurface(texture.surface)
            setOnVideoSizeChangedListener(this@Video)
            start()
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun toggle() {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause() else it.start()
        }
    }

    override fun onDeactivate() {
        mediaPlayer?.setOnVideoSizeChangedListener(null)
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onVideoSizeChanged(mp: MediaPlayer, width: Int, height: Int) {
        if (width == 0 || height == 0) return
        mp.setOnVideoSizeChangedListener(null)
        video.localScale = when {
            width > height -> Vector3(1F, height / width.toFloat(), 1F)
            width < height -> Vector3(width / height.toFloat(), 1F, 1F)
            else -> Vector3.one()
        }
    }
}
