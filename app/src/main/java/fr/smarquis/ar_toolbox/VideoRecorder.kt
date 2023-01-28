package fr.smarquis.ar_toolbox

import android.content.Context
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.FileProvider
import com.google.ar.sceneform.SceneView
import java.io.File
import java.io.IOException

class VideoRecorder(
    private val context: Context,
    private val sceneView: SceneView,
    private val onRecordingListener: (isRecording: Boolean) -> Unit,
) {

    companion object {
        private const val TAG = "VideoRecorder"
    }

    var isRecording: Boolean = false
        private set

    private val mediaRecorder: MediaRecorder = MediaRecorder()

    private var file: File? = null

    fun start(profile: CamcorderProfile) {
        if (isRecording) {
            return
        }
        try {
            file = cacheFile(context, ".mp4").apply { createNewFile() }
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setOutputFile(file!!.absolutePath)
            mediaRecorder.setVideoEncodingBitRate(profile.videoBitRate)
            mediaRecorder.setVideoFrameRate(profile.videoFrameRate)
            mediaRecorder.setVideoSize(profile.width(), profile.height())
            mediaRecorder.setVideoEncoder(profile.videoCodec)
            mediaRecorder.prepare()
            mediaRecorder.start()
        } catch (e: IOException) {
            Log.e(TAG, "Exception starting recorder", e)
            return
        }
        sceneView.startMirroringToSurface(mediaRecorder.surface, 0, 0, profile.width(), profile.height())
        isRecording = true
        onRecordingListener(true)
    }

    fun stop() {
        if (!isRecording) {
            return
        }
        sceneView.stopMirroringToSurface(mediaRecorder.surface)
        mediaRecorder.stop()
        mediaRecorder.reset()
        isRecording = false
        onRecordingListener(false)
    }

    fun export() {
        val uri = FileProvider.getUriForFile(context, context.packageName, file ?: return)
        context.startActivity(viewOrShare(uri, "video/mp4"))
    }

    private fun orientation() = context.resources.configuration.orientation

    private fun isLandscape() = orientation() == ORIENTATION_LANDSCAPE

    private fun CamcorderProfile.width(): Int = if (isLandscape()) videoFrameWidth else videoFrameHeight

    private fun CamcorderProfile.height(): Int = if (isLandscape()) videoFrameHeight else videoFrameWidth
}
