import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import com.android.build.gradle.internal.api.LibraryVariantOutputImpl
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
        private val ABI_CODES = mapOf(
            "universal" to 0,
            "armeabi-v7a" to 1,
            "arm64-v8a" to 2,
            "x86" to 3,
            "x86_64" to 4
        )
        private const val EXTENSION_KEY = "versioning"
    }

    override fun apply(project: Project) {
        val version = project.extensions.create(EXTENSION_KEY, Version::class.java)
        project.afterEvaluate {
            validateExtension(version)
            validateDefaultConfig(project, version)
            when {
                project.plugins.hasPlugin("com.android.application") -> renameApplication(project, version)
                project.plugins.hasPlugin("com.android.library") -> renameLibrary(project, version)
            }
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

    private val BaseExtension.app: AppExtension
        get() = this as AppExtension

    @SuppressWarnings("DefaultLocale")
    private fun renameApplication(project: Project, version: Version) {
        project.android.app.applicationVariants.all { variant ->
            // Rename APKs
            variant.outputs.all { output ->
                @Suppress("LABEL_NAME_CLASH")
                val apk = output as? ApkVariantOutputImpl ?: return@all
                if (project.android.splits.abi.isEnable) {
                    val abi = apk.getFilter("ABI") ?: "universal"
                    apk.versionCodeOverride = variant.versionCode * 10 + ABI_CODES.getValue(abi)
                    apk.outputFileName = "${variant.applicationId}-${version.name()}-$abi-${variant.baseName}.apk"
                } else {
                    apk.outputFileName = "${variant.applicationId}-${version.name()}-${variant.baseName}.apk"
                }
                project.logger.lifecycle(apk.outputFileName)
            }

            // Rename AABs
            val bundleTask = project.tasks.getByName("bundle${variant.name.capitalize()}")
            val renamingTask = project.tasks.register("${bundleTask.name}Renaming") { task ->
                task.doLast {
                    val directory = "${project.buildDir}/outputs/bundle/${variant.name}"
                    val aab = project.fileTree(directory) { it.include("*.aab") }.firstOrNull() ?: return@doLast
                    val filename = "${variant.applicationId}-${version.name()}-${variant.baseName}.aab"
                    aab.renameTo(project.file("$directory/$filename"))
                    project.logger.lifecycle(filename)
                }
            }
            bundleTask.finalizedBy(renamingTask)
        }
    }

    private val BaseExtension.lib: LibraryExtension
        get() = this as LibraryExtension

    private fun renameLibrary(project: Project, version: Version) {
        project.android.lib.libraryVariants.all { variant ->
            variant.outputs.all { output ->
                @Suppress("LABEL_NAME_CLASH")
                val aar = (output as? LibraryVariantOutputImpl) ?: return@all
                val archivesBaseName = project.property("archivesBaseName")
                aar.outputFileName = "$archivesBaseName-${version.name()}-${variant.baseName}.aar"
                project.logger.lifecycle(aar.outputFileName)
            }
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
