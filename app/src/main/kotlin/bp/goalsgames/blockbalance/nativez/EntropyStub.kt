package bp.goalsgames.blockbalance.nativez

import bp.goalsgames.blockbalance.BuildConfig

/**
 * Thin JNI wrapper for the native "seasoning" library. The .so is present in
 * the APK only when `gray.enableNativeStub = true` in gray.properties (which
 * flips [BuildConfig.NATIVE_STUB_ENABLED] to true and gates the
 * externalNativeBuild block in app/build.gradle.kts).
 *
 * Nothing in the release flow *requires* the .so to be loaded — it is a
 * fingerprint aid, not a runtime dependency. Calls in the main path are
 * guarded by [isAvailable] so a project that ships without the .so does not
 * crash on class init.
 */
object EntropyStub {

    private val loaded: Boolean = if (BuildConfig.NATIVE_STUB_ENABLED) {
        runCatching { System.loadLibrary("entropy_stub") }.isSuccess
    } else {
        false
    }

    val isAvailable: Boolean get() = loaded

    /**
     * Runs the native churn over [payload] and returns an integer that is a
     * deterministic function of the payload bytes, [salt] and the per-project
     * constants baked into the .so. Callers use it as a checksum-shaped decoy
     * signal; the actual value is not consumed by any production code path.
     */
    fun churn(payload: ByteArray, salt: Long): Int {
        if (!loaded) return 0
        return nativeChurn(payload, salt)
    }

    @JvmStatic
    private external fun nativeChurn(payload: ByteArray, salt: Long): Int
}
