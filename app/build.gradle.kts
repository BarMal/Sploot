plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

fun resolvedVersionName(): String =
    providers.environmentVariable("RELEASE_VERSION_NAME")
        .orElse(providers.environmentVariable("GITHUB_REF_NAME").map { it.removePrefix("v") })
        .orElse("0.1.0")
        .get()

fun resolvedVersionCode(): Int =
    providers.environmentVariable("RELEASE_VERSION_CODE")
        .map(String::toInt)
        .orElse(
            providers.environmentVariable("GITHUB_REF_NAME").map { tag ->
                val parts = tag.removePrefix("v").split(".").mapNotNull(String::toIntOrNull)
                if (parts.size == 3) parts[0] * 10_000 + parts[1] * 100 + parts[2] else 1
            }
        )
        .orElse(1)
        .get()

android {
    namespace   = "com.sploot.app"
    compileSdk  = 36

    defaultConfig {
        applicationId          = "com.sploot.app"
        minSdk                 = 26
        targetSdk              = 35
        versionCode            = resolvedVersionCode()
        versionName            = resolvedVersionName()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":whoop-ble"))
    implementation(project(":garmin-import"))
    implementation(project(":signal-proc"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.vico.compose)
    implementation(libs.timber)
    implementation(libs.core.ktx)
    implementation(libs.health.connect.client)

    testImplementation(libs.junit)
}
