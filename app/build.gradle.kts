import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val replicateApiKey: String = localProps.getProperty("REPLICATE_API_KEY", "")
val cloudinaryCloudName: String = localProps.getProperty("CLOUDINARY_CLOUD_NAME", "")
val uploadTestImage: String = localProps.getProperty("UPLOAD_TEST_IMAGE", "")
val actionFigureTestImage: String = localProps.getProperty("ACTION_FIGURE_TEST_IMAGE", "")

plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "rs.smobile.speak2act"
    compileSdk = 36

    defaultConfig {
        applicationId = "rs.smobile.speak2act"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "REPLICATE_API_KEY", "\"$replicateApiKey\"")
        buildConfigField("String", "UPLOAD_TEST_IMAGE", "\"$uploadTestImage\"")
        buildConfigField("String", "ACTION_FIGURE_TEST_IMAGE", "\"$actionFigureTestImage\"")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"$cloudinaryCloudName\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // Firebase AI Logic
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    // ML Kit Text Recognition
    implementation(libs.text.recognition)
    implementation(libs.kotlinx.coroutines.play.services)
    // Cloudinary
    implementation(libs.cloudinary.android)
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    // OkHttp
    implementation(libs.okhttp)
    // Coil for image loading in Compose
    implementation(libs.coil.compose)
    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}