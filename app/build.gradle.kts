plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.slimenull.wcxjvpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.slimenull.wcxjvpn"
        minSdk = 34
        targetSdk = 36
        versionCode = 5
        versionName = "1.7"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "wcxjvpn123"
            keyAlias = "wcxjvpn"
            keyPassword = "wcxjvpn123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        // 发布版不打包 frida gadget(仅逆向调试用,会开监听端口)
        jniLibs.excludes += setOf("**/libgadget.so", "**/libgadget.config.so")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
