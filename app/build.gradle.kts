plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20"
}

android {
    namespace = "com.kuromify.kuromix"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kuromify.kuromix"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.2"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        )
    }
}

dependencies {
    // Compose
    implementation(platform(libs.androidx.compose.bom.v20240900))
    implementation(libs.androidx.activity.compose.v192)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.core.ktx.v1170)
    implementation(libs.androidx.lifecycle.runtime.ktx.v284)

    // HyperOS-styled Compose UI kit
    implementation(libs.miuix.ui.android)
    implementation(libs.miuix.preference.android)
    implementation(libs.miuix.icons.android)
    implementation(libs.miuix.blur.android)
    implementation(libs.miuix.navigation3.ui.android)

    // Material Icons
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    // DataStore for preferences and whitelist
    implementation(libs.androidx.datastore.preferences)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)

    // Root shell access (su exec, output parsing, root service)
    implementation(libs.core)
    implementation(libs.service)

    // HyperIsland ToolKit for Xiaomi Super Island
    implementation(libs.hyperisland.kit)

    // Xposed/LSPosed API — hooks are compiled against this, provided by the
    // framework at runtime on the rooted device, never bundled into the APK.
    compileOnly(libs.xposed.api)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
