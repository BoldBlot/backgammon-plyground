import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * A fingerprint of the code the GPU games depend on (the shaders and Vulkan code, PureTD's C engine and its JNI bridge, the
 * games search): a passing GPU check stays valid until it changes, across app updates.
 */
val gpuCode: String = run {
    val root = rootProject.projectDir
    val src = "app/src/main/java/com/boldblot/backgammonplyground/engine"
    val files = listOf("engine/vkgames", "engine/puretd", "app/src/main/cpp").flatMap { d ->
        File(root, d).walkTopDown().filter { f -> f.isFile && "/out/" !in f.path && !f.name.endsWith(".md") && f.name != ".gitignore" }.toList()
    } + listOf("GpuGames.kt", "VkGamesNative.kt", "GamesSearch.kt").map { File(root, "$src/$it") }
    val md = MessageDigest.getInstance("SHA-256")
    files.sortedBy { it.relativeTo(root).invariantSeparatorsPath }.forEach { f ->
        md.update(f.relativeTo(root).invariantSeparatorsPath.toByteArray()); md.update(f.readBytes())
    }
    md.digest().take(6).joinToString("") { "%02x".format(it) }
}

// Release signing is read from signing.properties (not committed). Without it
// the release build falls back to the debug key so it can still be installed.
val signingProps = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.boldblot.backgammonplyground"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.boldblot.backgammonplyground"
        minSdk = 26
        targetSdk = 36
        // Play's upload code: one more for every bundle uploaded to Play (internal or closed). It is
        // also the build number the app shows, and each upload moves the version's last digit on.
        versionCode = 106
        versionName = "0.9.25"
        buildConfigField("String", "BUILD_LABEL", "\"$versionCode\"")
        buildConfigField("String", "GPU_CODE", "\"$gpuCode\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    signingConfigs {
        if (signingProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
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

    androidResources {
        // Engine databases are memory-mapped/read after extraction; keep them stored uncompressed.
        noCompress += listOf("bd", "wd")
    }


    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { test ->
            // Engine-backed unit tests run against a host build of the JNI library
            // (see scripts/host-tests.sh); they are skipped when it is not provided.
            providers.gradleProperty("bgHostLib").orNull?.let { test.systemProperty("bg.hostlib", it) }
            // The GPU parity tests (GpuParityTest), on when asked: scripts/host-tests.sh decides.
            providers.gradleProperty("bgGpuTests").orNull?.let { test.systemProperty("bg.gputests", it) }
            test.systemProperty("bg.datadir", file("src/main/assets/puretd").absolutePath)
            // Opponent PR calibration (OpponentPrCalibration): games per opponent, off when unset.
            providers.gradleProperty("bgCalibrateGames").orNull?.let { test.systemProperty("bg.calibrate.games", it) }
            providers.gradleProperty("bgCalibrateSet").orNull?.let { test.systemProperty("bg.calibrate.set", it) }
            // Simulated matches are exported here as .mat files (checked against gnubg by scripts/check-mat.py).
            test.systemProperty("bg.exportdir", layout.buildDirectory.dir("mat-export").get().asFile.absolutePath)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
