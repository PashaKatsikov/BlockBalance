package bp.goalsgames.blockbalance

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import bp.goalsgames.blockbalance.ui.GameRoot
import bp.goalsgames.blockbalance.ui.theme.BlockBalanceTheme
import bp.goalsgames.blockbalance.ui.theme.Yard

/** The single window of the game. Screens are Compose; the yard is a surface. */
class StageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            BlockBalanceTheme {
                GameRoot(modifier = Modifier.fillMaxSize().background(Yard.night))
            }
        }
    }
}
