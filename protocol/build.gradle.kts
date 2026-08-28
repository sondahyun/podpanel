plugins {
    // Pure JVM module: no Android, so AGP's built-in Kotlin does not apply here and the
    // standalone Kotlin plugin is required. Version must match AGP 9.0.1's built-in
    // Kotlin (2.2.10) so both modules compile against the same stdlib.
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed") }
}
