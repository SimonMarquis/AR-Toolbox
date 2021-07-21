package fr.smarquis.ar_toolbox

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.net.toUri
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.ar.core.*
import com.google.ar.core.Config.AugmentedFaceMode
import com.google.ar.core.Config.CloudAnchorMode
import com.google.ar.core.Config.DepthMode
import com.google.ar.core.Config.FocusMode
import com.google.ar.core.Config.LightEstimationMode
import com.google.ar.core.Config.PlaneFindingMode.*
import com.google.ar.core.Config.UpdateMode
import com.google.ar.core.TrackingFailureReason.*
import com.google.ar.core.TrackingState.*
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.HitTestResult
import com.google.ar.sceneform.rendering.PlaneRenderer
import fr.smarquis.ar_toolbox.databinding.ActivitySceneBinding
import fr.smarquis.ar_toolbox.databinding.DialogInputBinding

class SceneActivity : ArActivity<ActivitySceneBinding>(ActivitySceneBinding::inflate) {

    private val coordinator by lazy { Coordinator(this, ::onArTap, ::onNodeSelected, ::onNodeFocused) }
    private val model: SceneViewModel by viewModels()
    private val settings by lazy { Settings.instance(this) }
    private var drawing: Drawing? = null

    private val setOfMaterialViews by lazy {
        with(bottomSheetNode.body) {
            setOf(
                colorValue, colorLabel,
                metallicValue, metallicLabel,
                roughnessValue, roughnessLabel,
                reflectanceValue, reflectanceLabel
            )
        }
    }
    private val setOfCloudAnchorViews by lazy {
        with(bottomSheetNode.body) {
            setOf(
                cloudAnchorStateLabel, cloudAnchorStateValue,
                cloudAnchorIdLabel, cloudAnchorIdValue
            )
        }
    }
    private val setOfMeasureViews by lazy {
        with(bottomSheetNode) {
            setOf(
                header.undo,
                body.measureLabel, body.measureValue
            )
        }
    }

    override val arSceneView: ArSceneView get() = binding.arSceneView

    override val recordingIndicator: ImageView get() = bottomSheetScene.header.recording

    private val bottomSheetScene get() = binding.bottomSheetScene

    private val bottomSheetNode get() = binding.bottomSheetNode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initSceneBottomSheet()
        initNodeBottomSheet()
        initAr()
        initWithIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        initWithIntent(intent)
    }

    override fun onBackPressed() {
        if (coordinator.selectedNode != null) {
            coordinator.selectNode(null)
        } else {
            super.onBackPressed()
        }
    }

    override fun config(session: Session): Config = Config(session).apply {
        lightEstimationMode = LightEstimationMode.DISABLED
        planeFindingMode = HORIZONTAL_AND_VERTICAL
        updateMode = UpdateMode.LATEST_CAMERA_IMAGE
        cloudAnchorMode = CloudAnchorMode.ENABLED
        augmentedImageDatabase = AugmentedImageDatabase(session).apply {
            Augmented.target(this@SceneActivity)?.let { addImage("augmented", it) }
        }
        augmentedFaceMode = AugmentedFaceMode.DISABLED
        focusMode = FocusMode.AUTO
        if (session.isDepthModeSupported(DepthMode.AUTOMATIC)) {
            depthMode = DepthMode.AUTOMATIC
        }
    }

    override fun onArResumed() {
        bottomSheetScene.behavior().update(state = STATE_EXPANDED, isHideable = false)
        bottomSheetScene.body.cameraValue.text = arSceneView.session?.cameraConfig?.format(this)
    }

    private fun initWithIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.let {
            Toast.makeText(this, it.toString(), Toast.LENGTH_SHORT).show()
            selectExternalModel(it.toString())
            this.intent = null
        }
    }

    private fun initSceneBottomSheet() = with(bottomSheetScene) {
        behavior().state = STATE_HIDDEN
        header.root.setOnClickListener { behavior().toggle() }
        header.add.setOnClickListener {
            val session = arSceneView.session
            val camera = arSceneView.arFrame?.camera ?: return@setOnClickListener
            if (session == null || camera.trackingState != TRACKING) return@setOnClickListener
            createNodeAndAddToScene(anchor = { session.createAnchor(Nodes.defaultPose(arSceneView)) }, focus = false)
        }

        initPopupMenu(
            anchor = header.more,
            menu = R.menu.menu_scene,
            onClick = {
                when (it.itemId) {
                    R.id.menu_item_resolve_cloud_anchor -> promptCloudAnchorId()
                    R.id.menu_item_clean_up_scene -> arSceneView.scene.callOnHierarchy { node -> (node as? Nodes)?.detach() }
                    R.id.menu_item_sunlight -> settings.sunlight.toggle(it, arSceneView)
                    R.id.menu_item_shadows -> settings.shadows.toggle(it, arSceneView)
                    R.id.menu_item_plane_renderer -> settings.planes.toggle(it, arSceneView)
                    R.id.menu_item_selection_visualizer -> settings.selection.toggle(it, coordinator.selectionVisualizer)
                    R.id.menu_item_reticle -> settings.reticle.toggle(it, arSceneView)
                    R.id.menu_item_point_cloud -> settings.pointCloud.toggle(it, arSceneView)
                }
                when (it.itemId) {
                    R.id.menu_item_sunlight, R.id.menu_item_shadows, R.id.menu_item_plane_renderer, R.id.menu_item_selection_visualizer, R.id.menu_item_reticle, R.id.menu_item_point_cloud -> false
                    else -> true
                }
            },
            onUpdate = {
                findItem(R.id.menu_item_clean_up_scene).isEnabled = arSceneView.scene.findInHierarchy { it is Nodes } != null
                settings.apply {
                    sunlight.applyTo(findItem(R.id.menu_item_sunlight))
                    shadows.applyTo(findItem(R.id.menu_item_shadows))
                    planes.applyTo(findItem(R.id.menu_item_plane_renderer))
                    selection.applyTo(findItem(R.id.menu_item_selection_visualizer))
                    reticle.applyTo(findItem(R.id.menu_item_reticle))
                    pointCloud.applyTo(findItem(R.id.menu_item_point_cloud))
                }
            }
        )

        model.selection.observe(this@SceneActivity) {
            body.apply {
                sphere.isSelected = it == Sphere::class
                cylinder.isSelected = it == Cylinder::class
                cube.isSelected = it == Cube::class
                measure.isSelected = it == Measure::class
                view.isSelected = it == Layout::class
                andy.isSelected = it == Andy::class
                video.isSelected = it == Video::class
                drawing.isSelected = it == Drawing::class
                link.isSelected = it == Link::class
                cloudAnchor.isSelected = it == CloudAnchor::class
            }
            header.add.requestDisallowInterceptTouchEvent = it == Drawing::class
        }

        body.apply {
            sphere.setOnClickListener { model.selection.value = Sphere::class }
            cylinder.setOnClickListener { model.selection.value = Cylinder::class }
            cube.setOnClickListener { model.selection.value = Cube::class }
            view.setOnClickListener { model.selection.value = Layout::class }
            drawing.setOnClickListener { model.selection.value = Drawing::class }
            measure.setOnClickListener { model.selection.value = Measure::class }
            andy.setOnClickListener { model.selection.value = Andy::class }
            video.setOnClickListener { model.selection.value = Video::class }
            link.setOnClickListener { promptExternalModel() }
            cloudAnchor.setOnClickListener { model.selection.value = CloudAnchor::class }
            colorValue.setOnColorChangeListener { color ->
                arSceneView.planeRenderer.material?.thenAccept {
                    it.setFloat3(PlaneRenderer.MATERIAL_COLOR, color.toArColor())
                }
                settings.pointCloud.updateMaterial(arSceneView) { this.color = color }
                settings.reticle.updateMaterial(arSceneView) { this.color = color }
            }
            colorValue.post { colorValue.setColor(MaterialProperties.DEFAULT.color) }
        }
    }

    private fun initNodeBottomSheet() = with(bottomSheetNode) {
        behavior().apply {
            skipCollapsed = true
            addBottomSheetCallback(object : BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    bottomSheet.requestLayout()
                    if (newState == STATE_HIDDEN) {
                        coordinator.selectNode(null)
                    }
                }
            })
            state = STATE_HIDDEN
        }
        header.apply {
            root.setOnClickListener { coordinator.selectNode(null) }
            copy.setOnClickListener { (coordinator.focusedNode as? CloudAnchor)?.copyToClipboard(this@SceneActivity) }
            playPause.setOnClickListener { (coordinator.focusedNode as? Video)?.toggle() }
            delete.setOnClickListener { coordinator.focusedNode?.detach() }
            undo.setOnClickListener { (coordinator.focusedNode as? Measure)?.undo() }
        }

        body.apply {
            colorValue.setOnColorChangeListener { focusedMaterialNode()?.update { color = it } }
            metallicValue.progress = MaterialProperties.DEFAULT.metallic
            metallicValue.setOnSeekBarChangeListener(SimpleSeekBarChangeListener { focusedMaterialNode()?.update { metallic = it } })
            roughnessValue.progress = MaterialProperties.DEFAULT.roughness
            roughnessValue.setOnSeekBarChangeListener(SimpleSeekBarChangeListener { focusedMaterialNode()?.update { roughness = it } })
            reflectanceValue.progress = MaterialProperties.DEFAULT.reflectance
            reflectanceValue.setOnSeekBarChangeListener(SimpleSeekBarChangeListener { focusedMaterialNode()?.update { reflectance = it } })
        }
    }

    private fun focusedMaterialNode() = (coordinator.focusedNode as? MaterialNode)

    private fun materialProperties() = with(bottomSheetNode.body) {
        MaterialProperties(
            color = if (focusedMaterialNode() != null) colorValue.getColor() else bottomSheetScene.body.colorValue.getColor(),
            metallic = metallicValue.progress,
            roughness = roughnessValue.progress,
            reflectance = reflectanceValue.progress
        )
    }

    private fun initAr() = with(arSceneView) {
        scene.addOnUpdateListener { onArUpdate() }
        scene.addOnPeekTouchListener { hitTestResult, motionEvent ->
            coordinator.onTouch(hitTestResult, motionEvent)
            if (shouldHandleDrawing(motionEvent, hitTestResult)) {
                val x = motionEvent.x
                val y = motionEvent.y
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> drawing = Drawing.create(x, y, true, materialProperties(), this, coordinator, settings)
                    MotionEvent.ACTION_MOVE -> drawing?.extend(x, y)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drawing = drawing?.deleteIfEmpty().let { null }
                }
            }
        }
        settings.apply {
            sunlight.applyTo(this@with)
            shadows.applyTo(this@with)
            planes.applyTo(this@with)
            selection.applyTo(coordinator.selectionVisualizer)
            reticle.initAndApplyTo(this@with)
            pointCloud.initAndApplyTo(this@with)
        }
    }

    private fun shouldHandleDrawing(motionEvent: MotionEvent? = null, hitTestResult: HitTestResult? = null): Boolean {
        if (model.selection.value != Drawing::class) return false
        if (coordinator.selectedNode?.isTransforming == true) return false
        if (arSceneView.arFrame?.camera?.trackingState != TRACKING) return false
        if (motionEvent?.action == MotionEvent.ACTION_DOWN && hitTestResult?.node != null) return false
        return true
    }

    private fun promptExternalModel() {
        AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialog))
            .setItems(R.array.models_labels) { _, i ->
                if (i == 0) {
                    promptExternalModelUri()
                } else {
                    selectExternalModel(resources.getStringArray(R.array.models_uris)[i])
                }
            }
            .create()
            .show()
    }

    private fun prompt(block: DialogInputBinding.(AlertDialog.Builder) -> Unit) =
        DialogInputBinding.inflate(LayoutInflater.from(ContextThemeWrapper(this, R.style.AlertDialog)), null, false).apply {
            block(AlertDialog.Builder(root.context).setView(root))
        }

    private fun promptExternalModelUri() = prompt { builder ->
        layout.hint = getText(R.string.model_link_custom_hint)
        value.inputType = InputType.TYPE_TEXT_VARIATION_URI
        value.setText(model.externalModelUri.value.takeUnless { it in resources.getStringArray(R.array.models_uris) })
        builder.setPositiveButton(android.R.string.ok) { _, _ -> selectExternalModel(value.text.toString()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .setCancelable(false)
            .show()
        value.requestFocus()
    }

    private fun selectExternalModel(value: String) {
        model.externalModelUri.value = value
        model.selection.value = Link::class
        Link.warmup(this, value.toUri())
    }

    private fun promptCloudAnchorId() = prompt { builder ->
        layout.hint = getText(R.string.cloud_anchor_id_hint)
        value.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        val dialog = builder.setPositiveButton(R.string.cloud_anchor_id_resolve) { _, _ ->
            CloudAnchor.resolve(value.text.toString(), applicationContext, arSceneView, coordinator, settings)?.also {
                coordinator.focusNode(it)
            }
        }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .setCancelable(false)
            .show()
        value.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = !s.isNullOrBlank()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        value.text = value.text
        value.requestFocus()
    }

    private fun onArTap(motionEvent: MotionEvent) {
        val frame = arSceneView.arFrame ?: return
        if (frame.camera.trackingState != TRACKING) {
            coordinator.selectNode(null)
            return
        }

        frame.hitTest(motionEvent).firstOrNull {
            val trackable = it.trackable
            when {
                trackable is Plane && trackable.isPoseInPolygon(it.hitPose) -> true
                trackable is DepthPoint -> true
                trackable is Point -> true
                else -> false
            }
        }?.let { createNodeAndAddToScene(anchor = { it.createAnchor() }) } ?: coordinator.selectNode(null)
    }

    private fun createNodeAndAddToScene(anchor: () -> Anchor, focus: Boolean = true) {
        when (model.selection.value) {
            Sphere::class -> Sphere(this, materialProperties(), coordinator, settings)
            Cylinder::class -> Cylinder(this, materialProperties(), coordinator, settings)
            Cube::class -> Cube(this, materialProperties(), coordinator, settings)
            Layout::class -> Layout(this, coordinator, settings)
            Andy::class -> Andy(this, coordinator, settings)
            Video::class -> Video(this, coordinator, settings)
            Measure::class -> Measure(this, materialProperties(), coordinator, settings)
            Link::class -> Link(this, model.externalModelUri.value.orEmpty().toUri(), coordinator, settings)
            CloudAnchor::class -> CloudAnchor(this, arSceneView.session ?: return, coordinator, settings)
            else -> return
        }.attach(anchor(), arSceneView.scene, focus)
    }

    private fun onArUpdate() {
        val frame = arSceneView.arFrame
        val camera = frame?.camera
        val state = camera?.trackingState
        val reason = camera?.trackingFailureReason

        onArUpdateStatusText(state, reason)
        onArUpdateStatusIcon(state, reason)
        onArUpdateBottomSheet(state)
        onArUpdateDrawing()
        onArUpdateAugmentedImages()
    }

    private fun onArUpdateStatusText(state: TrackingState?, reason: TrackingFailureReason?) =
        bottomSheetScene.header.label.setText(
            when (state) {
                TRACKING -> R.string.tracking_success
                PAUSED -> when (reason) {
                    NONE -> R.string.tracking_failure_none
                    BAD_STATE -> R.string.tracking_failure_bad_state
                    INSUFFICIENT_LIGHT -> R.string.tracking_failure_insufficient_light
                    EXCESSIVE_MOTION -> R.string.tracking_failure_excessive_motion
                    INSUFFICIENT_FEATURES -> R.string.tracking_failure_insufficient_features
                    CAMERA_UNAVAILABLE -> R.string.tracking_failure_camera_unavailable
                    null -> 0
                }
                STOPPED -> R.string.tracking_stopped
                null -> 0
            }
        )

    private fun onArUpdateStatusIcon(state: TrackingState?, reason: TrackingFailureReason?) =
        bottomSheetScene.header.status.setImageResource(
            when (state) {
                TRACKING -> android.R.drawable.presence_online
                PAUSED -> when (reason) {
                    NONE -> android.R.drawable.presence_invisible
                    BAD_STATE, CAMERA_UNAVAILABLE -> android.R.drawable.presence_busy
                    INSUFFICIENT_LIGHT, EXCESSIVE_MOTION, INSUFFICIENT_FEATURES -> android.R.drawable.presence_away
                    null -> 0
                }
                STOPPED -> android.R.drawable.presence_offline
                null -> 0
            }
        )

    private fun onArUpdateBottomSheet(state: TrackingState?) = with(bottomSheetScene) {
        header.add.isEnabled = state == TRACKING
        when (behavior().state) {
            STATE_HIDDEN, STATE_COLLAPSED -> Unit
            else -> body.apply {
                arSceneView.arFrame?.camera?.pose.let {
                    poseTranslationValue.text = it.formatTranslation(this@SceneActivity)
                    poseRotationValue.text = it.formatRotation(this@SceneActivity)
                }
                sceneValue.text = arSceneView.session?.format(this@SceneActivity)
            }
        }
    }

    private fun onArUpdateDrawing() {
        if (shouldHandleDrawing()) {
            val x = arSceneView.width / 2F
            val y = arSceneView.height / 2F
            val pressed = bottomSheetScene.header.add.isPressed
            when {
                pressed && drawing == null -> drawing = Drawing.create(x, y, false, materialProperties(), arSceneView, coordinator, settings)
                pressed && drawing?.isFromTouch == false -> drawing?.extend(x, y)
                !pressed && drawing?.isFromTouch == false -> drawing = drawing?.deleteIfEmpty().let { null }
                else -> Unit
            }
        }
    }

    private fun onArUpdateAugmentedImages() {
        arSceneView.arFrame?.getUpdatedTrackables(AugmentedImage::class.java)?.forEach {
            Augmented.update(this, it, coordinator, settings)?.apply {
                attach(it.createAnchor(it.centerPose), arSceneView.scene)
            }
        }
    }

    private fun onNodeUpdate(node: Nodes) = with(bottomSheetNode) {
        when {
            node != coordinator.selectedNode || node != coordinator.focusedNode || behavior().state == STATE_HIDDEN -> Unit
            else -> {
                with(header) {
                    status.setImageResource(node.statusIcon())
                    distance.text = arSceneView.arFrame?.camera.formatDistance(this@SceneActivity, node)
                    copy.isEnabled = (node as? CloudAnchor)?.id() != null
                    playPause.isActivated = (node as? Video)?.isPlaying() == true
                    delete.isEnabled = !node.isTransforming
                }
                with(body) {
                    positionValue.text = node.worldPosition.format(this@SceneActivity)
                    rotationValue.text = node.worldRotation.format(this@SceneActivity)
                    scaleValue.text = node.worldScale.format(this@SceneActivity)
                    cloudAnchorStateValue.text = (node as? CloudAnchor)?.state()?.name
                    cloudAnchorIdValue.text = (node as? CloudAnchor)?.let { it.id() ?: "…" }
                    measureValue.text = (node as? Measure)?.formatMeasure()
                }
            }
        }
    }

    private fun onNodeSelected(old: Nodes? = coordinator.selectedNode, new: Nodes?) {
        old?.onNodeUpdate = null
        new?.onNodeUpdate = ::onNodeUpdate
    }

    private fun onNodeFocused(node: Nodes?) {
        val nodeSheetBehavior = bottomSheetNode.behavior()
        val sceneBehavior = bottomSheetScene.behavior()
        when (node) {
            null -> {
                nodeSheetBehavior.state = STATE_HIDDEN
                if ((bottomSheetScene.root.tag as? Boolean) == true) {
                    bottomSheetScene.root.tag = false
                    sceneBehavior.state = STATE_EXPANDED
                }
            }
            coordinator.selectedNode -> {
                with(bottomSheetNode.header) {
                    name.text = node.name
                    copy.visibility = if (node is CloudAnchor) VISIBLE else GONE
                    playPause.visibility = if (node is Video) VISIBLE else GONE
                }
                with(bottomSheetNode.body) {
                    (node as? MaterialNode)?.properties?.let {
                        colorValue.setColor(it.color)
                        metallicValue.progress = it.metallic
                        roughnessValue.progress = it.roughness
                        reflectanceValue.progress = it.reflectance
                    }
                }
                val materialVisibility = if (node is MaterialNode) VISIBLE else GONE
                setOfMaterialViews.forEach { it.visibility = materialVisibility }
                val cloudAnchorVisibility = if (node is CloudAnchor) VISIBLE else GONE
                setOfCloudAnchorViews.forEach { it.visibility = cloudAnchorVisibility }
                val measureVisibility = if (node is Measure) VISIBLE else GONE
                setOfMeasureViews.forEach { it.visibility = measureVisibility }
                nodeSheetBehavior.state = STATE_EXPANDED
                if (sceneBehavior.state != STATE_COLLAPSED) {
                    sceneBehavior.state = STATE_COLLAPSED
                    bottomSheetScene.root.tag = true
                }
            }
            else -> Unit
        }
    }

}
