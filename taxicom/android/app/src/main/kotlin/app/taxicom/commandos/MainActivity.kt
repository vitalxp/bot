package app.taxicom.commandos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.taxicom.commandos.ui.TaxiCommandosApp
import app.taxicom.commandos.ui.theme.TaxiCommandosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as TaxiCommandosApp).graph
        setContent {
            TaxiCommandosTheme {
                TaxiCommandosApp(graph)
            }
        }
    }
}
