import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

tasks.matching { it.name.contains("generateDebugBuildConfig") || it.name.contains("generateReleaseBuildConfig") }.configureEach {
    if (localPropertiesFile.exists()) {
        inputs.file(localPropertiesFile)
    }
}

android {
    namespace = "com.warzone.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.warzone.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        val serverUrl = localProperties.getProperty("server.url", "http://10.0.2.2:8000")
        buildConfigField("String", "API_BASE_URL", "\"$serverUrl\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.meta.wda.core)
    implementation(libs.meta.wda.camera)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.coordinatorlayout)
    implementation(libs.lifecycle.runtime)
    implementation(libs.exifinterface)
    implementation(libs.work.runtime)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coroutines)
    implementation(libs.coil)
    implementation(libs.gson)
}
