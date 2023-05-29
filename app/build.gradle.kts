import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.kotlin)
    alias(libs.plugins.google.oss)
}

val versionMajor = 1
val versionMinor = 13
val versionPatch = 0
val versionBuild = 0

android {
    namespace = "fr.smarquis.ar_toolbox"
    compileSdk = 33
    defaultConfig {
        applicationId = "fr.smarquis.ar_toolbox"
        minSdk = 24
        targetSdk = 33
        versionCode = versionMajor * 1000000 + versionMinor * 10000 + versionPatch * 100 + versionBuild
        versionName = "$versionMajor.$versionMinor.$versionPatch"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        viewBinding = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        textReport = true
        lintConfig = file("lint.xml")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.preference)
    implementation(libs.google.android.material)
    implementation(libs.google.ar.core)
    implementation(libs.google.ar.sceneform.assets)
    implementation(libs.google.ar.sceneform.core)
    implementation(libs.google.ar.sceneform.rendering)
    implementation(libs.google.ar.sceneform.sceneformBase)
    implementation(libs.google.ar.sceneform.ux)
    implementation(libs.google.oss)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
