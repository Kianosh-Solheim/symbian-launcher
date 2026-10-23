plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

// Version is supplied by CI (run number) so every release upgrades cleanly over the last one.
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val ciVersionName = System.getenv("VERSION_NAME") ?: "2.0-dev"

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "no.kianosh.symbianlauncher"
    minSdk = 26
    targetSdk = 36
    versionCode = ciVersionCode
    versionName = ciVersionName
  }

  // Only ship English and Norwegian resources from libraries; drops dozens of unused translations.
  androidResources {
    localeFilters += setOf("en", "nb")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
      if (keystorePath != null) {
        storeFile = file(keystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "launcher"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = if (System.getenv("KEYSTORE_PATH") != null) {
        signingConfigs.getByName("release")
      } else {
        signingConfigs.getByName("debug")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
  }
  packaging {
    resources {
      excludes += listOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "/META-INF/*.version",
        "/META-INF/*.kotlin_module",
        "DebugProbesKt.bin",
        "kotlin-tooling-metadata.json",
      )
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.zxing.core)
  // Installs the ahead-of-time compilation profiles that ship inside Compose, so a sideloaded
  // APK starts and scrolls as smoothly as a Play Store install.
  implementation(libs.androidx.profileinstaller)
}
