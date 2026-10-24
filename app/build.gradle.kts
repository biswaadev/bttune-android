import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.kotlin.serialization)
    kotlin("kapt")
    id("com.google.gms.google-services")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
}


val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val googleApiKey: String = localProperties.getProperty("GOOGLE_API_KEY", "")
val statsApiKey: String = localProperties.getProperty("STATS_API_KEY", "")

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.bt.bttune"
    //noinspection GradleDependency
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bt.bttune"
        minSdk = 26
        targetSdk = 35
        resConfigs("en")
        versionCode = 173
        versionName = "6.0.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GOOGLE_API_KEY", googleApiKey.asBuildConfigString())
        buildConfigField("String", "STATS_API_KEY", statsApiKey.asBuildConfigString())
        manifestPlaceholders["redirectHost"] = "play.bttune.app"
        manifestPlaceholders["redirectScheme"] = "https"
        ndk {
            // Filter for common ABIs to reduce APK size
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    signingConfigs {
        getByName("debug") {
            isV1SigningEnabled = true
            isV2SigningEnabled = true
            val keystoreFile = file("bttune.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "bttuneapppass"
                keyAlias = "bttune"
                keyPassword = "bttuneapppass"
            } else if (System.getenv("MUSIC_DEBUG_SIGNING_STORE_PASSWORD") != null) {
                storeFile = file(System.getenv("MUSIC_DEBUG_KEYSTORE_FILE"))
                storePassword = System.getenv("MUSIC_DEBUG_SIGNING_STORE_PASSWORD")
                keyAlias = "bttune"
                keyPassword = System.getenv("MUSIC_DEBUG_SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "IS_NIGHTLY", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "IS_NIGHTLY", "false")
            applicationIdSuffix = ".debug"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xcontext-receivers")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    lint {
        disable += "MissingTranslation"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/*.md"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/*.version"
            excludes += "org/bouncycastle/**/*.properties"
            excludes += "**/*.bin.properties"
            excludes += "com/google/api/client/**/*.p12"
            excludes += "com/google/api/client/**/*.jks"
            excludes += "**/*.proto"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.all {
    exclude(group = "org.json", module = "json")
}

dependencies {
    implementation("dev.chrisbanes.haze:haze:0.7.3")
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)

    implementation(libs.material3)
    implementation(libs.palette)
    implementation(projects.materialColorUtilities)

    implementation(libs.coil)
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("com.github.jeziellago:compose-markdown:0.5.4")
    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.squigglyslider)

    // Google Cast & local streaming proxy
    implementation("com.google.android.gms:play-services-cast-framework:22.3.1")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    implementation(libs.room.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.blurry)
    implementation(libs.material.ripple)
    implementation(libs.material.icons.extended)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.graphics.shapes)
    implementation(libs.work.runtime.ktx)
    implementation(libs.constraintlayout)
    implementation(libs.constraintlayout.compose)
    implementation(libs.foundation)
    implementation(libs.ui.graphics)
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-auth")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.hilt)
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.github.skydoves:cloudy:0.2.7")
    kapt(libs.hilt.compiler)

    // Google Auth & Drive API
    implementation("androidx.credentials:credentials:1.3.0-rc01")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0-rc01")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("com.google.api-client:google-api-client-android:1.33.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.auth:google-auth-library-oauth2-http:1.16.0") {
        exclude(group = "org.apache.httpcomponents")
    }

    implementation(projects.innertube)
    implementation(projects.kugou)
    implementation(projects.lrclib)
    implementation(projects.kizzy)
    implementation(projects.spotify)
    implementation(project(":jossredconnect"))
    implementation(project(":shazamkit"))
    implementation(project(":betterlyrics"))

    implementation(libs.ktor.client.core)

    coreLibraryDesugaring(libs.desugaring)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.liquid.glass)
    implementation(libs.liquid.glass.shape)

    implementation(libs.timber)
    testImplementation(libs.junit)
}
