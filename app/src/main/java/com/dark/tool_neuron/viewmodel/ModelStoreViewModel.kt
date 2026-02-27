package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.data.HFModelRepository
import com.dark.tool_neuron.models.data.HuggingFaceModel
import com.dark.tool_neuron.models.data.ModelCategory
import com.dark.tool_neuron.models.data.ModelType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.network.HuggingFaceClient
import com.dark.tool_neuron.network.HuggingFaceSearchResult
import com.dark.tool_neuron.repo.ModelRepositoryDataStore
import com.dark.tool_neuron.repo.ModelStoreRepository
import com.dark.tool_neuron.repo.RepositoryValidator
import com.dark.tool_neuron.repo.ValidationResult
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.utils.ModelMetadataExtractor
import com.dark.tool_neuron.utils.SizeCategory
import com.dark.tool_neuron.ui.screen.StoreTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

enum class SortOption {
    NAME,
    SIZE,
    RECENTLY_ADDED
}

sealed class HFSearchState {
    object Idle : HFSearchState()
    object Loading : HFSearchState()
    object Success : HFSearchState()
    data class Error(val message: String) : HFSearchState()
}

data class RepoGroupInfo(
    val displayName: String,
    val author: String,
    val modelType: ModelType,
    val modelCount: Int
)

class ModelStoreViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ModelStoreRepository(application)
    private val systemRepo = AppContainer.getModelRepository()
    private val repoDataStore = ModelRepositoryDataStore(application)
    private val repositoryValidator = RepositoryValidator()

    private val _selectedTab = MutableStateFlow(StoreTab.MODELS)
    val selectedTab: StateFlow<StoreTab> = _selectedTab

    private val _models = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val models: StateFlow<List<HuggingFaceModel>> = _models

    private val _filteredModels = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val filteredModels: StateFlow<List<HuggingFaceModel>> = _filteredModels

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _installedModels = MutableStateFlow<List<Model>>(emptyList())
    val installedModels: StateFlow<List<Model>> = _installedModels

    private val _deviceInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val deviceInfo: StateFlow<Map<String, String>> = _deviceInfo

    private val _deleteInProgress = MutableStateFlow<String?>(null)
    val deleteInProgress: StateFlow<String?> = _deleteInProgress

    val repositories = repoDataStore.repositories
    val downloadStates = ModelDownloadService.downloadStates

    // Cached repos for synchronous lookup in getGroupedRepos
    private var cachedRepos: List<HFModelRepository> = emptyList()

    // Filter states
    private val _selectedModelType = MutableStateFlow<ModelType?>(null)
    val selectedModelType: StateFlow<ModelType?> = _selectedModelType

    private val _selectedCategory = MutableStateFlow<ModelCategory?>(null)
    val selectedCategory: StateFlow<ModelCategory?> = _selectedCategory

    private val _selectedParameters = MutableStateFlow<Set<String>>(emptySet())
    val selectedParameters: StateFlow<Set<String>> = _selectedParameters

    private val _selectedQuantizations = MutableStateFlow<Set<String>>(emptySet())
    val selectedQuantizations: StateFlow<Set<String>> = _selectedQuantizations

    private val _selectedSizeCategory = MutableStateFlow<SizeCategory?>(null)
    val selectedSizeCategory: StateFlow<SizeCategory?> = _selectedSizeCategory

    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = _selectedTags

    private val _showNsfw = MutableStateFlow(true)
    val showNsfw: StateFlow<Boolean> = _showNsfw

    private val _executionTarget = MutableStateFlow<String?>(null)
    val executionTarget: StateFlow<String?> = _executionTarget

    private val _sortBy = MutableStateFlow(SortOption.NAME)
    val sortBy: StateFlow<SortOption> = _sortBy

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Repo card navigation: null = show repo list, non-null = show models inside that repo
    private val _selectedRepository = MutableStateFlow<String?>(null)
    val selectedRepository: StateFlow<String?> = _selectedRepository

    // Validation results
    private val _validationResults = MutableStateFlow<Map<String, ValidationResult>>(emptyMap())
    val validationResults: StateFlow<Map<String, ValidationResult>> = _validationResults

    // HuggingFace Search state
    private val _hfSearchQuery = MutableStateFlow("")
    val hfSearchQuery: StateFlow<String> = _hfSearchQuery

    private val _hfSearchResults = MutableStateFlow<List<HuggingFaceSearchResult>>(emptyList())
    val hfSearchResults: StateFlow<List<HuggingFaceSearchResult>> = _hfSearchResults

    private val _hfSearchState = MutableStateFlow<HFSearchState>(HFSearchState.Idle)
    val hfSearchState: StateFlow<HFSearchState> = _hfSearchState

    private val _showHFSearch = MutableStateFlow(false)
    val showHFSearch: StateFlow<Boolean> = _showHFSearch

    // Cache for expanded repo files in search results
    private val _expandedRepoFiles = MutableStateFlow<Map<String, List<com.dark.tool_neuron.network.HuggingFaceFileResponse>>>(emptyMap())
    val expandedRepoFiles: StateFlow<Map<String, List<com.dark.tool_neuron.network.HuggingFaceFileResponse>>> = _expandedRepoFiles

    // App's internal models directory
    private val appModelsDir = File(application.filesDir, "models")

    init {
        loadDeviceInfo()
        loadModels()
        loadInstalledModels()
    }

    private fun loadDeviceInfo() {
        _deviceInfo.value = repository.getDeviceInfo()
    }

    fun selectTab(tab: StoreTab) {
        _selectedTab.value = tab
    }

    fun refreshModels() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val repos = repositories.first()
                cachedRepos = repos
                repository.refreshModels(repos).onSuccess { modelsList ->
                    _models.value = modelsList
                    applyAllFilters()
                }.onFailure { exception ->
                    _error.value = exception.message
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadModels() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val repos = repositories.first()
                cachedRepos = repos
                repository.getAvailableModels(repos).onSuccess { modelsList ->
                    _models.value = modelsList
                    applyAllFilters()
                }.onFailure { exception ->
                    _error.value = exception.message
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadInstalledModels() {
        viewModelScope.launch {
            try {
                val installedList = systemRepo.getAllModels().first()
                _installedModels.value = installedList
            } catch (e: Exception) {
                Log.e("ModelStoreViewModel", "Error loading installed models", e)
            }
        }
    }

    private fun applyAllFilters() {
        viewModelScope.launch {
            var filtered = _models.value

            // Model type filter (GGUF, SD, TTS)
            _selectedModelType.value?.let { type ->
                filtered = filtered.filter { it.modelType == type }
            }

            // Category filter (repository level) - only applies to GGUF
            _selectedCategory.value?.let { category ->
                val repos = repositories.first()
                val enabledRepos = repos
                    .filter { it.category == category && it.isEnabled }
                    .map { it.id }
                    .toSet()
                filtered = filtered.filter { model ->
                    // Category filter only applies to GGUF models
                    model.modelType != ModelType.GGUF ||
                            enabledRepos.any { model.id.startsWith(it) }
                }
            }

            // Parameter count filter (GGUF only)
            if (_selectedParameters.value.isNotEmpty()) {
                filtered = filtered.filter { model ->
                    if (model.modelType != ModelType.GGUF) true
                    else {
                        val params = ModelMetadataExtractor.extractParameterCount(model.name)
                        params != null && params in _selectedParameters.value
                    }
                }
            }

            // Quantization filter (GGUF only)
            if (_selectedQuantizations.value.isNotEmpty()) {
                filtered = filtered.filter { model ->
                    if (model.modelType != ModelType.GGUF) true
                    else {
                        val quant = ModelMetadataExtractor.extractQuantization(model.name)
                        quant != null && quant in _selectedQuantizations.value
                    }
                }
            }

            // Size category filter
            _selectedSizeCategory.value?.let { sizeCategory ->
                filtered = filtered.filter { model ->
                    ModelMetadataExtractor.extractSizeCategory(model.approximateSize) == sizeCategory
                }
            }

            // Tag filter
            if (_selectedTags.value.isNotEmpty()) {
                filtered = filtered.filter { model ->
                    _selectedTags.value.all { tag -> tag in model.tags }
                }
            }

            // NSFW filter
            if (!_showNsfw.value) {
                filtered = filtered.filter { model ->
                    "NSFW" !in model.tags
                }
            }

            // Execution target filter
            _executionTarget.value?.let { target ->
                filtered = filtered.filter { model ->
                    target in model.tags
                }
            }

            // Search query filter
            if (_searchQuery.value.isNotBlank()) {
                val query = _searchQuery.value
                filtered = filtered.filter {
                    it.name.contains(query, ignoreCase = true) ||
                            it.description.contains(query, ignoreCase = true) ||
                            it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
                }
            }

            // Apply sorting
            filtered = when (_sortBy.value) {
                SortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
                SortOption.SIZE -> filtered.sortedBy { ModelMetadataExtractor.parseSizeToBytes(it.approximateSize) }
                SortOption.RECENTLY_ADDED -> filtered.reversed()
            }

            _filteredModels.value = filtered
        }
    }

    fun filterModels(query: String) {
        _searchQuery.value = query
        applyAllFilters()
    }

    fun filterByModelType(modelType: ModelType?) {
        _selectedModelType.value = modelType
        applyAllFilters()
    }

    fun filterByCategory(category: ModelCategory?) {
        _selectedCategory.value = category
        applyAllFilters()
    }

    fun toggleParameterFilter(parameter: String) {
        _selectedParameters.value = if (parameter in _selectedParameters.value) {
            _selectedParameters.value - parameter
        } else {
            _selectedParameters.value + parameter
        }
        applyAllFilters()
    }

    fun toggleQuantizationFilter(quantization: String) {
        _selectedQuantizations.value = if (quantization in _selectedQuantizations.value) {
            _selectedQuantizations.value - quantization
        } else {
            _selectedQuantizations.value + quantization
        }
        applyAllFilters()
    }

    fun filterBySizeCategory(sizeCategory: SizeCategory?) {
        _selectedSizeCategory.value = sizeCategory
        applyAllFilters()
    }

    fun setSortOption(sortOption: SortOption) {
        _sortBy.value = sortOption
        applyAllFilters()
    }

    fun toggleTagFilter(tag: String) {
        _selectedTags.value = if (tag in _selectedTags.value) {
            _selectedTags.value - tag
        } else {
            _selectedTags.value + tag
        }
        applyAllFilters()
    }

    fun setShowNsfw(show: Boolean) {
        _showNsfw.value = show
        applyAllFilters()
    }

    fun setExecutionTarget(target: String?) {
        _executionTarget.value = target
        applyAllFilters()
    }

    fun getAvailableTags(): List<String> {
        return _models.value
            .flatMap { it.tags }
            .distinct()
            .filter { tag ->
                tag !in listOf("GGUF") && !tag.matches(Regex("Q\\d.*"))
            }
            .sorted()
    }

    fun clearAllFilters() {
        _selectedModelType.value = null
        _selectedCategory.value = null
        _selectedParameters.value = emptySet()
        _selectedQuantizations.value = emptySet()
        _selectedSizeCategory.value = null
        _selectedTags.value = emptySet()
        _showNsfw.value = true
        _executionTarget.value = null
        _sortBy.value = SortOption.NAME
        _searchQuery.value = ""
        applyAllFilters()
    }

    fun selectRepository(repoKey: String?) {
        _selectedRepository.value = repoKey
    }

    fun getGroupedRepos(): Map<String, RepoGroupInfo> {
        val models = _filteredModels.value
        val grouped = mutableMapOf<String, RepoGroupInfo>()

        val repoNameLookup = cachedRepos.associate { it.repoPath to it.name }

        models.groupBy { model ->
            when (model.modelType) {
                ModelType.GGUF -> model.repositoryUrl.ifEmpty { "Unknown" }
                ModelType.SD -> model.repositoryUrl.ifEmpty { "SD Models" }
                ModelType.TTS -> "tts-models"
            }
        }.forEach { (key, groupModels) ->
            val first = groupModels.first()
            val displayName = when (first.modelType) {
                ModelType.TTS -> first.name
                else -> repoNameLookup[key] ?: key.substringAfterLast("/")
            }
            val author = if (key.contains("/")) key.substringBefore("/") else ""
            grouped[key] = RepoGroupInfo(displayName, author, first.modelType, groupModels.size)
        }

        return grouped
    }

    fun getModelsForRepo(repoKey: String): List<HuggingFaceModel> {
        return _filteredModels.value.filter { model ->
            when (model.modelType) {
                ModelType.GGUF -> (model.repositoryUrl.ifEmpty { "Unknown" }) == repoKey
                ModelType.SD -> (model.repositoryUrl.ifEmpty { "SD Models" }) == repoKey
                ModelType.TTS -> repoKey == "tts-models"
            }
        }
    }

    fun downloadModel(model: HuggingFaceModel) {
        val context = getApplication<Application>()
        val fileUrl = "https://huggingface.co/${model.fileUri}"

        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_START_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, model.id)
            putExtra(ModelDownloadService.EXTRA_MODEL_NAME, model.name)
            putExtra(ModelDownloadService.EXTRA_FILE_URL, fileUrl)
            putExtra(ModelDownloadService.EXTRA_IS_ZIP, model.isZip)
            putExtra(ModelDownloadService.EXTRA_MODEL_TYPE, model.modelType.name)
            putExtra(ModelDownloadService.EXTRA_RUN_ON_CPU, model.runOnCpu)
            putExtra(ModelDownloadService.EXTRA_TEXT_EMBEDDING_SIZE, model.textEmbeddingSize)
        }

        context.startForegroundService(intent)
    }

    fun cancelDownload(modelId: String) {
        val context = getApplication<Application>()
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, modelId)
        }
        context.startService(intent)
    }

    fun deleteModel(model: Model) {
        viewModelScope.launch {
            _deleteInProgress.value = model.id
            try {
                // Delete model file if it's in app's internal directory
                val modelFile = File(model.modelPath)
                if (modelFile.exists() && modelFile.absolutePath.startsWith(appModelsDir.absolutePath)) {
                    val deleted = modelFile.delete()
                    Log.d("ModelStoreViewModel", "Model file deleted: $deleted - ${modelFile.absolutePath}")

                    // If it's a directory (for SD models), delete recursively
                    if (modelFile.isDirectory) {
                        modelFile.deleteRecursively()
                    }
                }

                // Delete config from database
                val config = systemRepo.getConfigByModelId(model.id)
                if (config != null) {
                    systemRepo.deleteConfig(config)
                    Log.d("ModelStoreViewModel", "Model config deleted for: ${model.id}")
                }

                // Delete model from database
                systemRepo.deleteModel(model)
                Log.d("ModelStoreViewModel", "Model deleted from database: ${model.modelName}")

                // Reload installed models
                loadInstalledModels()
            } catch (e: Exception) {
                Log.e("ModelStoreViewModel", "Error deleting model", e)
                _error.value = "Failed to delete model: ${e.message}"
            } finally {
                _deleteInProgress.value = null
            }
        }
    }

    fun addRepository(repo: HFModelRepository) {
        viewModelScope.launch {
            repoDataStore.addRepository(repo)
            loadModels()
        }
    }

    fun removeRepository(repoId: String) {
        viewModelScope.launch {
            repoDataStore.removeRepository(repoId)
            loadModels()
        }
    }

    fun toggleRepository(repoId: String) {
        viewModelScope.launch {
            repoDataStore.toggleRepository(repoId)
            loadModels()
        }
    }

    fun updateRepository(repo: HFModelRepository) {
        viewModelScope.launch {
            repoDataStore.updateRepository(repo)
            loadModels()
        }
    }

    fun validateRepository(repo: HFModelRepository) {
        viewModelScope.launch {
            _validationResults.value += repo.id to ValidationResult.Checking
            val result = repositoryValidator.validateRepository(repo)
            _validationResults.value += repo.id to result
        }
    }

    fun getValidationResult(repoId: String): ValidationResult? {
        return _validationResults.value[repoId]
    }

    suspend fun getModelConfig(modelId: String): ModelConfig? {
        return systemRepo.getConfigByModelId(modelId)
    }

    // HuggingFace Search functions
    fun openHFSearch() {
        _showHFSearch.value = true
        _hfSearchQuery.value = ""
        _hfSearchResults.value = emptyList()
        _hfSearchState.value = HFSearchState.Idle
    }

    fun closeHFSearch() {
        _showHFSearch.value = false
        _hfSearchQuery.value = ""
        _hfSearchResults.value = emptyList()
        _hfSearchState.value = HFSearchState.Idle
        _expandedRepoFiles.value = emptyMap()
    }

    fun updateHFSearchQuery(query: String) {
        _hfSearchQuery.value = query
    }

    fun searchHuggingFace(query: String) {
        if (query.isBlank()) {
            _hfSearchResults.value = emptyList()
            _hfSearchState.value = HFSearchState.Idle
            return
        }

        viewModelScope.launch {
            _hfSearchState.value = HFSearchState.Loading
            try {
                val response = HuggingFaceClient.api.searchModels(query)
                if (response.isSuccessful) {
                    _hfSearchResults.value = response.body() ?: emptyList()
                    _hfSearchState.value = HFSearchState.Success
                } else {
                    _hfSearchState.value = HFSearchState.Error("Search failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _hfSearchState.value = HFSearchState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addRepoFromSearch(result: HuggingFaceSearchResult) {
        val repo = HFModelRepository(
            id = result.id.replace("/", "-"),
            name = result.id.substringAfterLast("/"),
            repoPath = result.id,
            modelType = ModelType.GGUF
        )
        addRepository(repo)
    }

    fun fetchRepoFiles(repoId: String) {
        viewModelScope.launch {
            try {
                val response = HuggingFaceClient.api.getRepoFiles(repoId)
                if (response.isSuccessful) {
                    val ggufFiles = response.body()?.filter { 
                        it.path.lowercase().endsWith(".gguf")
                    } ?: emptyList()
                    _expandedRepoFiles.value = _expandedRepoFiles.value + (repoId to ggufFiles)
                }
            } catch (e: Exception) {
                Log.e("ModelStoreViewModel", "Error fetching repo files", e)
            }
        }
    }

    fun downloadFromSearchResult(result: HuggingFaceSearchResult, file: com.dark.tool_neuron.network.HuggingFaceFileResponse) {
        val context = getApplication<Application>()
        val fileUrl = "https://huggingface.co/${result.id}/resolve/main/${file.path}"

        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_START_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, "${result.id}_${file.path}".replace("/", "_"))
            putExtra(ModelDownloadService.EXTRA_MODEL_NAME, file.path.substringAfterLast("/"))
            putExtra(ModelDownloadService.EXTRA_FILE_URL, fileUrl)
            putExtra(ModelDownloadService.EXTRA_IS_ZIP, false)
            putExtra(ModelDownloadService.EXTRA_MODEL_TYPE, ModelType.GGUF.name)
            putExtra(ModelDownloadService.EXTRA_RUN_ON_CPU, true)
            putExtra(ModelDownloadService.EXTRA_TEXT_EMBEDDING_SIZE, 768)
        }

        context.startForegroundService(intent)
    }

    fun isRepoInLibrary(repoId: String): Boolean {
        return cachedRepos.any { it.repoPath == repoId }
    }
}