plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.sondahyun.podpanel"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.sondahyun.podpanel"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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
    implementation(project(":protocol"))
    implementation(project(":design"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    // AGP's built-in Kotlin does not wire kotlin("test") for us, so the coordinates
    // are spelled out and pinned to the same Kotlin the compiler uses.
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.10")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.2.10")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

/**
 * The widget's sizing rules and the screen's message selection are plain functions over
 * plain values, so they run here rather than needing a device.
 */
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events("passed", "failed") }
}
