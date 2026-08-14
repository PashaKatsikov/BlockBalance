package bp.goalsgames.blockbalance.net

import android.app.Application
import bp.goalsgames.blockbalance.BuildConfig
import bp.goalsgames.blockbalance.pkg0.Trace
import bp.goalsgames.blockbalance.pkg0.UrlGuard
import bp.goalsgames.blockbalance.cfg.AttrHub
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Application entry. Two responsibilities: initialise Firebase (best-effort)
 * and prime the AppsFlyer SDK before any activity runs.
 *
 * See `.cursor/rules/kotlin_launch_flow.mdc` for why the split between prime
 * and start belongs where it does — moving it out of the Application is one
 * of the three ways to lose attribution (#19 in the pitfalls).
 */
open class OrbitApp : Application() {

    lateinit var trackingDispatch: AttrHub
        private set

    override fun onCreate() {
        super.onCreate()


        // UNIQUE:INIT_DECOYS_PRE:BEGIN
        // decoy: probe vd7x
        run { val warmProbeVd7x = System.nanoTime(); warmProbeVd7x.hashCode() }
        // decoy: probe zfev4h
        run { val trickleProbeZfev4h = System.nanoTime(); trickleProbeZfev4h.hashCode() }
        // decoy: probe p292r8
        run { val swayProbeP292r8 = System.nanoTime(); swayProbeP292r8.hashCode() }
        // UNIQUE:INIT_DECOYS_PRE:END
        try {
            FirebaseApp.initializeApp(this)
            val fac = if (BuildConfig.DEBUG)
                DebugAppCheckProviderFactory.getInstance()
            else
                PlayIntegrityAppCheckProviderFactory.getInstance()
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(fac)
        } catch (e: Exception) {
            Trace.w(TAG, "Firebase not configured — gray flow will still try the config POST", e)
        }

        // Warn once if the operator did not fill in gray.allowedHosts.
        UrlGuard.warnIfMissing()

        trackingDispatch = AttrHub(this)
        trackingDispatch.prime()

        // UNIQUE:INIT_DECOYS_POST:BEGIN
        // decoy: probe hu9a
        run { val furlProbeHu9a = System.nanoTime(); furlProbeHu9a.hashCode() }
        // decoy: probe ze95db
        run { val cuspProbeZe95db = System.nanoTime(); cuspProbeZe95db.hashCode() }
        // decoy: probe tm2e
        run { val settleProbeTm2e = System.nanoTime(); settleProbeTm2e.hashCode() }
        // decoy: probe tzmyyk
        run { val unspoolProbeTzmyyk = System.nanoTime(); unspoolProbeTzmyyk.hashCode() }
        // UNIQUE:INIT_DECOYS_POST:END
    }

    private companion object { const val TAG = "OrbitApp" }
}
