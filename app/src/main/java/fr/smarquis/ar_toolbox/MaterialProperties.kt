package fr.smarquis.ar_toolbox

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import com.google.ar.sceneform.rendering.Material
import com.google.ar.sceneform.rendering.MaterialFactory.MATERIAL_COLOR
import com.google.ar.sceneform.rendering.MaterialFactory.MATERIAL_METALLIC
import com.google.ar.sceneform.rendering.MaterialFactory.MATERIAL_REFLECTANCE
import com.google.ar.sceneform.rendering.MaterialFactory.MATERIAL_ROUGHNESS

class MaterialProperties(
    @field:ColorInt var color: Int = DEFAULT_COLOR,
    @field:IntRange(from = 0, to = 100) var metallic: Int = DEFAULT_METALLIC,
    @field:IntRange(from = 0, to = 100) var roughness: Int = DEFAULT_ROUGHNESS,
    @field:IntRange(from = 0, to = 100) var reflectance: Int = DEFAULT_REFLECTANCE,
) {
    companion object {

        private const val DEFAULT_COLOR = Color.WHITE
        private const val DEFAULT_METALLIC = 0
        private const val DEFAULT_ROUGHNESS = 40
        private const val DEFAULT_REFLECTANCE = 50

        val DEFAULT = MaterialProperties()
    }

    fun update(material: Material?, block: (MaterialProperties.() -> Unit) = {}) {
        block(this)
        material?.apply {
            setFloat3(MATERIAL_COLOR, color.toArColor())
            setFloat(MATERIAL_METALLIC, metallic / 100F)
            setFloat(MATERIAL_ROUGHNESS, roughness / 100F)
            setFloat(MATERIAL_REFLECTANCE, reflectance / 100F)
        }
    }
}
