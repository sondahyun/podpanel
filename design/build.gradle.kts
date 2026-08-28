plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.sondahyun.podpanel.design"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(platform("androidx.compose:compose-bom:2026.08.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.ui:ui-text")
    // Deliberately no Material: this module *is* the design system. Pulling Material in
    // would put a second, conflicting set of shapes and colours within reach.
    debugImplementation("androidx.compose.ui:ui-tooling")
    api("androidx.compose.ui:ui-tooling-preview")
}
