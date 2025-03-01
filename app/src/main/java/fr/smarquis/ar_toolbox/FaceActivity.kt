package fr.smarquis.ar_toolbox

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.ar.core.AugmentedFace
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.AugmentedFaceNode
import fr.smarquis.ar_toolbox.databinding.ActivityFaceBinding

class FaceActivity : ArActivity<ActivityFaceBinding>(ActivityFaceBinding::inflate) {

    private val nodes = mutableMapOf<AugmentedFace, AugmentedFaceNode>()

    private val settings by lazy { Settings.instance(this) }

    override val arSceneView: ArSceneView get() = binding.arSceneView

    override val recordingIndicator: ImageView get() = bottomSheet.header.recording

    private val bottomSheet get() = binding.bottomSheet

    override val features: Set<Session.Feature> = setOf(Session.Feature.FRONT_CAMERA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arSceneView.cameraStreamRenderPriority = Renderable.RENDER_PRIORITY_FIRST
        arSceneView.scene.addOnUpdateListener { onArUpdate() }
        settings.faceRegions.applyTo(arSceneView)
        settings.faceMesh.applyTo(arSceneView)
        bottomSheet.behavior().apply {
            state = STATE_HIDDEN
            configureBottomSheetAnimatedForegroundMask(bottomSheet.body)
            configureBottomSheetInset(bottomSheet.inset)
        }
        bottomSheet.header.root.setOnClickListener { bottomSheet.behavior().toggle() }
        initPopupMenu(
            anchor = bottomSheet.header.more,
            menu = R.menu.menu_face,
            onClick = {
                when (it.itemId) {
                    R.id.menu_item_face_regions -> settings.faceRegions.toggle(it, arSceneView)
                    R.id.menu_item_face_mesh -> settings.faceMesh.toggle(it, arSceneView)
                }
                when (it.itemId) {
                    R.id.menu_item_face_regions, R.id.menu_item_face_mesh -> false
                    else -> true
                }
            },
            onUpdate = {
                settings.faceRegions.applyTo(findItem(R.id.menu_item_face_regions))
                settings.faceMesh.applyTo(findItem(R.id.menu_item_face_mesh))
            },
        )
    }

    override fun config(session: Session): Config = Config(session).apply {
        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
        focusMode = Config.FocusMode.AUTO
    }

    override fun onArResumed() {
        bottomSheet.behavior().update(state = STATE_EXPANDED, isHideable = false)
    }

    private fun onArUpdate() {
        val updatedFaces = arSceneView.arFrame?.getUpdatedTrackables(AugmentedFace::class.java)
        updatedFaces?.forEach {
            when (it.trackingState) {
                TrackingState.TRACKING -> nodes.computeIfAbsent(it) { face -> FaceNode(this, face).apply { setParent(arSceneView.scene) } }
                TrackingState.PAUSED -> Unit
                TrackingState.STOPPED -> nodes[it]?.setParent(null)
                else -> Unit
            }
        }

        val trackingFaces = updatedFaces?.filter { it.trackingState == TrackingState.TRACKING }
        bottomSheet.header.status.setImageResource(
            when (trackingFaces?.size) {
                null, 0 -> android.R.drawable.presence_invisible
                else -> android.R.drawable.presence_online
            },
        )
        trackingFaces?.firstOrNull()?.let {
            with(bottomSheet.body) {
                it.getRegionPose(AugmentedFace.RegionType.NOSE_TIP).let { pose ->
                    noseTipTranslation.text = pose.formatTranslation(this@FaceActivity)
                    noseTipRotation.text = pose.formatRotation(this@FaceActivity)
                }
                it.getRegionPose(AugmentedFace.RegionType.FOREHEAD_LEFT).let { pose ->
                    foreheadLeftTranslation.text = pose.formatTranslation(this@FaceActivity)
                    foreheadLeftRotation.text = pose.formatRotation(this@FaceActivity)
                }
                it.getRegionPose(AugmentedFace.RegionType.FOREHEAD_RIGHT).let { pose ->
                    foreheadRightTranslation.text = pose.formatTranslation(this@FaceActivity)
                    foreheadRightRotation.text = pose.formatRotation(this@FaceActivity)
                }
            }
        }
    }

    class FaceNode(context: Context, augmentedFace: AugmentedFace) : AugmentedFaceNode(augmentedFace) {

        private var faceRegions: ModelRenderable? = null
        private var faceMesh: Texture? = null

        init {
            val settings = Settings.instance(context)
            ModelRenderable.builder().setSource(context.applicationContext, R.raw.fox_face).build().thenAccept {
                faceRegions = it.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
                apply(settings.faceRegions)
            }
            Texture.builder().setSource(context.applicationContext, R.drawable.fox_face_mesh_texture).build().thenAccept {
                faceMesh = it
                apply(settings.faceMesh)
            }
        }

        fun apply(faceRegions: Settings.FaceRegions) {
            faceRegionsRenderable = if (faceRegions.get()) this.faceRegions else null
        }

        fun apply(faceMesh: Settings.FaceMesh) {
            faceMeshTexture = if (faceMesh.get()) this.faceMesh else null
        }
    }
}
