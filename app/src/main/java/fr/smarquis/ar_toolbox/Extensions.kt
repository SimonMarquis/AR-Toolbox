package fr.smarquis.ar_toolbox

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.view.PixelCopy
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.sqrt

val screenshotHandler = HandlerThread("screenshot")
    .also { it.start() }
    .let { Handler(it.looper) }

fun Pose.formatTranslation(context: Context) = context.getString(R.string.format_pose_translation, tx(), ty(), tz())
fun Pose.formatRotation(context: Context) = context.getString(R.string.format_pose_rotation, qx(), qy(), qz(), qw())
fun Vector3.format(context: Context) = context.getString(R.string.format_vector3, x, y, z)
fun Quaternion.format(context: Context) = context.getString(R.string.format_quaternion, x, y, z, w)
fun Session.format(context: Context) = context.getString(
    R.string.format_session,
    allAnchors.count(),
    getAllTrackables(Plane::class.java).count(),
    getAllTrackables(Point::class.java).count()
)

fun formatDistance(context: Context, pose: Pose, vector3: Vector3): String {
    val x = pose.tx() - vector3.x
    val y = pose.ty() - vector3.y
    val z = pose.tz() - vector3.z
    val distanceInMeters = sqrt((x * x + y * y + z * z).toDouble())
    val distanceInCentimeters = (distanceInMeters * 100).roundToInt()
    return if (distanceInCentimeters >= 100) {
        context.getString(R.string.format_distance_m, distanceInCentimeters / 100F)
    } else {
        context.getString(R.string.format_distance_cm, distanceInCentimeters)
    }
}

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


fun Nodes.delete() {
    if (this == transformationSystem.selectedNode) {
        transformationSystem.selectNode(null)
    }
    (parent as? AnchorNode)?.anchor?.detach()
    setParent(null)
}

fun Nodes.anchorToScene(anchor: Anchor, scene: Scene) {
    setParent(AnchorNode(anchor).apply { setParent(scene) })
}
