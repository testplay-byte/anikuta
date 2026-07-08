package app.anikuta.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
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
 * `"source_$id"` SharedPreferences and are read by the extension on-demand
 * during network work, so they apply throughout automatically.
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
                FrameLayout(ctx).apply {
                    id = View.generateViewId()
                    val fragment = SourcePreferencesFragment.newInstance(sourceId)
                    (ctx as? FragmentActivity)?.supportFragmentManager
                        ?.beginTransaction()
                        ?.replace(id, fragment, "source_prefs_$sourceId")
                        ?.commitNowAllowingStateLoss()
                }
            },
        )
    }
}

/**
 * The PreferenceFragmentCompat that hosts the extension's preference screen.
 * Created with a sourceId argument. Looks up the source from
 * [AnimeSourceManager], and if it's a [ConfigurableAnimeSource], calls
 * [ConfigurableAnimeSource.setupPreferenceScreen] to build the UI.
 */
class SourcePreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val sourceId = arguments?.getLong(ARG_SOURCE_ID) ?: return
        val sourceManager = try { Injekt.get<AnimeSourceManager>() } catch (e: Exception) { return }
        val source = sourceManager.getOrStub(sourceId)

        if (source is ConfigurableAnimeSource) {
            // Wire the source's SharedPreferences as the backing store.
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
