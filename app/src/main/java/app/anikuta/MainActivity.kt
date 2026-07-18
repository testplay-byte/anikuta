package app.anikuta

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.anikuta.navigation.AnikutaNavGraph
import app.anikuta.onboarding.OnboardingScreen
import app.anikuta.ui.theme.AnikutaTheme
import app.anikuta.ui.theme.DarkBackground
import app.anikuta.ui.theme.Background
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge with system bar colors matching our theme.
        // The default (before this fix) used the system's dark #1D1B20 which
        // clashed with our lime theme. Now the status/nav bar scrims use our
        // own background color.
        val isDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bgArgb = (if (isDark) DarkBackground else Background).value.toInt()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(bgArgb, bgArgb),
            navigationBarStyle = SystemBarStyle.auto(bgArgb, bgArgb),
        )

        // Handle AniList OAuth callback (Phase 6 task 6.7)
        handleOAuthCallback(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle OAuth callback when the activity is already running
        handleOAuthCallback(intent)
    }

    private fun handleOAuthCallback(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme == "aniyomi" && data.host == "anilist-auth") {
            Log.d("MainActivity", "AniList OAuth callback received: $data")
            try {
                val tracker: app.anikuta.data.tracker.AniListTracker = Injekt.get()
                val success = tracker.handleOAuthCallback(data)
                if (success) {
                    Toast.makeText(this, "AniList login successful!", Toast.LENGTH_SHORT).show()
                    // Fetch username in background
                    kotlinx.coroutines.MainScope().launch {
                        tracker.fetchUsername()
                    }
                } else {
                    Toast.makeText(this, "AniList login failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "OAuth callback handling failed", e)
            }
        }
    }
}
