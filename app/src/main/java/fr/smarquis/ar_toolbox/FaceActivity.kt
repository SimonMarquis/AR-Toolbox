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
import kotlinx.android.synthetic.main.activity_face.*
import kotlinx.android.synthetic.main.bottom_sheet_face.*
import kotlinx.android.synthetic.main.bottom_sheet_face_body.*
import kotlinx.android.synthetic.main.bottom_sheet_face_header.*

class FaceActivity : ArActivity(R.layout.activity_face) {

    private val nodes = mutableMapOf<AugmentedFace, AugmentedFaceNode>()

    private val settings by lazy { Settings.instance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arSceneView.cameraStreamRenderPriority = Renderable.RENDER_PRIORITY_FIRST
        arSceneView.scene.addOnUpdateListener { onArUpdate() }
        settings.faceRegions.applyTo(arSceneView)
        settings.faceMesh.applyTo(arSceneView)
        faceBottomSheet.behavior().state = STATE_HIDDEN
        faceHeader.setOnClickListener { faceBottomSheet.behavior().toggle() }
        initPopupMenu(
            anchor = faceMore,
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
            })
    }

    override fun arSceneView(): ArSceneView = arSceneView

    override fun features(): Set<Session.Feature> = setOf(Session.Feature.FRONT_CAMERA)

    override fun config(session: Session): Config = Config(session).apply {
        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
        focusMode = Config.FocusMode.AUTO
    }

    override fun recordingIndicator(): ImageView? = faceRecording

    override fun onArResumed() {
        faceBottomSheet.behavior().update(state = STATE_EXPANDED, isHideable = false)
    }

    private fun onArUpdate() {
        val updatedFaces = arSceneView.arFrame?.getUpdatedTrackables(AugmentedFace::class.java)
        updatedFaces?.forEach {
            when (it.trackingState) {
                TrackingState.TRACKING -> nodes.computeIfAbsent(it) { face -> FaceNode(this, face).apply { setParent(arSceneView().scene) } }
                TrackingState.PAUSED -> Unit
                TrackingState.STOPPED -> nodes[it]?.setParent(null)
                else -> Unit
            }
        }

        val trackingFaces = updatedFaces?.filter { it.trackingState == TrackingState.TRACKING }
        faceStatusIcon.setImageResource(
            when (trackingFaces?.size) {
                null, 0 -> android.R.drawable.presence_invisible
                else -> android.R.drawable.presence_online
            }
        )
        trackingFaces?.firstOrNull().apply {
            this?.getRegionPose(AugmentedFace.RegionType.NOSE_TIP).let { pose ->
                faceNoseTipTranslation.text = pose.formatTranslation(this@FaceActivity)
                faceNoseTipRotation.text = pose.formatRotation(this@FaceActivity)
            }
            this?.getRegionPose(AugmentedFace.RegionType.FOREHEAD_LEFT).let { pose ->
                faceForeheadLeftTranslation.text = pose.formatTranslation(this@FaceActivity)
                faceForeheadLeftRotation.text = pose.formatRotation(this@FaceActivity)
            }
            this?.getRegionPose(AugmentedFace.RegionType.FOREHEAD_RIGHT).let { pose ->
                faceForeheadRightTranslation.text = pose.formatTranslation(this@FaceActivity)
                faceForeheadRightRotation.text = pose.formatRotation(this@FaceActivity)
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
