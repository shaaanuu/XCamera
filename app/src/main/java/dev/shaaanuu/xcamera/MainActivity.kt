package dev.shaaanuu.xcamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.shaaanuu.xcamera.ui.XCameraApp
import dev.shaaanuu.xcamera.ui.theme.XCameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            XCameraTheme {
                XCameraApp()
            }
        }
    }
}
