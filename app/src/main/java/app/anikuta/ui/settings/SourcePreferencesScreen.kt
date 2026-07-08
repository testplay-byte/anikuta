package app.anikuta.ui.settings

import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceFragmentCompat
import app.anikuta.R
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.sourcePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 7 — Extension Source Preferences screen.
 *
 * Hosts a [PreferenceFragmentCompat] inside a Compose [AndroidView]. The
 * fragment calls [ConfigurableAnimeSource.setupPreferenceScreen] to let the
 * extension build its own settings UI. Values persist to the source's
 * `"source_$id"` SharedPreferences.
 *
 * CRITICAL: the fragment container must use a STABLE resource ID (not
 * View.generateViewId()) and the fragment transaction must run in the
 * `update` block (not `factory`) so the container is already attached to
 * the view hierarchy when FragmentManager looks for it.
 */
@Composable
fun SourcePreferencesScreen(
    sourceId: Long,
    onBack: () -> Unit,
) {
    SettingsSubpageScaffold(title = "Source settings", onBack = onBack) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FragmentContainerView(ctx).apply {
                    id = R.id.source_preferences_container
                }
            },
            update = { container ->
                val activity = container.context as? FragmentActivity ?: return@AndroidView
                val fm = activity.supportFragmentManager
                val tag = "source_prefs_$sourceId"
                val existing = fm.findFragmentByTag(tag)
                if (existing == null) {
                    val fragment = SourcePreferencesFragment.newInstance(sourceId)
                    fm.beginTransaction()
                        .replace(R.id.source_preferences_container, fragment, tag)
                        .commitAllowingStateLoss()
                }
            },
        )
    }
}

/**
 * The PreferenceFragmentCompat that hosts the extension's preference screen.
 *
 * If the source is NOT [ConfigurableAnimeSource], shows a "No settings
 * available" message instead of crashing.
 */
class SourcePreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val sourceId = arguments?.getLong(ARG_SOURCE_ID) ?: run {
            Log.e(TAG, "No sourceId argument")
            return
        }
        Log.i(TAG, "onCreatePreferences: sourceId=$sourceId")

        val sourceManager = try {
            Injekt.get<AnimeSourceManager>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get AnimeSourceManager from DI", e)
            return
        }

        val source = sourceManager.getOrStub(sourceId)
        Log.i(TAG, "Source: ${source.name} (id=${source.id}, class=${source.javaClass.simpleName})")

        if (source is ConfigurableAnimeSource) {
            Log.i(TAG, "Source IS ConfigurableAnimeSource — building preference screen")
            try {
                preferenceManager.preferenceDataStore =
                    SourcePreferenceDataStore(source.sourcePreferences())
                val screen = preferenceManager.createPreferenceScreen(requireContext())
                source.setupPreferenceScreen(screen)
                // CRITICAL: setPreferenceScreen must be called to attach the
                // screen to the fragment. Without this, the screen is built
                // but never displayed (blank page).
                preferenceScreen = screen
                Log.i(TAG, "Preference screen built + attached. Preference count: ${screen.preferenceCount}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build preference screen", e)
            }
        } else {
            Log.w(TAG, "Source is NOT ConfigurableAnimeSource — no settings to show")
            // Build an empty screen — the Compose layer shows the back button.
            val screen = preferenceManager.createPreferenceScreen(requireContext())
            preferenceScreen = screen
        }
    }

    companion object {
        private const val TAG = "SourcePrefsFragment"
        private const val ARG_SOURCE_ID = "source_id"

        fun newInstance(sourceId: Long): SourcePreferencesFragment {
            return SourcePreferencesFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SOURCE_ID, sourceId)
                }
            }
        }
    }
}
