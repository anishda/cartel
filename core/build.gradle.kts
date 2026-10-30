import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinxSerialization)
}

// Logique pure, sans interface : domaine du cartel, catalogue d'œuvres, format des packs.
// Partagé par :shared (app) et :pack-builder (outil JVM qui fabrique les packs).
kotlin {
    iosArm64()
    iosSimulatorArm64()
    // Cible Java 11 explicite : sans elle, le bytecode suit le JDK du démon Gradle (25), que :pack-builder (JDK 21)
    // ne sait pas charger.
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    androidLibrary {
        namespace = "app.audioguide.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.androidx.sqlite)
            api(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
