package com.simon.trafikpuls

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.simon.trafikpuls.ui.TrafikpulsAppRoot
import com.simon.trafikpuls.ui.theme.TrafikpulsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrafikpulsTheme {
                TrafikpulsAppRoot()
            }
        }
    }
}
