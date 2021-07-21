plugins {
    id("com.android.application")
    kotlin("android")
}

val versionMajor = 1
val versionMinor = 11
val versionPatch = 0
val versionBuild = 0

android {
    compileSdk = 30
    defaultConfig {
        applicationId = "fr.smarquis.ar_toolbox"
        minSdk = 24
        targetSdk = 30
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
        }
    }
    lint {
        textReport = true
        disable("ObsoleteLintCustomCheck")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(Kotlin.stdlib.jdk7)

    implementation(AndroidX.activityKtx)
    implementation(AndroidX.appCompat)
    implementation(AndroidX.browser)
    implementation(AndroidX.constraintLayout)
    implementation(AndroidX.coordinatorLayout)
    implementation(AndroidX.core)
    implementation(AndroidX.fragmentKtx)
    implementation(AndroidX.lifecycle.common)
    implementation(AndroidX.lifecycle.liveDataCoreKtx)
    implementation(AndroidX.lifecycle.viewModelKtx)
    implementation(AndroidX.preferenceKtx)

    implementation(Google.Android.material)

    implementation(Google.Ar.core)
    implementation("com.gorisse.thomas.sceneform:sceneform:_")

    testImplementation(Testing.junit4)

    androidTestImplementation(AndroidX.Test.ext.junit)
    androidTestImplementation(AndroidX.Test.espresso.core)
}