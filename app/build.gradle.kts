plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("android")
}

android {
    namespace = "com.crossa.androiddemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crossa.androiddemo"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "CROSSA_ARTIFACT", "\"release\"")
        buildConfigField("String", "CROSSA_ARTIFACT_VERSION", "\"0.1.0\"")
        buildConfigField("String", "CROSSA_ARTIFACT_SHA256", "\"0de94787b51e1754b69f51325e984654249059bc59a65e48f72cd65efa3d158c\"")
        buildConfigField("String", "CROSSA_SOURCE_COMMIT", "\"e13bde22f4210f0d0d3e508c28f32464aba105bf\"")
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            buildConfigField("String", "BENCHMARK_BUILD", "\"debug-app\"")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BENCHMARK_BUILD", "\"release\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(files("libs/crossa-generated-release.aar"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
