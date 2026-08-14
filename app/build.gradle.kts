import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("keystore/keystore.properties")
if (keystorePropsFile.exists()) keystoreProps.load(keystorePropsFile.inputStream())
val hasKeystore = keystorePropsFile.exists()

android {
    namespace = "bp.goalsgames.blockbalance"
    compileSdk = 36

    defaultConfig {
        applicationId = "bp.goalsgames.blockbalance"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        localeFilters += listOf("en")
        // The art pack at the repository root doubles as the design source: it holds
        // masters that feed the launcher icon and the derived sprites, plus screens
        // this game has no feature for. None of that belongs in the APK.
        ignoreAssetsPatterns += listOf(
            "*_Notifications_Screen.webp",
            "*_Nowifi_Screen.webp",
            "Game_Name.png",
            "icon.png",
            "icon.webp",
            "block_asset_main.webp",
            "button_blank.jpg",
        )
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
            // Art pack lives at the repository root and is shared with the design pipeline.
            assets.srcDirs("src/main/assets", rootProject.layout.projectDirectory.dir("assets"))
        }
        getByName("test") { kotlin.srcDirs("src/test/kotlin") }
        getByName("androidTest") { kotlin.srcDirs("src/androidTest/kotlin") }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (hasKeystore) create("release") {
            storeFile = file(keystoreProps["storeFile"] as String)
            storePassword = keystoreProps["storePassword"] as String
            keyAlias = keystoreProps["keyAlias"] as String
            keyPassword = keystoreProps["keyPassword"] as String
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
            )
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        // Dependency versions are pinned on purpose: they are the newest releases
        // that still build against compileSdk 36.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
