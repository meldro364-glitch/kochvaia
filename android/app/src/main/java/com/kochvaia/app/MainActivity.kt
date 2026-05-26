package com.kochvaia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kochvaia.app.ui.KochvaiaApp
import com.kochvaia.app.ui.theme.KochvaiaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KochvaiaTheme {
                // The whole app is a cream/light surface in light mode. Match
                // the status bar icon contrast so the time/battery/wifi stay
                // readable on top of it; in dark mode the system default
                // (light icons on dark bg) is already correct.
                val view = LocalView.current
                val dark = isSystemInDarkTheme()
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !dark
                    controller.isAppearanceLightNavigationBars = !dark
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    KochvaiaApp()
                }
            }
        }
    }
}
