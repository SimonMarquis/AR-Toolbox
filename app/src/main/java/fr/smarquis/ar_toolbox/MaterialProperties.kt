package fr.smarquis.ar_toolbox

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.IntRange

class MaterialProperties(
    @ColorInt val color: Int = DEFAULT_COLOR,
    @IntRange(from = 0, to = 100) val metallic: Int = DEFAULT_METALLIC,
    @IntRange(from = 0, to = 100) val roughness: Int = DEFAULT_ROUGHNESS,
    @IntRange(from = 0, to = 100) val reflectance: Int = DEFAULT_REFLECTANCE
) {
    companion object {

        private const val DEFAULT_COLOR = Color.WHITE
        private const val DEFAULT_METALLIC = 0
        private const val DEFAULT_ROUGHNESS = 40
        private const val DEFAULT_REFLECTANCE = 50

        val DEFAULT = MaterialProperties()

    }
}