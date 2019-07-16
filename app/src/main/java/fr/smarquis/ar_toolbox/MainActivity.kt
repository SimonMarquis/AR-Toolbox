package fr.smarquis.ar_toolbox

import android.Manifest.permission.CAMERA
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.media.CamcorderProfile.*
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat.checkSelfPermission
import androidx.core.net.toUri
import androidx.core.view.MenuCompat
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.ar.core.*
import com.google.ar.core.ArCoreApk.InstallStatus.INSTALL_REQUESTED
import com.google.ar.core.TrackingFailureReason.*
import com.google.ar.core.TrackingState.*
import com.google.ar.core.exceptions.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.HitTestResult
import com.google.ar.sceneform.rendering.PlaneRenderer
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.bottom_sheet_main.*
import kotlinx.android.synthetic.main.bottom_sheet_main_body.*
import kotlinx.android.synthetic.main.bottom_sheet_main_header.*
import kotlinx.android.synthetic.main.bottom_sheet_node.*
import kotlinx.android.synthetic.main.bottom_sheet_node_body.*
import kotlinx.android.synthetic.main.bottom_sheet_node_header.*

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_CAMERA_PERMISSION = 1
    }

    private lateinit var mainBottomSheetBehavior: BottomSheetBehavior<out View>
    private lateinit var nodeBottomSheetBehavior: BottomSheetBehavior<out View>
    private lateinit var videoRecorder: VideoRecorder

    private val coordinator by lazy { Coordinator(this, onArTap = ::onArTap, onNodeSelected = ::onNodeSelected) }
    private val model by lazy { ViewModelProviders.of(this).get(MainViewModel::class.java) }

    private val setOfMaterialViews by lazy {
        setOf(
            nodeColorValue, nodeColorLabel,
            nodeMetallicValue, nodeMetallicLabel,
            nodeRoughnessValue, nodeRoughnessLabel,
            nodeReflectanceValue, nodeReflectanceLabel
        )
    }

    private var drawing: Drawing? = null
    private var restoreMainBottomSheetExpandedState: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initMainBottomSheet()
        initNodeBottomSheet()
        initAr()
        initWithIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        initWithIntent(intent)
    }

    private fun initWithIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.let {
            Toast.makeText(this, it.toString(), Toast.LENGTH_SHORT).show()
            selectExternalModel(it.toString())
            this.intent = null
        }
    }

    override fun onResume() {
        super.onResume()
        initArSession()
        try {
            arSceneView.resume()
        } catch (ex: CameraNotAvailableException) {
            model.sessionInitializationFailed = true
        }
        if (hasCameraPermission() && !model.installRequested && !model.sessionInitializationFailed) {
            if (SDK_INT >= O && resources.configuration.orientation != ORIENTATION_LANDSCAPE) {
                window.navigationBarColor = Color.WHITE
                window.decorView.systemUiVisibility =
                    FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            mainBottomSheetBehavior.apply {
                if (state == STATE_HIDDEN) {
                    state = STATE_EXPANDED
                    isHideable = false
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        arSceneView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arSceneView.destroy()
        videoRecorder.stop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION && !hasCameraPermission()) {
            redirectToApplicationSettings()
        }
    }

    override fun onBackPressed() {
        if (coordinator.selectedNode != null) {
            coordinator.selectNode(null)
        } else {
            super.onBackPressed()
        }
    }


    private fun initMainBottomSheet() {
        mainBottomSheetBehavior = from(mainBottomSheet).apply {
            state = STATE_HIDDEN
        }

        mainHeader.setOnClickListener {
            mainBottomSheetBehavior.state = when (mainBottomSheetBehavior.state) {
                STATE_COLLAPSED -> STATE_EXPANDED
                STATE_EXPANDED -> STATE_COLLAPSED
                else -> return@setOnClickListener
            }
        }

        addImageView.setOnClickListener {
            val session = arSceneView.session
            val camera = arSceneView.arFrame?.camera ?: return@setOnClickListener
            if (session == null || camera.trackingState != TRACKING) return@setOnClickListener
            createNodeAndAddToScene(anchor = { session.createAnchor(Nodes.defaultPose(arSceneView)) }, select = false)
        }

        val settings = PopupMenu(ContextThemeWrapper(this, R.style.PopupMenu), moreImageView, Gravity.END)
        settings.inflate(R.menu.menu_main)
        MenuCompat.setGroupDividerEnabled(settings.menu, true)
        settings.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_item_screenshot -> arSceneView.screenshot()
                R.id.menu_item_quality_2160p -> videoRecorder.start(get(QUALITY_2160P))
                R.id.menu_item_quality_1080p -> videoRecorder.start(get(QUALITY_1080P))
                R.id.menu_item_quality_720p -> videoRecorder.start(get(QUALITY_720P))
                R.id.menu_item_quality_480p -> videoRecorder.start(get(QUALITY_480P))
                R.id.menu_item_clean_up_scene -> arSceneView.scene.callOnHierarchy { node ->
                    (node as? Nodes)?.delete()
                }
                R.id.menu_item_sunlight -> Settings.Sunlight.apply {
                    toggle()
                    update(arSceneView)
                }
                R.id.menu_item_shadows -> Settings.Shadows.apply {
                    toggle()
                    update(arSceneView)
                }
                R.id.menu_item_plane_renderer -> Settings.Planes.apply {
                    toggle()
                    update(arSceneView)
                }
                R.id.menu_item_selection_visualizer -> Settings.Selection.apply {
                    toggle()
                    update(coordinator.selectionVisualizer)
                }
            }
            true
        }
        (recordImageView.drawable as? Animatable)?.start()
        recordImageView.setOnClickListener {
            videoRecorder.stop()
            videoRecorder.export()
        }
        moreImageView.setOnClickListener {
            settings.menu.apply {
                findItem(R.id.menu_item_record).isVisible = !videoRecorder.isRecording
                findItem(R.id.menu_item_quality_2160p).isEnabled = hasProfile(QUALITY_2160P)
                findItem(R.id.menu_item_quality_1080p).isEnabled = hasProfile(QUALITY_1080P)
                findItem(R.id.menu_item_quality_720p).isEnabled = hasProfile(QUALITY_720P)
                findItem(R.id.menu_item_quality_480p).isEnabled = hasProfile(QUALITY_480P)
                findItem(R.id.menu_item_clean_up_scene).isVisible = arSceneView.scene.findInHierarchy { it is Nodes } != null
                findItem(R.id.menu_item_sunlight).isChecked = Settings.Sunlight.get()
                findItem(R.id.menu_item_shadows).isChecked = Settings.Shadows.get()
                findItem(R.id.menu_item_plane_renderer).isChecked = Settings.Planes.get()
                findItem(R.id.menu_item_selection_visualizer).isChecked = Settings.Selection.get()
            }
            settings.show()
        }

        model.selection.observe(this, androidx.lifecycle.Observer {
            modelSphere.isSelected = it == Sphere::class
            modelCylinder.isSelected = it == Cylinder::class
            modelCube.isSelected = it == Cube::class
            modelView.isSelected = it == Layout::class
            modelAndy.isSelected = it == Andy::class
            modelDrawing.isSelected = it == Drawing::class
            modelLink.isSelected = it == Link::class
            addImageView.requestDisallowInterceptTouchEvent = it == Drawing::class
        })

        modelSphere.setOnClickListener { model.selection.value = Sphere::class }
        modelCylinder.setOnClickListener { model.selection.value = Cylinder::class }
        modelCube.setOnClickListener { model.selection.value = Cube::class }
        modelView.setOnClickListener { model.selection.value = Layout::class }
        modelDrawing.setOnClickListener { model.selection.value = Drawing::class }
        modelAndy.setOnClickListener { model.selection.value = Andy::class }
        modelLink.setOnClickListener {
            promptExternalModel()
        }
        colorValue.setOnColorChangeListener { color ->
            arSceneView.planeRenderer.material?.thenAccept {
                it.setFloat3(PlaneRenderer.MATERIAL_COLOR, color.toArColor())
            }
        }
        colorValue.post { colorValue.setColor(MaterialProperties.DEFAULT.color) }
    }

    private fun initNodeBottomSheet() {
        nodeBottomSheetBehavior = from(nodeBottomSheet)
        nodeBottomSheetBehavior.skipCollapsed = true
        nodeBottomSheetBehavior.setBottomSheetCallback(object : BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                bottomSheet.requestLayout()
                if (newState == STATE_HIDDEN) {
                    coordinator.selectNode(null)
                }
            }
        })
        nodeBottomSheetBehavior.state = STATE_HIDDEN
        nodeHeader.setOnClickListener {
            if (coordinator.selectedNode == null) {
                nodeBottomSheetBehavior.state = STATE_HIDDEN
            } else {
                coordinator.selectNode(null)
            }
        }
        nodeDelete.setOnClickListener { (coordinator.selectedNode as? Nodes)?.delete() }

        nodeColorValue.setOnColorChangeListener { selectedMaterialNode()?.update { color = it } }
        nodeMetallicValue.progress = MaterialProperties.DEFAULT.metallic
        nodeMetallicValue.setOnSeekBarChangeListener(SimpleSeekBarChangeListener { selectedMaterialNode()?.update { metallic = it } })
        nodeRoughnessValue.progress = MaterialProperties.DEFAULT.roughness
        nodeRoughnessValue.setOnSeekBarChangeListener(SimpleSeekBarChangeListener { selectedMaterialNode()?.update { roughness = it } })
        nodeReflectanceValue.progress = MaterialProperties.DEFAULT.reflectance
        nodeReflectanceValue.setOnSeekBarChangeListener(SimpleSeekBarChangeListener { selectedMaterialNode()?.update { reflectance = it } })
    }

    private fun selectedMaterialNode() = (coordinator.selectedNode as? MaterialNode)

    private fun materialProperties() = MaterialProperties(
        if (selectedMaterialNode() != null) nodeColorValue.getColor() else colorValue.getColor(),
        nodeMetallicValue.progress,
        nodeRoughnessValue.progress,
        nodeReflectanceValue.progress
    )

    private fun initAr() {
        arSceneView.scene.addOnPeekTouchListener { hitTestResult, motionEvent ->
            coordinator.onTouch(hitTestResult, motionEvent)
            if (shouldHandleDrawing(motionEvent, hitTestResult)) {
                val x = motionEvent.x
                val y = motionEvent.y
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> drawing = Drawing.create(x, y, true, materialProperties(), arSceneView, coordinator)
                    MotionEvent.ACTION_MOVE -> drawing?.extend(x, y)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drawing = drawing?.deleteIfEmpty().let { null }
                }
            }
        }

        arSceneView.scene.addOnUpdateListener(::onArUpdate)
        videoRecorder = VideoRecorder(this, arSceneView) { isRecording ->
            if (isRecording) {
                Toast.makeText(this, R.string.recording, Toast.LENGTH_LONG).show()
            }
            recordImageView.visibility = if (isRecording) VISIBLE else GONE
        }
        Settings.Sunlight.update(arSceneView)
        Settings.Shadows.update(arSceneView)
        Settings.Planes.update(arSceneView)
        Settings.Selection.update(coordinator.selectionVisualizer)
    }

    private fun shouldHandleDrawing(motionEvent: MotionEvent? = null, hitTestResult: HitTestResult? = null): Boolean {
        if (model.selection.value != Drawing::class) return false
        if (coordinator.selectedNode?.isTransforming == true) return false
        if (arSceneView.arFrame?.camera?.trackingState != TRACKING) return false
        if (motionEvent?.action == MotionEvent.ACTION_DOWN && hitTestResult?.node != null) return false
        return true
    }

    private fun initArSession() {
        if (arSceneView.session != null) {
            return
        }
        if (!hasCameraPermission()) {
            requestCameraPermission()
            return
        }
        if (model.sessionInitializationFailed) {
            return
        }
        val sessionException: UnavailableException?
        try {
            val requestInstall = ArCoreApk.getInstance().requestInstall(this, !model.installRequested)
            if (requestInstall == INSTALL_REQUESTED) {
                model.installRequested = true
                return
            }
            model.installRequested = false
            val session = Session(this, emptySet())
            session.configure(Config(session).apply {
                lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                cloudAnchorMode = Config.CloudAnchorMode.DISABLED
                augmentedFaceMode = Config.AugmentedFaceMode.DISABLED
                focusMode = Config.FocusMode.AUTO
            })
            arSceneView.setupSession(session)
            return
        } catch (e: UnavailableException) {
            sessionException = e
        } catch (e: Exception) {
            sessionException = UnavailableException().apply { initCause(e) }
        }
        model.sessionInitializationFailed = true

        val message = when (sessionException) {
            is UnavailableArcoreNotInstalledException -> R.string.exception_arcore_not_installed
            is UnavailableApkTooOldException -> R.string.exception_apk_too_old
            is UnavailableSdkTooOldException -> R.string.exception_sdk_too_old
            is UnavailableDeviceNotCompatibleException -> R.string.exception_device_not_compatible
            else -> R.string.exception_unknown
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun hasCameraPermission() = checkSelfPermission(this, CAMERA) == PERMISSION_GRANTED

    private fun requestCameraPermission() {
        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(CAMERA), REQUEST_CAMERA_PERMISSION)
        }
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
        val editText = AppCompatEditText(context)
        editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
        editText.setText(model.externalModelUri.value.takeUnless { it in resources.getStringArray(R.array.models_uris) })
        editText.setHint(R.string.model_link_custom_hint)
        AlertDialog.Builder(context)
            .setTitle(R.string.model_link_custom_title)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ -> selectExternalModel(editText.text.toString()) }
            .show()
        val layoutParams = editText.layoutParams as ViewGroup.MarginLayoutParams
        val margin = resources.getDimensionPixelSize(R.dimen.material_unit_2)
        layoutParams.setMargins(margin, margin, margin, 0)
        editText.requestLayout()
    }

    private fun selectExternalModel(value: String) {
        model.externalModelUri.value = value
        model.selection.value = Link::class
        Link.warmup(this, value.toUri())
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
        }?.let { createNodeAndAddToScene(anchor = { it.createAnchor() }) }
    }

    private fun createNodeAndAddToScene(anchor: () -> Anchor, select: Boolean = true) {
        when (model.selection.value) {
            Sphere::class -> Sphere(this, materialProperties(), coordinator)
            Cylinder::class -> Cylinder(this, materialProperties(), coordinator)
            Cube::class -> Cube(this, materialProperties(), coordinator)
            Layout::class -> Layout(this, coordinator)
            Andy::class -> Andy(this, coordinator)
            Link::class -> Link(this, model.externalModelUri.value.orEmpty().toUri(), coordinator)
            else -> null
        }?.let {
            it.anchorToScene(anchor(), arSceneView.scene)
            if (select) coordinator.selectNode(it)
        }
    }

    private fun onArUpdate(@Suppress("UNUSED_PARAMETER") frameTime: FrameTime) {
        val camera = arSceneView.arFrame?.camera
        val state = camera?.trackingState
        val reason = camera?.trackingFailureReason
        trackingTextView.setText(
            when (state) {
                TRACKING -> R.string.tracking_success
                PAUSED -> when (reason) {
                    NONE -> R.string.tracking_failure_none
                    BAD_STATE -> R.string.tracking_failure_bad_state
                    INSUFFICIENT_LIGHT -> R.string.tracking_failure_insufficient_light
                    EXCESSIVE_MOTION -> R.string.tracking_failure_excessive_motion
                    INSUFFICIENT_FEATURES -> R.string.tracking_failure_insufficient_features
                    null -> 0
                }
                STOPPED -> R.string.tracking_stopped
                null -> 0
            }
        )
        trackingImageView.setImageResource(
            when (state) {
                TRACKING -> android.R.drawable.presence_online
                PAUSED -> when (reason) {
                    NONE -> android.R.drawable.presence_invisible
                    BAD_STATE -> android.R.drawable.presence_busy
                    INSUFFICIENT_LIGHT, EXCESSIVE_MOTION, INSUFFICIENT_FEATURES -> android.R.drawable.presence_away
                    null -> 0
                }
                STOPPED -> android.R.drawable.presence_offline
                null -> 0
            }
        )


        when (mainBottomSheetBehavior.state) {
            STATE_HIDDEN, STATE_COLLAPSED -> Unit
            else -> {
                arSceneView.arFrame?.camera?.pose?.let {
                    poseTranslationValue.text = it.formatTranslation(this)
                    poseRotationValue.text = it.formatRotation(this)
                }
                sceneValue.text = arSceneView.session?.format(this)
            }
        }

        addImageView.isEnabled = state == TRACKING

        if (shouldHandleDrawing()) {
            val x = arSceneView.width / 2F
            val y = arSceneView.height / 2F
            val pressed = addImageView.isPressed
            when {
                pressed && drawing == null -> drawing = Drawing.create(x, y, false, materialProperties(), arSceneView, coordinator)
                pressed && drawing?.isFromTouch == false -> drawing?.extend(x, y)
                !pressed && drawing?.isFromTouch == false -> drawing = drawing?.deleteIfEmpty().let { null }
                else -> Unit
            }
        }
    }

    private fun onNodeUpdate(node: Nodes) {
        when (nodeBottomSheetBehavior.state) {
            STATE_HIDDEN -> Unit
            else -> arSceneView.arFrame?.camera?.pose?.let {
                nodeStatus.setImageResource(if (node.isActive && node.isEnabled && (node.parent as? AnchorNode)?.isTracking == true) android.R.drawable.presence_online else android.R.drawable.presence_invisible)
                nodeDistance.text = formatDistance(this, it, node.worldPosition)
                nodeDelete.isEnabled = !node.isTransforming
                nodePositionValue.text = node.worldPosition.format(this)
                nodeRotationValue.text = node.worldRotation.format(this)
                nodeScaleValue.text = node.worldScale.format(this)
            }
        }
    }

    private fun onNodeSelected(old: Nodes?, new: Nodes?) {
        old?.onNodeUpdate = null
        if (new == null) {
            nodeBottomSheetBehavior.state = STATE_HIDDEN
            if (restoreMainBottomSheetExpandedState) {
                restoreMainBottomSheetExpandedState = false
                mainBottomSheetBehavior.state = STATE_EXPANDED
            }
        } else {
            nodeName.text = new.name
            (new as? MaterialNode)?.properties?.run {
                nodeColorValue.setColor(color)
                nodeMetallicValue.progress = metallic
                nodeRoughnessValue.progress = roughness
                nodeReflectanceValue.progress = reflectance
            }
            val visibility = if (new is MaterialNode) VISIBLE else GONE
            setOfMaterialViews.forEach { it.visibility = visibility }
            nodeBottomSheetBehavior.state = STATE_EXPANDED
            if (mainBottomSheetBehavior.state != STATE_COLLAPSED) {
                mainBottomSheetBehavior.state = STATE_COLLAPSED
                restoreMainBottomSheetExpandedState = true
            }
            new.onNodeUpdate = ::onNodeUpdate
        }
    }
}
