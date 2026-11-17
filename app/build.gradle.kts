import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.lumasr.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.lumasr.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 10
        versionName = "0.4.3-lab"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    androidResources {
        noCompress += listOf("bin", "param")
    }

    buildTypes {
        release {
            // 调试/自用阶段关闭 R8，构建更快、堆栈和类名可读。
            // 正式分发前把下面两项改回 true，并确认 proguard-rules.pro。
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val debugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")
            if (debugKeystore.exists()) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Release lint is the memory peak of this 230 MB build; keep it off
        // for local iteration and run `lint` manually when needed.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(project(":engine97"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
