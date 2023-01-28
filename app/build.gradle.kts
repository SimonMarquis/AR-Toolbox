plugins {
    id("com.android.application")
    id("com.google.android.gms.oss-licenses-plugin")
    kotlin("android")
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
    lint {
        textReport = true
        disable += "ObsoleteLintCustomCheck"
    }
}

dependencies {
    implementation(AndroidX.activity.ktx)
    implementation(AndroidX.appCompat)
    implementation(AndroidX.browser)
    implementation(AndroidX.constraintLayout)
    implementation(AndroidX.coordinatorLayout)
    implementation(AndroidX.core)
    implementation(AndroidX.fragment.ktx)
    implementation(AndroidX.lifecycle.common)
    implementation(AndroidX.lifecycle.liveDataKtx)
    implementation(AndroidX.lifecycle.viewModelKtx)
    implementation(AndroidX.preference.ktx)

    implementation(Google.Android.material)

    implementation(Google.Ar.core)
    implementation(Google.Ar.sceneform.assets)
    implementation(Google.Ar.sceneform.core)
    implementation(Google.Ar.sceneform.rendering)
    implementation(Google.Ar.sceneform.sceneformBase)
    implementation(Google.Ar.sceneform.ux)

    implementation(Google.android.playServices.openSourceLicenses)

    testImplementation(Testing.junit4)

    androidTestImplementation(AndroidX.Test.ext.junit)
    androidTestImplementation(AndroidX.Test.espresso.core)
}