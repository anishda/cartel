import java.security.MessageDigest
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

// Bibliothèques natives de sherpa-onnx (Apache-2.0, moteur de la voix neuronale) : téléchargées à la compilation, avec
// empreinte vérifiée, au lieu d'être commitées (environ 27 Mo par architecture). Seules libonnxruntime.so et
// libsherpa-onnx-jni.so servent à l'app.
val sherpaVersion = "2.0.1"
val sherpaArchiveSha256 = "39003753ec293425ed56723ea9b972b98841a490af3311670c3060548d654330"
val sherpaDir = layout.buildDirectory.dir("sherpa-onnx")
val fetchSherpaOnnx = tasks.register("fetchSherpaOnnx") {
    // Valeurs recopiées en local : un doLast ne peut pas référencer le script (cache de configuration).
    val version = sherpaVersion
    val expectedSha256 = sherpaArchiveSha256
    val root = sherpaDir
    val archive = root.map { it.file("sherpa-onnx-v$version-android.tar.bz2") }
    val jniLibs = root.map { it.dir("jniLibs") }
    outputs.dir(jniLibs)
    doLast {
        val dir = root.get().asFile.also { it.mkdirs() }
        val file = archive.get().asFile
        if (!file.exists()) {
            ProcessBuilder(
                "curl", "-fsSL", "-o", file.absolutePath,
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$version/sherpa-onnx-v$version-android.tar.bz2",
            ).inheritIO().start().waitFor().also { check(it == 0) { "Téléchargement de sherpa-onnx impossible" } }
        }
        val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == expectedSha256) { "Empreinte inattendue pour ${file.name} : $actual" }
        if (!jniLibs.get().asFile.resolve("arm64-v8a/libonnxruntime.so").exists()) {
            ProcessBuilder("tar", "xjf", file.absolutePath, "-C", dir.absolutePath).inheritIO().start().waitFor()
                .also { check(it == 0) { "Extraction de sherpa-onnx impossible" } }
        }
    }
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
        // Les téléphones d'aujourd'hui : l'ABI x86 ne sert qu'aux émulateurs, et chaque ABI coûte 20 à 30 Mo de plus.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("sherpa-onnx/jniLibs").get().asFile)
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Les API C et C++ de sherpa-onnx ne servent pas : l'app passe par JNI.
        jniLibs {
            excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    // Adresse du serveur de packs : -PpackBaseUrl=https://… (en développement : -PpackBaseUrl=http://10.0.2.2:8080/ pour
    // le Mac vu de l'émulateur). Par défaut, debug comme release : la dernière release GitHub, publiée chaque semaine par
    // .github/workflows/packs.yml. 10.0.2.2 n'existe que dans l'émulateur : sur un vrai téléphone, le serveur est injoignable.
    val releasePackUrl = "https://github.com/anishda/cartel/releases/latest/download/"
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "PACK_BASE_URL", "\"${findProperty("packBaseUrl") ?: releasePackUrl}\"")
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

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach { dependsOn(fetchSherpaOnnx) }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
