package dev.andrej.echo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.andrej.echo.ui.theme.EchoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as EchoApplication).container

        setContent {
            EchoTheme {
                EchoApp(viewModelFactory = container.viewModelFactory)
            }
        }
    }
}
