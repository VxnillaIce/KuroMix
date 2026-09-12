plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // HyperOS-styled Compose UI kit
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui-android:0.9.3")

    // Material Icons
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // DataStore for preferences and whitelist
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Navigation 3
    implementation("androidx.navigation3:navigation3-runtime:1.1.4")

    // Root shell access (su exec, output parsing, root service)
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
    implementation("com.github.topjohnwu.libsu:service:5.2.2")

    // HyperIsland ToolKit for Xiaomi Super Island
    implementation("io.github.d4viddf:hyperisland_kit:0.4.4")

    // Xposed/LSPosed API — hooks are compiled against this, provided by the
    // framework at runtime on the rooted device, never bundled into the APK.
    compileOnly(libs.xposed.api)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
