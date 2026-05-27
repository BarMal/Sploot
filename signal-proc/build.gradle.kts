plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure JVM module — zero Android dependencies.
// All signal-processing functions are pure (input arrays → output) so they
// can be unit-tested without a device or emulator.

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
