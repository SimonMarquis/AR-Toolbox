package fr.smarquis.ar_toolbox

import android.Manifest.permission.CAMERA
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.media.CamcorderProfile
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
import android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.core.view.MenuCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.updatePadding
import androidx.viewbinding.ViewBinding
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.ArSceneView

abstract class ArActivity<T : ViewBinding>(private val inflate: (LayoutInflater) -> T) : AppCompatActivity() {

    private companion object {
        const val REQUEST_CAMERA_PERMISSION = 1
    }

    private var sessionInitializationFailed: Boolean = false

    private var installRequested: Boolean = false

    private lateinit var videoRecorder: VideoRecorder

    private val arCoreViewerIntent by lazy {
        createArCoreViewerIntent(
            uri = getString(R.string.scene_viewer_native_uri).toUri(),
            model = getString(R.string.scene_viewer_native_model),
            link = getString(R.string.scene_viewer_native_link),
            title = getString(R.string.scene_viewer_native_title),
        )
    }

    protected lateinit var binding: T

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(inflate(layoutInflater).also { binding = it }.root)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        videoRecorder = VideoRecorder(this, arSceneView) { isRecording ->
            if (isRecording) {
                Toast.makeText(this, R.string.recording, Toast.LENGTH_LONG).show()
            }

            recordingIndicator.apply {
                visibility = if (isRecording) View.VISIBLE else View.GONE
                (drawable as? Animatable)?.apply { if (isRecording) start() else stop() }
            }
        }
        recordingIndicator.setOnClickListener {
            videoRecorder.stop()
            videoRecorder.export()
        }
    }

    override fun onResume() {
        super.onResume()
        initArSession()
        try {
            arSceneView.resume()
        } catch (ex: CameraNotAvailableException) {
            sessionInitializationFailed = true
        }
        if (hasCameraPermission() && !installRequested && !sessionInitializationFailed) {
            renderNavigationBar()
            onArResumed()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION && !hasCameraPermission()) {
            redirectToApplicationSettings()
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

    abstract val arSceneView: ArSceneView

    abstract val recordingIndicator: ImageView

    open val features = emptySet<Session.Feature>()

    abstract fun config(session: Session): Config

    open fun onArResumed() = Unit

    private fun hasCameraPermission() = ActivityCompat.checkSelfPermission(this, CAMERA) == PERMISSION_GRANTED

    private fun requestCameraPermission() {
        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    private fun renderNavigationBar() {
        if (SDK_INT >= VERSION_CODES.O && resources.configuration.orientation != ORIENTATION_LANDSCAPE) {
            window.navigationBarColor = Color.WHITE
            window.decorView.systemUiVisibility = FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun initArSession() {
        if (arSceneView.session != null) {
            return
        }
        if (!hasCameraPermission()) {
            requestCameraPermission()
            return
        }
        if (sessionInitializationFailed) {
            return
        }
        val sessionException: UnavailableException?
        try {
            val requestInstall = ArCoreApk.getInstance().requestInstall(this, !installRequested)
            if (requestInstall == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true
                return
            }
            installRequested = false
            val session = Session(applicationContext, features)
            session.configure(config(session))
            arSceneView.setupSession(session)
            return
        } catch (e: UnavailableException) {
            sessionException = e
        } catch (e: Exception) {
            sessionException = UnavailableException().apply { initCause(e) }
        }
        sessionInitializationFailed = true
        Toast.makeText(this, sessionException.message(), Toast.LENGTH_LONG).show()
        finish()
    }

    fun initPopupMenu(anchor: View, @MenuRes menu: Int = 0, onClick: (MenuItem) -> Boolean = { false }, onUpdate: Menu.() -> Unit = {}): PopupMenu {
        val popupMenu = PopupMenu(ContextThemeWrapper(this, R.style.PopupMenu), anchor, Gravity.END)
        popupMenu.inflate(R.menu.menu_ar)
        menu.takeIf { it != 0 }?.let { popupMenu.inflate(it) }
        MenuCompat.setGroupDividerEnabled(popupMenu.menu, true)
        popupMenu.setOnMenuItemClickListener {
            when (it?.itemId) {
                R.id.menu_item_screenshot -> arSceneView.screenshot()
                R.id.menu_item_quality_2160p -> videoRecorder.start(CamcorderProfile.get(CamcorderProfile.QUALITY_2160P))
                R.id.menu_item_quality_1080p -> videoRecorder.start(CamcorderProfile.get(CamcorderProfile.QUALITY_1080P))
                R.id.menu_item_quality_720p -> videoRecorder.start(CamcorderProfile.get(CamcorderProfile.QUALITY_720P))
                R.id.menu_item_quality_480p -> videoRecorder.start(CamcorderProfile.get(CamcorderProfile.QUALITY_480P))
                R.id.menu_item_mode_scene -> {
                    it.isChecked = true
                    if (this !is SceneActivity) {
                        startActivity(Intent(this, SceneActivity::class.java))
                        finish()
                    }
                    return@setOnMenuItemClickListener false
                }
                R.id.menu_item_mode_faces -> {
                    it.isChecked = true
                    if (this !is FaceActivity) {
                        startActivity(Intent(this, FaceActivity::class.java))
                        finish()
                    }
                    return@setOnMenuItemClickListener false
                }
                R.id.menu_item_mode_native_viewer -> {
                    it.isChecked = true
                    arCoreViewerIntent.safeStartActivity(this)
                }
                R.id.menu_item_mode_web_viewer -> {
                    it.isChecked = true
                    CustomTabsIntent.Builder().build().launchUrl(this, getString(R.string.scene_viewer_web).toUri())
                }
                R.id.menu_item_performance_overlay -> sendBroadcast(Intent("com.google.ar.core.ENABLE_PERFORMANCE_OVERLAY"))
                R.id.menu_item_oss -> startActivity(Intent(this@ArActivity, OssLicensesMenuActivity::class.java))
                else -> return@setOnMenuItemClickListener onClick(it)
            }
            return@setOnMenuItemClickListener true
        }
        anchor.setOnClickListener {
            popupMenu.menu.apply {
                findItem(R.id.menu_item_mode_scene).isChecked = false
                findItem(R.id.menu_item_mode_faces).isChecked = false
                if (this@ArActivity is SceneActivity) findItem(R.id.menu_item_mode_scene).isChecked = true
                if (this@ArActivity is FaceActivity) findItem(R.id.menu_item_mode_faces).isChecked = true
                findItem(R.id.menu_item_mode_native_viewer).isEnabled = arCoreViewerIntent.resolveActivity(packageManager) != null
                findItem(R.id.menu_item_record).isEnabled = !videoRecorder.isRecording
                findItem(R.id.menu_item_quality_2160p).isEnabled = CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_2160P)
                findItem(R.id.menu_item_quality_1080p).isEnabled = CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P)
                findItem(R.id.menu_item_quality_720p).isEnabled = CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)
                findItem(R.id.menu_item_quality_480p).isEnabled = CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)
                onUpdate(this)
            }
            popupMenu.show()
        }
        return popupMenu
    }

    internal fun BottomSheetBehavior<out View>.configureBottomSheetInset(binding: ViewBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val navigationBarsInsets = insets.getInsets(navigationBars())
            view.updatePadding(bottom = navigationBarsInsets.bottom)
            peekHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height) + navigationBarsInsets.bottom
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    internal fun BottomSheetBehavior<out View>.configureBottomSheetAnimatedForegroundMask(binding: ViewBinding) {
        addBottomSheetCallback(
            object : BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    binding.root.isEnabled = newState != STATE_COLLAPSED
                }
            },
        )
    }
}
