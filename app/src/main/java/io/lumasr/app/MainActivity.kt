package io.lumasr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.lumasr.app.ui.AppRoot
import io.lumasr.app.ui.theme.LumaSRTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LumaSRTheme {
                AppRoot()
            }
        }
    }
}
