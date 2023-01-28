buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:_")
        classpath(Android.tools.build.gradlePlugin)
        classpath(Google.android.openSourceLicensesPlugin)
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.diffplug.spotless")
}

spotless {
    format("misc") {
        target("**/*.md", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlin {
        target("**/src/**/*.kt", "**/src/**/*.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}
