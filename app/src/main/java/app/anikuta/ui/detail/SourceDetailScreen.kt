package app.anikuta.ui.detail

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.source.api.model.SAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase D — Source-based detail screen.
 *
 * Shown when the user taps a source search result (from Search → Extensions mode).
 * Unlike [DetailScreen] (which is AniList-keyed), this fetches anime info directly
 * from the extension source via [AnimeSource.getAnimeDetails].
 *
 * Shows: cover, title, description, genres, status. Does NOT show AniList-specific
 * data (score, season year, format) because the source doesn't provide it.
 *
 * @param sourceId the extension source ID
 * @param animeUrl the source-specific anime URL
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SourceDetailScreen(
    sourceId: Long,
    animeUrl: String,
    onBack: () -> Unit,
) {
    val viewModel: SourceDetailViewModel = viewModel(key = "source_detail_${sourceId}_$animeUrl") {
        SourceDetailViewModel(sourceId, animeUrl)
    }
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (state as? SourceDetailState.Success)?.anime?.title ?: "Loading…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is SourceDetailState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is SourceDetailState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is SourceDetailState.Success -> {
                val anime = s.anime
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Cover
                    if (!anime.thumbnail_url.isNullOrBlank()) {
                        AsyncImage(
                            model = anime.thumbnail_url,
                            contentDescription = anime.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    // Title
                    Text(
                        text = anime.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    // Genres
                    anime.getGenres()?.let { genres ->
                        if (genres.isNotEmpty()) {
                            Text(
                                text = genres.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    // Status
                    val statusLabel = when (anime.status) {
                        SAnime.ONGOING -> "Ongoing"
                        SAnime.COMPLETED -> "Completed"
                        SAnime.LICENSED -> "Licensed"
                        SAnime.PUBLISHING_FINISHED -> "Publishing Finished"
                        SAnime.CANCELLED -> "Cancelled"
                        SAnime.ON_HIATUS -> "On Hiatus"
                        else -> "Unknown"
                    }
                    Text(
                        text = "Status: $statusLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    // Description
                    val desc = anime.description
                    if (!desc.isNullOrBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

/** ViewModel for [SourceDetailScreen]. */
class SourceDetailViewModel(
    private val sourceId: Long,
    private val animeUrl: String,
) : ViewModel() {

    companion object {
        private const val TAG = "SourceDetailVM"
    }

    private val sourceManager: AnimeSourceManager? = try { Injekt.get() } catch (e: Exception) { null }

    private val _state = MutableStateFlow<SourceDetailState>(SourceDetailState.Loading)
    val state: StateFlow<SourceDetailState> = _state.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        val manager = sourceManager
        if (manager == null) {
            _state.value = SourceDetailState.Error("Source manager not available")
            return
        }
        viewModelScope.launch {
            _state.value = SourceDetailState.Loading
            try {
                val source = withContext(Dispatchers.IO) {
                    manager.getCatalogueSources().find { it.id == sourceId }
                }
                if (source == null) {
                    _state.value = SourceDetailState.Error("Source not found (id=$sourceId)")
                    return@launch
                }
                // Build a minimal SAnime and fetch full details from the source.
                val sAnime = SAnime.create().apply {
                    url = animeUrl
                    title = "" // will be filled by getAnimeDetails
                }
                val details = withContext(Dispatchers.IO) { source.getAnimeDetails(sAnime) }
                _state.value = SourceDetailState.Success(details)
                Log.d(TAG, "Loaded source details: ${details.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load source details", e)
                _state.value = SourceDetailState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class SourceDetailState {
    data object Loading : SourceDetailState()
    data class Success(val anime: SAnime) : SourceDetailState()
    data class Error(val message: String) : SourceDetailState()
}
