import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

val revenueCatKey = Properties().apply {
    val local = rootProject.file("local.properties")
    if (local.exists()) local.inputStream().use { load(it) }
}.getProperty("revenuecat.apiKey", "")
val quotedRevenueCatKey = "\"" + revenueCatKey.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "dev.andrej.echo"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "dev.andrej.echo"
        minSdk = 29
        targetSdk = 36
        buildConfigField("String", "REVENUECAT_API_KEY", quotedRevenueCatKey)
        versionCode = 2
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk { abiFilters += "arm64-v8a" }
        // An unoptimised whisper.cpp is tens of times slower, so debug builds get -O3 too.
        externalNativeBuild { cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" } }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Device tests must never install over the user's real local notes database.
    testBuildType = "uitest"

    buildTypes {
        create("uitest") {
            initWith(getByName("debug"))
            buildConfigField("String", "REVENUECAT_API_KEY", "\"\"")
            applicationIdSuffix = ".uitest"
            matchingFallbacks += "debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        // Echo calls sherpa-onnx's C API itself (see diarize_jni.cpp); its JNI and C++ wrappers go unused.
        jniLibs.excludes += listOf("**/libsherpa-onnx-jni.so", "**/libsherpa-onnx-cxx-api.so")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

sqldelight {
    databases {
        create("EchoDatabase") {
            packageName = "dev.andrej.echo.data.db"
        }
    }
}

dependencies {
    constraints {
        implementation(libs.androidx.fragment) {
            because("Google Play services pulls Fragment 1.1.0, which is incompatible with Activity Result APIs.")
        }
    }
    implementation(libs.revenuecat.purchases)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.litertlm.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.sherpa.onnx)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    add("uitestImplementation", libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

/**
 * Builds, installs, and relaunches the debug app on the connected device.
 *
 * Pair with Gradle's continuous build for an auto-reload loop:
 *   ./gradlew -t installAndRun
 */
tasks.register<Exec>("installAndRun") {
    group = "install"
    description = "Installs the debug APK and launches it on the connected device."

    dependsOn("installDebug")

    val adb = providers.environmentVariable("ANDROID_HOME")
        .map { "$it/platform-tools/adb" }
        .getOrElse("adb")
    val launchTarget = "${android.defaultConfig.applicationId}/.MainActivity"

    commandLine(adb, "shell", "am", "start", "-n", launchTarget)
}
