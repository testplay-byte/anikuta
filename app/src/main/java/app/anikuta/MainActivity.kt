package app.anikuta

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.anikuta.navigation.AnikutaNavGraph
import app.anikuta.onboarding.OnboardingScreen
import app.anikuta.ui.theme.AnikutaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnikutaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val prefs = getSharedPreferences("anikuta_prefs", Context.MODE_PRIVATE)
                    var onboardingComplete by remember {
                        mutableStateOf(prefs.getBoolean("onboarding_complete", false))
                    }

                    if (!onboardingComplete) {
                        OnboardingScreen(
                            onComplete = {
                                prefs.edit().putBoolean("onboarding_complete", true).apply()
                                onboardingComplete = true
                            },
                        )
                    } else {
                        AnikutaNavGraph()
                    }
                }
            }
        }
    }
}
