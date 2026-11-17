import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.foundation)
    implementation(libs.koin.android)
}

android {
    namespace = "app.audioguide"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.anishda.cartel"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        buildConfig = true
    }
    // Adresse du serveur de packs : -PpackBaseUrl=https://… (en développement : le Mac, vu de l'émulateur).
    // En release : la dernière release GitHub, publiée chaque semaine par .github/workflows/packs.yml.
    val releasePackUrl = "https://github.com/anishda/cartel/releases/latest/download/"
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "PACK_BASE_URL", "\"${findProperty("packBaseUrl") ?: "http://10.0.2.2:8080/"}\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("String", "PACK_BASE_URL", "\"${findProperty("packBaseUrl") ?: releasePackUrl}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
