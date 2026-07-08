package app.anikuta.ui.settings

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceFragmentCompat
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
 * `"source_$id"` SharedPreferences and are read by the extension on-demand.
 *
 * CRITICAL: the fragment container must use a STABLE resource ID (not
 * View.generateViewId()) and the fragment transaction must run in the
 * `update` block (not `factory`) so the container is already attached to
 * the view hierarchy when FragmentManager looks for it.
 *
 * Source: REFERENCE/app/.../ui/browse/anime/extension/details/AnimeSourcePreferencesScreen.kt
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
                    // Must use a stable resource ID, not View.generateViewId().
                    id = R.id.source_preferences_container
                }
            },
            update = { container ->
                val activity = container.context as? FragmentActivity ?: return@AndroidView
                val fm = activity.supportFragmentManager
                // Guard: only add the fragment once per sourceId. If the fragment
                // is already there (same tag), don't re-add — this avoids
                // "Fragment already added" exceptions on recomposition.
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
 */
class SourcePreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val sourceId = arguments?.getLong(ARG_SOURCE_ID) ?: return
        val sourceManager = try { Injekt.get<AnimeSourceManager>() } catch (e: Exception) { return }
        val source = sourceManager.getOrStub(sourceId)

        if (source is ConfigurableAnimeSource) {
            preferenceManager.preferenceDataStore =
                SourcePreferenceDataStore(source.sourcePreferences())
            source.setupPreferenceScreen(preferenceManager.createPreferenceScreen(requireContext()))
        }
    }

    companion object {
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
