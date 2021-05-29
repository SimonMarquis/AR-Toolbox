buildscript {
    repositories {
        google()
        mavenCentral()
    }
    @Suppress("GradleDependency", "GradlePluginVersion")
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:_")
        classpath("com.android.tools.build:gradle:_")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
