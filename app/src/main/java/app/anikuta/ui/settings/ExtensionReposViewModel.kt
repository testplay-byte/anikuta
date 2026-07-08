package app.anikuta.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.model.ExtensionRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 7 — ViewModel for the Extension Repositories settings screen.
 *
 * Uses LAZY initialization for the interactors (not constructor-time) so that
 * if one interactor fails to resolve, the others still work and we can log
 * the actual exception instead of silently nulled-out fields.
 */
class ExtensionReposViewModel : ViewModel() {

    companion object {
        private const val TAG = "ExtReposViewModel"
    }

    // Lazy init — resolves on first use, not at construction time.
    // This way if DI has a transient issue, we get the actual error when
    // the action is attempted, not a silent null at startup.
    private val getExtensionRepo: GetAnimeExtensionRepo? by lazy { safeGet("GetAnimeExtensionRepo") }
    private val createRepo: CreateAnimeExtensionRepo? by lazy { safeGet("CreateAnimeExtensionRepo") }
    private val deleteRepo: DeleteAnimeExtensionRepo? by lazy { safeGet("DeleteAnimeExtensionRepo") }
    private val updateRepo: UpdateAnimeExtensionRepo? by lazy { safeGet("UpdateAnimeExtensionRepo") }

    private val _repos = MutableStateFlow<List<ExtensionRepo>>(emptyList())
    val repos: StateFlow<List<ExtensionRepo>> = _repos.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _createResult = MutableStateFlow<CreateResult>(CreateResult.Idle)
    val createResult: StateFlow<CreateResult> = _createResult.asStateFlow()

    private fun <T> safeGet(name: String): T? {
        return try {
            @Suppress("UNCHECKED_CAST")
            Injekt.get<T>().also {
                Log.d(TAG, "✅ $name resolved from DI")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to resolve $name from DI", e)
            null
        }
    }

    init {
        viewModelScope.launch {
            getExtensionRepo?.subscribeAll()?.collectLatest { repoList ->
                _repos.value = repoList
                Log.d(TAG, "Repos updated: ${repoList.size}")
            }
        }
    }

    /**
     * Add a new repo. Accepts URLs with or without /index.min.json suffix.
     */
    fun createRepo(url: String) {
        val interactor = createRepo ?: run {
            Log.e(TAG, "createRepo: CreateAnimeExtensionRepo is null (DI failed)")
            _createResult.value = CreateResult.Error("Internal error: repo service unavailable. Restart the app.")
            return
        }
        viewModelScope.launch {
            _createResult.value = CreateResult.Loading
            try {
                Log.i(TAG, "createRepo: attempting to add '$url'")
                val result = interactor.await(url)
                _createResult.value = when (result) {
                    is CreateAnimeExtensionRepo.Result.Success -> {
                        Log.i(TAG, "createRepo: SUCCESS — repo added")
                        CreateResult.Success
                    }
                    is CreateAnimeExtensionRepo.Result.InvalidUrl -> {
                        Log.w(TAG, "createRepo: InvalidUrl")
                        CreateResult.Error("Invalid URL. Must be an HTTPS URL ending with /index.min.json (or just the base URL).")
                    }
                    is CreateAnimeExtensionRepo.Result.RepoAlreadyExists -> {
                        Log.w(TAG, "createRepo: RepoAlreadyExists")
                        CreateResult.Error("Repository already exists")
                    }
                    is CreateAnimeExtensionRepo.Result.DuplicateFingerprint -> CreateResult.DuplicateFingerprint(result.oldRepo, result.newRepo)
                    is CreateAnimeExtensionRepo.Result.Error -> {
                        Log.e(TAG, "createRepo: Error")
                        CreateResult.Error("Failed to add repository")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "createRepo failed", e)
                _createResult.value = CreateResult.Error(e.message ?: "Unknown error: ${e.javaClass.simpleName}")
            }
        }
    }

    fun deleteRepo(baseUrl: String) {
        val interactor = deleteRepo ?: return
        viewModelScope.launch {
            try {
                interactor.await(baseUrl)
                Log.i(TAG, "Deleted repo: $baseUrl")
            } catch (e: Exception) {
                Log.e(TAG, "deleteRepo failed", e)
            }
        }
    }

    fun refreshRepos() {
        val interactor = updateRepo ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                interactor.awaitAll()
                Log.i(TAG, "Repos refreshed")
            } catch (e: Exception) {
                Log.e(TAG, "refreshRepos failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun dismissCreateResult() {
        _createResult.value = CreateResult.Idle
    }

    sealed class CreateResult {
        data object Idle : CreateResult()
        data object Loading : CreateResult()
        data object Success : CreateResult()
        data class Error(val message: String) : CreateResult()
        data class DuplicateFingerprint(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : CreateResult()
    }
}
