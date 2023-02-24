plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin) apply false
    alias(libs.plugins.google.oss) apply false
    alias(libs.plugins.spotless)
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
