import com.android.build.gradle.BaseExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Application version code will follow this convention:
 * 'aabbccdde'
 *  └┤└┤└┤└┤└──ABI
 *   │ │ │ └──build
 *   │ │ └──patch
 *   │ └──minor
 *   └──major
 * Example: 1.2.3.4 -> 10203040 (universal)
 */
class VersioningPlugin : Plugin<Project> {

    companion object {
        private const val EXTENSION_KEY = "versioning"
    }

    override fun apply(project: Project) {
        val version = project.extensions.create(EXTENSION_KEY, Version::class.java)
        project.afterEvaluate {
            validateExtension(version)
            validateDefaultConfig(project, version)
        }
    }

    private fun validateExtension(version: Version) {
        if (!version.hasBeenConfigured()) {
            throw GradleException(
                """
                    |The [$EXTENSION_KEY] extension has not been configured:
                    |
                    |$EXTENSION_KEY {
                    |    major = 1
                    |    minor = 0
                    |    patch = 0
                    |    build = 0
                    |    label = null
                    |}
                    """.trimMargin()
            )
        }
    }

    private val Project.android: BaseExtension
        get() = extensions.findByName("android") as BaseExtension

    private fun validateDefaultConfig(project: Project, version: Version) {
        val defaultConfig = project.android.defaultConfig
        if (defaultConfig.versionName != version.name() || defaultConfig.versionCode != version.code()) {
            throw GradleException(
                """
                    |versionName and versionCode must be set on the defaultConfig:
                    |
                    |android {
                    |    defaultConfig {
                    |        versionName $EXTENSION_KEY.name()
                    |        versionCode $EXTENSION_KEY.code()
                    |    }
                    |}
                    """.trimMargin()
            )
        }
    }

}

open class Version(
    var major: Int = 0,
    var minor: Int = 0,
    var patch: Int = 0,
    var build: Int = 0,
    var qualifier: String? = null
) {

    fun code(): Int = major * 1000000 + minor * 10000 + patch * 100 + build

    fun name(): String = "$major.$minor.$patch${qualifier.orEmpty()}"

}

private fun Version.hasBeenConfigured(): Boolean = major != 0 || minor != 0 || patch != 0 || build != 0 || qualifier != null
