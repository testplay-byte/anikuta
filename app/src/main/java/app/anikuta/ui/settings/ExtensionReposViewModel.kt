package app.anikuta.ui.settings

import android.util.Log
import android.widget.Toast
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
 * Manages the list of extension repos (add/remove/refresh). Repos are stored
 * in the SQLDelight `extension_repos` table. Each repo has a baseUrl, name,
 * website, and signingKeyFingerprint.
 *
 * The repo URL must point to an `index.min.json` file. We strip the
 * `/index.min.json` suffix to get the baseUrl, then fetch `/repo.json` for
 * the repo's metadata (name, signing fingerprint).
 */
class ExtensionReposViewModel : ViewModel() {

    companion object {
        private const val TAG = "ExtReposViewModel"
    }

    private val getExtensionRepo: GetAnimeExtensionRepo? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val createRepo: CreateAnimeExtensionRepo? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val deleteRepo: DeleteAnimeExtensionRepo? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }
    private val updateRepo: UpdateAnimeExtensionRepo? = try { Injekt.get() } catch (e: Exception) { Log.e(TAG, "DI failed", e); null }

    private val _repos = MutableStateFlow<List<ExtensionRepo>>(emptyList())
    val repos: StateFlow<List<ExtensionRepo>> = _repos.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Result of the last createRepo call — drives conflict/error dialogs. */
    private val _createResult = MutableStateFlow<CreateResult>(CreateResult.Idle)
    val createResult: StateFlow<CreateResult> = _createResult.asStateFlow()

    init {
        viewModelScope.launch {
            getExtensionRepo?.subscribeAll()?.collectLatest { repoList ->
                _repos.value = repoList
                Log.d(TAG, "Repos updated: ${repoList.size}")
            }
        }
    }

    /**
     * Add a new repo. The URL must end with `/index.min.json`.
     * On success, the repo is inserted into the DB and the Flow auto-updates.
     */
    fun createRepo(url: String) {
        val interactor = createRepo ?: return
        viewModelScope.launch {
            _createResult.value = CreateResult.Loading
            try {
                val result = interactor.await(url)
                _createResult.value = when (result) {
                    is CreateAnimeExtensionRepo.Result.Success -> CreateResult.Success
                    is CreateAnimeExtensionRepo.Result.InvalidUrl -> CreateResult.Error("Invalid URL. Must end with /index.min.json")
                    is CreateAnimeExtensionRepo.Result.RepoAlreadyExists -> CreateResult.Error("Repository already exists")
                    is CreateAnimeExtensionRepo.Result.DuplicateFingerprint -> CreateResult.DuplicateFingerprint(result.oldRepo, result.newRepo)
                    is CreateAnimeExtensionRepo.Result.Error -> CreateResult.Error("Failed to add repository")
                }
            } catch (e: Exception) {
                Log.e(TAG, "createRepo failed", e)
                _createResult.value = CreateResult.Error(e.message ?: "Unknown error")
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
