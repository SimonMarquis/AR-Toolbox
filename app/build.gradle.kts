plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
}

val versionMajor = 1
val versionMinor = 10
val versionPatch = 0
val versionBuild = 0

android {
    compileSdkVersion(30)
    defaultConfig {
        applicationId = "fr.smarquis.ar_toolbox"
        minSdkVersion(24)
        targetSdkVersion(30)
        versionCode = versionMajor * 1000000 + versionMinor * 10000 + versionPatch * 100 + versionBuild
        versionName = "$versionMajor.$versionMinor.$versionPatch"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    lintOptions {
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.4.10")

    implementation("androidx.activity:activity-ktx:1.1.0")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.browser:browser:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.2")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.1.0")
    implementation("androidx.core:core-ktx:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.2.5")
    implementation("androidx.lifecycle:lifecycle-common:2.2.0")
    implementation("androidx.lifecycle:lifecycle-livedata-core-ktx:2.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.2.0")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("com.google.android.material:material:1.3.0-alpha03")

    implementation("com.google.ar:core:1.20.0")
    implementation("com.google.ar.sceneform:assets:1.17.1")
    implementation("com.google.ar.sceneform:core:1.17.1")
    implementation("com.google.ar.sceneform:rendering:1.17.1")
    implementation("com.google.ar.sceneform:sceneform-base:1.17.1")
    implementation("com.google.ar.sceneform.ux:sceneform-ux:1.17.1")

    testImplementation("junit:junit:4.13.1")

    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
}