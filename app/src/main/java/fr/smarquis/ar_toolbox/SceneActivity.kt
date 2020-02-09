package fr.smarquis.ar_toolbox

import android.annotation.SuppressLint
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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.ar.core.*
import com.google.ar.core.TrackingFailureReason.*
import com.google.ar.core.TrackingState.*
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.HitTestResult
import com.google.ar.sceneform.rendering.PlaneRenderer
import kotlinx.android.synthetic.main.activity_scene.*
import kotlinx.android.synthetic.main.bottom_sheet_node.*
import kotlinx.android.synthetic.main.bottom_sheet_node_body.*
import kotlinx.android.synthetic.main.bottom_sheet_node_header.*
import kotlinx.android.synthetic.main.bottom_sheet_scene.*
import kotlinx.android.synthetic.main.bottom_sheet_scene_body.*
import kotlinx.android.synthetic.main.bottom_sheet_scene_header.*

class SceneActivity : ArActivity(R.layout.activity_scene) {

    private val coordinator by lazy { Coordinator(this, ::onArTap, ::onNodeSelected, ::onNodeFocused) }
    private val model: SceneViewModel by viewModels()
    private val settings by lazy { Settings.instance(this) }
    private var drawing: Drawing? = null

    private val setOfMaterialViews by lazy {
        setOf(
            nodeColorValue, nodeColorLabel,
            nodeMetallicValue, nodeMetallicLabel,
            nodeRoughnessValue, nodeRoughnessLabel,
            nodeReflectanceValue, nodeReflectanceLabel
        )
    }
    private val setOfCloudAnchorViews by lazy {
        setOf(
            nodeCloudAnchorStateLabel, nodeCloudAnchorStateValue,
            nodeCloudAnchorIdLabel, nodeCloudAnchorIdValue
        )
    }

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

    override fun arSceneView(): ArSceneView = arSceneView

    override fun recordingIndicator(): ImageView? = sceneRecording

    override fun config(session: Session): Config = Config(session).apply {
        lightEstimationMode = Config.LightEstimationMode.DISABLED
        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        cloudAnchorMode = Config.CloudAnchorMode.ENABLED
        augmentedImageDatabase = AugmentedImageDatabase(session).apply {
            Augmented.target(this@SceneActivity)?.let { addImage("augmented", it) }
        }
        augmentedFaceMode = Config.AugmentedFaceMode.DISABLED
        focusMode = Config.FocusMode.AUTO
    }

    override fun onArResumed() {
        sceneBottomSheet.behavior().update(state = STATE_EXPANDED, isHideable = false)
    }

    private fun initWithIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.let {
            Toast.makeText(this, it.toString(), Toast.LENGTH_SHORT).show()
            selectExternalModel(it.toString())
            this.intent = null
        }
    }

    private fun initSceneBottomSheet() {
        sceneBottomSheet.behavior().state = STATE_HIDDEN
        sceneHeader.setOnClickListener { sceneBottomSheet.behavior().toggle() }

        sceneAdd.setOnClickListener {
            val session = arSceneView.session
            val camera = arSceneView.arFrame?.camera ?: return@setOnClickListener
            if (session == null || camera.trackingState != TRACKING) return@setOnClickListener
            createNodeAndAddToScene(anchor = { session.createAnchor(Nodes.defaultPose(arSceneView)) }, focus = false)
        }

        initPopupMenu(
            anchor = sceneMore,
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
                settings.sunlight.applyTo(findItem(R.id.menu_item_sunlight))
                settings.shadows.applyTo(findItem(R.id.menu_item_shadows))
                settings.planes.applyTo(findItem(R.id.menu_item_plane_renderer))
                settings.selection.applyTo(findItem(R.id.menu_item_selection_visualizer))
                settings.reticle.applyTo(findItem(R.id.menu_item_reticle))
                settings.pointCloud.applyTo(findItem(R.id.menu_item_point_cloud))
            }
        )

        model.selection.observe(this, androidx.lifecycle.Observer {
            modelSphere.isSelected = it == Sphere::class
            modelCylinder.isSelected = it == Cylinder::class
            modelCube.isSelected = it == Cube::class
            modelView.isSelected = it == Layout::class
            modelAndy.isSelected = it == Andy::class
            modelVideo.isSelected = it == Video::class
            modelDrawing.isSelected = it == Drawing::class
            modelLink.isSelected = it == Link::class
            modelCloudAnchor.isSelected = it == CloudAnchor::class
            sceneAdd.requestDisallowInterceptTouchEvent = it == Drawing::class
        })

        modelSphere.setOnClickListener { model.selection.value = Sphere::class }
        modelCylinder.setOnClickListener { model.selection.value = Cylinder::class }
        modelCube.setOnClickListener { model.selection.value = Cube::class }
        modelView.setOnClickListener { model.selection.value = Layout::class }
        modelDrawing.setOnClickListener { model.selection.value = Drawing::class }
        modelAndy.setOnClickListener { model.selection.value = Andy::class }
        modelVideo.setOnClickListener { model.selection.value = Video::class }
        modelLink.setOnClickListener {
            promptExternalModel()
        }
        modelCloudAnchor.setOnClickListener { model.selection.value = CloudAnchor::class }
        modelCloudAnchor.setOnLongClickListener { promptCloudAnchorId().let { true } }
        colorValue.setOnColorChangeListener { color ->
            arSceneView.planeRenderer.material?.thenAccept {
                it.setFloat3(PlaneRenderer.MATERIAL_COLOR, color.toArColor())
            }
            settings.pointCloud.updateMaterial(arSceneView) { this.color = color }
        }
        colorValue.post { colorValue.setColor(MaterialProperties.DEFAULT.color) }
    }

    private fun initNodeBottomSheet() {
        nodeBottomSheet.behavior().apply {
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
        nodeHeader.setOnClickListener { coordinator.selectNode(null) }
        nodeCopy.setOnClickListener { (coordinator.focusedNode as? CloudAnchor)?.copyToClipboard(this) }
        nodePlayPause.setOnClickListener { (coordinator.focusedNode as? Video)?.toggle() }
        nodeDelete.setOnClickListener { coordinator.focusedNode?.detach() }

        nodeColorValue.setOnColorChangeListener { focusedMaterialNode()?.update { color = it } }
        nodeMetallicValue.progress = MaterialProperties.DEFAULT.metallic
        nodeMetallicValue.setOnSeekBarChangeListener(SimpleSeekBarChangeListener { focusedMaterialNode()?.update { metallic = it } })
        nodeRoughnessValue.progress = MaterialProperties.DEFAULT.roughness
        nodeRoughnessValue.setOnSeekBarChangeListener(SimpleSeekBarChangeListener { focusedMaterialNode()?.update { roughness = it } })
        nodeReflectanceValue.progress = MaterialProperties.DEFAULT.reflectance
        nodeReflectanceValue.setOnSeekBarChangeListener(SimpleSeekBarChangeListener { focusedMaterialNode()?.update { reflectance = it } })
    }

    private fun focusedMaterialNode() = (coordinator.focusedNode as? MaterialNode)

    private fun materialProperties() = MaterialProperties(
        if (focusedMaterialNode() != null) nodeColorValue.getColor() else colorValue.getColor(),
        nodeMetallicValue.progress,
        nodeRoughnessValue.progress,
        nodeReflectanceValue.progress
    )

    private fun initAr() {
        arSceneView.scene.addOnUpdateListener { onArUpdate() }
        arSceneView.scene.addOnPeekTouchListener { hitTestResult, motionEvent ->
            coordinator.onTouch(hitTestResult, motionEvent)
            if (shouldHandleDrawing(motionEvent, hitTestResult)) {
                val x = motionEvent.x
                val y = motionEvent.y
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> drawing = Drawing.create(x, y, true, materialProperties(), arSceneView, coordinator, settings)
                    MotionEvent.ACTION_MOVE -> drawing?.extend(x, y)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drawing = drawing?.deleteIfEmpty().let { null }
                }
            }
        }

        settings.sunlight.applyTo(arSceneView)
        settings.shadows.applyTo(arSceneView)
        settings.planes.applyTo(arSceneView)
        settings.selection.applyTo(coordinator.selectionVisualizer)
        settings.reticle.initAndApplyTo(arSceneView)
        settings.pointCloud.initAndApplyTo(arSceneView)
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

    private fun promptExternalModelUri() {
        val context = ContextThemeWrapper(this, R.style.AlertDialog)
        @SuppressLint("InflateParams")
        val view: View = LayoutInflater.from(context).inflate(R.layout.dialog_input, null)
        view.findViewById<TextInputLayout>(R.id.dialog_input_layout).hint = getText(R.string.model_link_custom_hint)
        val input = view.findViewById<TextInputEditText>(R.id.dialog_input_value)
        input.inputType = InputType.TYPE_TEXT_VARIATION_URI
        input.setText(model.externalModelUri.value.takeUnless { it in resources.getStringArray(R.array.models_uris) })
        AlertDialog.Builder(context)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ -> selectExternalModel(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .setCancelable(false)
            .show()
        input.requestFocus()
    }

    private fun selectExternalModel(value: String) {
        model.externalModelUri.value = value
        model.selection.value = Link::class
        Link.warmup(this, value.toUri())
    }

    private fun promptCloudAnchorId() {
        val context = ContextThemeWrapper(this, R.style.AlertDialog)
        @SuppressLint("InflateParams")
        val view: View = LayoutInflater.from(context).inflate(R.layout.dialog_input, null)
        view.findViewById<TextInputLayout>(R.id.dialog_input_layout).hint = getText(R.string.cloud_anchor_id_hint)
        val input = view.findViewById<TextInputEditText>(R.id.dialog_input_value)
        input.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setPositiveButton(R.string.cloud_anchor_id_resolve) { _, _ ->
                CloudAnchor.resolve(input.text.toString(), this, arSceneView, coordinator, settings)?.also {
                    coordinator.focusNode(it)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .setCancelable(false)
            .show()
        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = !s.isNullOrBlank()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        input.text = input.text
        input.requestFocus()
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

    private fun onArUpdateStatusText(state: TrackingState?, reason: TrackingFailureReason?) {
        sceneStatusLabel.setText(
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
    }

    private fun onArUpdateStatusIcon(state: TrackingState?, reason: TrackingFailureReason?) {
        sceneStatusIcon.setImageResource(
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
    }

    private fun onArUpdateBottomSheet(state: TrackingState?) {
        sceneAdd.isEnabled = state == TRACKING
        when (sceneBottomSheet.behavior().state) {
            STATE_HIDDEN, STATE_COLLAPSED -> Unit
            else -> {
                arSceneView.arFrame?.camera?.pose.let {
                    poseTranslationValue.text = it.formatTranslation(this)
                    poseRotationValue.text = it.formatRotation(this)
                }
                sceneValue.text = arSceneView.session?.format(this)
            }
        }
    }

    private fun onArUpdateDrawing() {
        if (shouldHandleDrawing()) {
            val x = arSceneView.width / 2F
            val y = arSceneView.height / 2F
            val pressed = sceneAdd.isPressed
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

    private fun onNodeUpdate(node: Nodes) {
        when {
            node != coordinator.selectedNode || node != coordinator.focusedNode || nodeBottomSheet.behavior().state == STATE_HIDDEN -> Unit
            else -> {
                nodeStatus.setImageResource(node.statusIcon())
                nodeDistance.text = formatDistance(this, arSceneView.arFrame?.camera?.pose, node.worldPosition)
                nodeCopy.isEnabled = (node as? CloudAnchor)?.id() != null
                nodePlayPause.isActivated = (node as? Video)?.isPlaying() == true
                nodeDelete.isEnabled = !node.isTransforming
                nodePositionValue.text = node.worldPosition.format(this)
                nodeRotationValue.text = node.worldRotation.format(this)
                nodeScaleValue.text = node.worldScale.format(this)
                nodeCloudAnchorStateValue.text = (node as? CloudAnchor)?.state()?.name
                nodeCloudAnchorIdValue.text = (node as? CloudAnchor)?.let { it.id() ?: "…" }
            }
        }
    }

    private fun onNodeSelected(old: Nodes? = coordinator.selectedNode, new: Nodes?) {
        old?.onNodeUpdate = null
        new?.onNodeUpdate = ::onNodeUpdate
    }

    private fun onNodeFocused(node: Nodes?) {
        val nodeSheetBehavior = nodeBottomSheet.behavior()
        val sceneBehavior = sceneBottomSheet.behavior()
        when (node) {
            null -> {
                nodeSheetBehavior.state = STATE_HIDDEN
                if ((sceneBottomSheet.tag as? Boolean) == true) {
                    sceneBottomSheet.tag = false
                    sceneBehavior.state = STATE_EXPANDED
                }
            }
            coordinator.selectedNode -> {
                nodeName.text = node.name
                nodeCopy.visibility = if (node is CloudAnchor) VISIBLE else GONE
                nodePlayPause.visibility = if (node is Video) VISIBLE else GONE
                (node as? MaterialNode)?.properties?.run {
                    nodeColorValue.setColor(color)
                    nodeMetallicValue.progress = metallic
                    nodeRoughnessValue.progress = roughness
                    nodeReflectanceValue.progress = reflectance
                }
                val materialVisibility = if (node is MaterialNode) VISIBLE else GONE
                setOfMaterialViews.forEach { it.visibility = materialVisibility }
                val cloudAnchorVisibility = if (node is CloudAnchor) VISIBLE else GONE
                setOfCloudAnchorViews.forEach { it.visibility = cloudAnchorVisibility }
                nodeSheetBehavior.state = STATE_EXPANDED
                if (sceneBehavior.state != STATE_COLLAPSED) {
                    sceneBehavior.state = STATE_COLLAPSED
                    sceneBottomSheet.tag = true
                }
            }
        }
    }

}
