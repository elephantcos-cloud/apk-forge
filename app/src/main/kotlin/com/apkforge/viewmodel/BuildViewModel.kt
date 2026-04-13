package com.apkforge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.apkforge.AppPreferences
import com.apkforge.core.BuildManager
import com.apkforge.model.BuildJob
import com.apkforge.model.BuildStatus
import com.apkforge.network.GithubApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * APK Forge — Build ViewModel (Kotlin)
 * Bridges Repository ↔ UI, holds all build-related state
 */
class BuildViewModel(app: Application) : AndroidViewModel(app) {

    private val buildManager = BuildManager.getInstance(app)
    private val apiClient = GithubApiClient.getInstance()

    // ── UI State ───────────────────────────────────────────────────────────
    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class TokenValidating(val token: String) : UiState()
        data class TokenValid(val username: String, val avatarUrl: String?) : UiState()
        data class TokenInvalid(val reason: String) : UiState()
        data class BuildQueued(val runId: Long, val repoPath: String) : UiState()
        data class BuildRunning(val runId: Long, val progress: Int, val statusText: String) : UiState()
        data class BuildSuccess(val runId: Long, val artifacts: List<BuildManager.ArtifactInfo>) : UiState()
        data class BuildFailed(val runId: Long, val reason: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    private val _repoInfo = MutableLiveData<com.apkforge.model.RepoInfo?>()
    val repoInfo: LiveData<com.apkforge.model.RepoInfo?> = _repoInfo

    private val _branches = MutableLiveData<List<String>>()
    val branches: LiveData<List<String>> = _branches

    private val _currentRunId = MutableLiveData<Long>(-1L)
    val currentRunId: LiveData<Long> = _currentRunId

    private val _progressPercent = MutableLiveData(0)
    val progressPercent: LiveData<Int> = _progressPercent

    // ── Actions ────────────────────────────────────────────────────────────

    fun validateToken(token: String) {
        if (token.isBlank()) { _uiState.value = UiState.TokenInvalid("Token cannot be empty"); return }
        _uiState.value = UiState.TokenValidating(token)
        viewModelScope.launch(Dispatchers.IO) {
            apiClient.setToken(token)
            val result = apiClient.validateToken()
            withContext(Dispatchers.Main) {
                if (result.success) {
                    val username = result.data?.get("login")?.asString ?: ""
                    val avatar   = result.data?.get("avatar_url")?.asString
                    _uiState.value = UiState.TokenValid(username, avatar)
                    viewModelScope.launch {
                        AppPreferences.setGithubToken(token)
                        AppPreferences.setGithubUser(username)
                    }
                } else {
                    _uiState.value = UiState.TokenInvalid(
                        if (result.isAuthError()) "Invalid token — check permissions"
                        else "Validation failed: ${result.message}"
                    )
                }
            }
        }
    }

    fun loadRepo(owner: String, repo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _uiState.value = UiState.Loading }

            // Load repo info
            val repoResult = apiClient.getRepository(owner, repo)
            val branchResult = apiClient.listBranches(owner, repo)

            withContext(Dispatchers.Main) {
                if (repoResult.success) {
                    val d = repoResult.data!!
                    _repoInfo.value = com.apkforge.model.RepoInfo(
                        name          = d.get("name").asString,
                        fullName      = d.get("full_name").asString,
                        description   = d.get("description")?.let { if (it.isJsonNull) null else it.asString },
                        defaultBranch = d.get("default_branch").asString,
                        isPrivate     = d.get("private").asBoolean,
                        stars         = d.get("stargazers_count").asInt,
                        language      = d.get("language")?.let { if (it.isJsonNull) null else it.asString },
                        updatedAt     = d.get("updated_at")?.asString
                    )
                }

                if (branchResult.success) {
                    val list = mutableListOf<String>()
                    branchResult.data?.forEach { e -> list.add(e.asJsonObject.get("name").asString) }
                    _branches.value = list
                }

                _uiState.value = UiState.Idle
            }
        }
    }

    fun startBuild(job: BuildJob) {
        if (!job.isValid) { _uiState.value = UiState.Error("Invalid repo configuration"); return }
        _uiState.value = UiState.Loading

        buildManager.startBuild(job, object : BuildManager.BuildCallback {
            override fun onBuildQueued(runId: Long) {
                _currentRunId.postValue(runId)
                _uiState.postValue(UiState.BuildQueued(runId, job.fullRepoPath))
                _progressPercent.postValue(5)
            }
            override fun onStatusUpdate(runId: Long, status: BuildStatus, progress: Int) {
                _progressPercent.postValue(progress)
                _uiState.postValue(UiState.BuildRunning(
                    runId, progress,
                    "${status.emoji} ${status.displayName} ($progress%)"
                ))
            }
            override fun onBuildComplete(runId: Long, artifacts: List<BuildManager.ArtifactInfo>) {
                _progressPercent.postValue(100)
                _uiState.postValue(UiState.BuildSuccess(runId, artifacts))
            }
            override fun onBuildFailed(runId: Long, reason: String) {
                _uiState.postValue(UiState.BuildFailed(runId, reason))
            }
            override fun onError(message: String) {
                _uiState.postValue(UiState.Error(message))
            }
        })
    }

    fun cancelBuild(owner: String, repo: String) {
        val runId = _currentRunId.value ?: return
        if (runId < 0) return
        buildManager.cancelBuild(owner, repo, runId, object : BuildManager.BuildCallback {
            override fun onBuildFailed(runId: Long, reason: String) {
                _uiState.postValue(UiState.BuildFailed(runId, reason))
            }
            override fun onError(message: String) { _uiState.postValue(UiState.Error(message)) }
            override fun onBuildQueued(runId: Long) {}
            override fun onStatusUpdate(runId: Long, status: BuildStatus, progress: Int) {}
            override fun onBuildComplete(runId: Long, artifacts: List<BuildManager.ArtifactInfo>) {}
        })
    }

    fun loadSavedToken() {
        viewModelScope.launch {
            val token = AppPreferences.githubToken.first()
            if (token.isNotBlank()) {
                validateToken(token)
            }
        }
    }

    fun resetState() {
        buildManager.stopPolling()
        _uiState.value = UiState.Idle
        _progressPercent.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        buildManager.stopPolling()
    }
}
