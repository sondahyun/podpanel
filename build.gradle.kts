plugins {
    id("com.android.application") version "9.3.2" apply false
    id("com.android.library") version "9.3.2" apply false
    // Pure-JVM modules are outside AGP's built-in Kotlin, so they need the standalone
    // plugin. Version-matched to AGP's built-in Kotlin (2.2.10) so both compile against
    // the same stdlib.
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
