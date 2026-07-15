package app.anikuta.ui.detail

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.ExtensionLinkStore
import app.anikuta.source.api.model.SAnime
import app.anikuta.source.bridge.SourceSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase I — Source linking screen.
 *
 * When the user taps an extension anime result:
 *   1. This screen shows a loading card (cover + name + genres + description)
 *   2. While "Processing...", it searches AniList by the anime title
 *   3. Finds the best match → links it (ExtensionLinkStore) → opens DetailScreen
 *
 * If the user has already linked this anime, the NavGraph skips this screen
 * and goes directly to DetailScreen.
 *
 * @param sourceId the extension source ID
 * @param animeUrl the source-specific anime URL
 * @param title the anime title (from the extension result)
 * @param thumbnailUrl the cover image URL (from the extension result)
 * @param sourceName the name of the extension source
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceLinkingScreen(
    sourceId: Long,
    animeUrl: String,
    title: String,
    thumbnailUrl: String?,
    sourceName: String,
    onLinked: (anilistId: Int) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SourceLinkingViewModel = viewModel(key = "source_linking_${sourceId}_$animeUrl") {
        SourceLinkingViewModel(sourceId, animeUrl, title)
    }
    val state by viewModel.state.collectAsState()

    // When linked, navigate to the detail page
    LaunchedEffect(state) {
        if (state is SourceLinkingState.Linked) {
            onLinked((state as SourceLinkingState.Linked).anilistId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Cover image
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .width(180.dp)
                        .height(270.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Anime name
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Source name
            Text(
                text = "From: $sourceName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Processing indicator
            when (val s = state) {
                is SourceLinkingState.Searching -> {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Processing... Just wait a moment",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Searching AniList for \"$title\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                is SourceLinkingState.Linked -> {
                    Text(
                        "Found! Opening details...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                is SourceLinkingState.Error -> {
                    Text(
                        "Could not find this anime on AniList.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Go back") }
                }
            }
        }
    }
}

/** ViewModel for [SourceLinkingScreen]. */
class SourceLinkingViewModel(
    private val sourceId: Long,
    private val animeUrl: String,
    private val animeTitle: String,
) : ViewModel() {

    companion object {
        private const val TAG = "SourceLinkingVM"
    }

    private val anilistRepo: AniListRepository? = try { Injekt.get() } catch (e: Exception) { null }
    private val linkStore: ExtensionLinkStore? = try { Injekt.get() } catch (e: Exception) { null }

    private val _state = MutableStateFlow<SourceLinkingState>(SourceLinkingState.Searching)
    val state: StateFlow<SourceLinkingState> = _state.asStateFlow()

    init {
        searchAndLink()
    }

    private fun searchAndLink() {
        val repo = anilistRepo
        if (repo == null) {
            _state.value = SourceLinkingState.Error("AniList not available")
            return
        }
        viewModelScope.launch {
            _state.value = SourceLinkingState.Searching
            try {
                // Search AniList by the anime title
                val results = withContext(Dispatchers.IO) {
                    repo.searchAnime(animeTitle, page = 1, perPage = 5)
                }
                if (results.isEmpty()) {
                    _state.value = SourceLinkingState.Error("No AniList results for \"$animeTitle\"")
                    return@launch
                }
                // Pick the first result (AniList SEARCH_MATCH sort already returns best matches first)
                val bestMatch = results.first()
                Log.d(TAG, "Linked: \"$animeTitle\" → AniList ID ${bestMatch.id} (${bestMatch.title.preferred()})")

                // Cache the link
                linkStore?.link(sourceId, animeUrl, bestMatch.id)

                _state.value = SourceLinkingState.Linked(bestMatch.id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to search AniList for \"$animeTitle\"", e)
                _state.value = SourceLinkingState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class SourceLinkingState {
    data object Searching : SourceLinkingState()
    data class Linked(val anilistId: Int) : SourceLinkingState()
    data class Error(val message: String) : SourceLinkingState()
}
