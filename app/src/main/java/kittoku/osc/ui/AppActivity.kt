package kittoku.osc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kittoku.osc.activity.MainActivity
import kittoku.osc.ui.theme.OscTheme


class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            OscTheme {
                HomeScreen(onOpenLegacySettings = {
                    startActivity(Intent(this, MainActivity::class.java))
                })
            }
        }
    }
}
