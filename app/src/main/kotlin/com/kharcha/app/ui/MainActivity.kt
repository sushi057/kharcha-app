package com.kharcha.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kharcha.app.ui.theme.KharchaTheme
import dagger.hilt.android.AndroidEntryPoint

/** Sole activity: hosts [KharchaNavHost] inside [KharchaTheme]. Launcher activity. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KharchaTheme {
                KharchaNavHost()
            }
        }
    }
}
