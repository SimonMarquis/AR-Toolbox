package fr.smarquis.ar_toolbox

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.view.PixelCopy
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.ar.core.Camera
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfig.DepthSensorUsage.DO_NOT_USE
import com.google.ar.core.CameraConfig.DepthSensorUsage.REQUIRE_AND_USE
import com.google.ar.core.DepthPoint
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.Trackable
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

val screenshotHandler = HandlerThread("screenshot")
    .also { it.start() }
    .let { Handler(it.looper) }

fun Pose?.formatTranslation(context: Context): String = context.getString(R.string.format_pose_translation, this?.tx() ?: 0F, this?.ty() ?: 0F, this?.tz() ?: 0F)
fun Pose?.formatRotation(context: Context): String = context.getString(R.string.format_pose_rotation, this?.qx() ?: 0F, this?.qy() ?: 0F, this?.qz() ?: 0F, this?.qw() ?: 0F)
fun Pose.toVector3(): Vector3 = Vector3(tx(), ty(), tz())
fun Vector3.format(context: Context) = context.getString(R.string.format_vector3, x, y, z)
fun Quaternion.format(context: Context) = context.getString(R.string.format_quaternion, x, y, z, w)
fun Session.format(context: Context): String {
    val trackables = getAllTrackables(Trackable::class.java).groupBy { it::class.java }
    return context.getString(
        R.string.format_session,
        allAnchors.count(),
        trackables[Plane::class.java]?.count() ?: 0,
        trackables[Point::class.java]?.count() ?: 0,
        trackables[DepthPoint::class.java]?.count() ?: 0,
    )
}

fun CameraConfig.format(context: Context) = context.getString(
    R.string.format_camera_config,
    textureSize,
    fpsRange,
    when (depthSensorUsage) {
        REQUIRE_AND_USE -> true
        DO_NOT_USE, null -> false
    },
)

fun Camera?.formatDistance(context: Context, node: Node) = this?.pose?.let { formatDistance(context, distance(it.toVector3(), node.worldPosition)) } ?: "?"

fun formatDistance(context: Context, src: Node, dst: Node): String = formatDistance(context, distance(src, dst))

fun formatDistance(context: Context, distanceInMeters: Double): String {
    val distanceInCentimeters = (distanceInMeters * 100).roundToInt()
    return if (distanceInCentimeters >= 100) {
        context.getString(R.string.format_distance_m, distanceInCentimeters / 100F)
    } else {
        context.getString(R.string.format_distance_cm, distanceInCentimeters)
    }
}

fun distance(src: Node, dst: Node): Double = distance(src.worldPosition, dst.worldPosition)

fun distance(src: Vector3, dst: Vector3): Double = sqrt(((dst.x - src.x).pow(2) + (dst.y - src.y).pow(2) + (dst.z - src.z).pow(2)).toDouble())

class SimpleSeekBarChangeListener(val block: (Int) -> Unit) : SeekBar.OnSeekBarChangeListener {
    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        block(progress)
    }
}

fun AppCompatActivity.redirectToApplicationSettings() {
    val intent = Intent().apply {
        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        data = Uri.fromParts("package", packageName, null)
    }
    startActivity(intent)
}

fun filename(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

fun cacheFile(context: Context, extension: String): File = File(context.cacheDir, filename() + extension)

inline fun <reified T> ArSceneView.findNode(): T? = scene.findInHierarchy { it is T } as T?

fun ArSceneView.screenshot() {
    Toast.makeText(context, R.string.screenshot_saving, Toast.LENGTH_LONG).show()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(this, bitmap, { result ->
        when (result) {
            PixelCopy.SUCCESS -> {
                val file = cacheFile(context, ".png")
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
                val uri = FileProvider.getUriForFile(context, context.packageName, file)
                context.startActivity(viewOrShare(uri, "image/png"))
            }
            else -> Toast.makeText(context, "Screenshot failure: $result", Toast.LENGTH_LONG).show()
        }
    }, screenshotHandler)
}

fun viewOrShare(data: Uri, mime: String): Intent {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(data, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val share = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, data)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(Intent(), null).apply {
        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(view, share))
    }
}

fun @receiver:ColorInt Int.toArColor(): Color = Color(this)

fun Pose.translation() = Vector3(tx(), ty(), tz())

fun Pose.rotation() = Quaternion(qx(), qy(), qz(), qw())

fun UnavailableException?.message(): Int = when (this) {
    is UnavailableArcoreNotInstalledException -> R.string.exception_arcore_not_installed
    is UnavailableApkTooOldException -> R.string.exception_apk_too_old
    is UnavailableSdkTooOldException -> R.string.exception_sdk_too_old
    is UnavailableDeviceNotCompatibleException -> R.string.exception_device_not_compatible
    is UnavailableUserDeclinedInstallationException -> R.string.exception_user_declined_installation
    else -> R.string.exception_unknown
}

fun ViewBinding.behavior(): BottomSheetBehavior<out View> = root.behavior()

fun View.behavior(): BottomSheetBehavior<out View> = BottomSheetBehavior.from(this)

fun BottomSheetBehavior<out View>.toggle() {
    state = when (state) {
        BottomSheetBehavior.STATE_COLLAPSED -> BottomSheetBehavior.STATE_EXPANDED
        BottomSheetBehavior.STATE_EXPANDED -> BottomSheetBehavior.STATE_COLLAPSED
        else -> return
    }
}

fun BottomSheetBehavior<out View>.update(@BottomSheetBehavior.State state: Int, isHideable: Boolean?) {
    this.state = state
    if (isHideable != null) {
        this.isHideable = isHideable
    }
}

fun createArCoreViewerIntent(uri: Uri, model: String, link: String, title: String): Intent = Intent(Intent.ACTION_VIEW).apply {
    `package` = "com.google.ar.core"
    data = uri.buildUpon()
        .appendQueryParameter("file", model)
        .appendQueryParameter("mode", "ar_preferred")
        .appendQueryParameter("title", title)
        .appendQueryParameter("link", link)
        .appendQueryParameter("resizable", "true")
        .build()
}

fun Intent?.safeStartActivity(context: Context) {
    if (this == null) return
    if (resolveActivity(context.packageManager) == null) return
    try {
        context.startActivity(this)
    } catch (e: Exception) {
    }
}
