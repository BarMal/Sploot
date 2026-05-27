plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure JVM module — scorers, models, and comparator.
// No Android or Room dependencies here.

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
