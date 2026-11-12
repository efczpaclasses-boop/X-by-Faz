import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.faz.xbyfaz"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.faz.xbyfaz"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        }
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                val properties = Properties().apply { keystorePropertiesFile.inputStream().use { load(it) } }
                storeFile = rootProject.file(properties.getProperty("storeFile"))
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:navigation"))
    implementation(project(":core:ui"))
    implementation(project(":core:web"))
    implementation(project(":core:database"))
    implementation(project(":core:auth"))
    implementation(project(":core:network"))
    implementation(project(":core:player"))
    implementation(project(":feature:home"))
    implementation(project(":feature:videos"))
    implementation(project(":feature:search"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:history"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer)
    implementation(libs.coil)
    implementation(libs.okhttp)
    implementation(libs.webkit)
    implementation(libs.nanohttpd)
    implementation(libs.zxing)
    ksp(libs.hilt.compiler)
}
