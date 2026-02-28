package com.dark.tool_neuron.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.network.HuggingFaceFileResponse
import com.dark.tool_neuron.network.HuggingFaceSearchResult
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.HFSearchState
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuggingFaceSearchScreen(
    viewModel: ModelStoreViewModel,
    onClose: () -> Unit
) {
    val searchQuery by viewModel.hfSearchQuery.collectAsState()
    val searchResults by viewModel.hfSearchResults.collectAsState()
    val searchState by viewModel.hfSearchState.collectAsState()
    val expandedRepoFiles by viewModel.expandedRepoFiles.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()

    val focusManager = LocalFocusManager.current
    var localQuery by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        localQuery = searchQuery
    }

    LaunchedEffect(localQuery) {
        delay(500)
        if (localQuery != searchQuery && localQuery.isNotBlank()) {
            viewModel.updateHFSearchQuery(localQuery)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search HuggingFace") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TextField(
                value = localQuery,
                onValueChange = { localQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(16.dp), vertical = rDp(8.dp)),
                placeholder = { Text("Search GGUF models on HuggingFace") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(rDp(12.dp)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.updateHFSearchQuery(localQuery)
                        viewModel.searchHuggingFace(localQuery)
                        focusManager.clearFocus()
                    }
                )
            )

            when (val state = searchState) {
                is HFSearchState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(rDp(48.dp)),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Search GGUF models on HuggingFace",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is HFSearchState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is HFSearchState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
                        ) {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { viewModel.searchHuggingFace(localQuery) }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                is HFSearchState.Success -> {
                    if (searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No results found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = rDp(12.dp),
                                vertical = rDp(8.dp)
                            ),
                            verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                        ) {
                            items(searchResults, key = { it.id }) { result ->
                                HFSearchResultCard(
                                    result = result,
                                    isInLibrary = viewModel.isRepoInLibrary(result.id),
                                    files = expandedRepoFiles[result.id],
                                    onAddToRepos = { viewModel.addRepoFromSearch(result) },
                                    onBrowseFiles = { viewModel.fetchRepoFiles(result.id) },
                                    onDownloadFile = { file -> viewModel.downloadFromSearchResult(result, file) },
                                    downloadStates = downloadStates
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HFSearchResultCard(
    result: HuggingFaceSearchResult,
    isInLibrary: Boolean,
    files: List<HuggingFaceFileResponse>?,
    onAddToRepos: () -> Unit,
    onBrowseFiles: () -> Unit,
    onDownloadFile: (HuggingFaceFileResponse) -> Unit,
    downloadStates: Map<String, ModelDownloadService.DownloadState>
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(rDp(12.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                Icon(
                    painter = painterResource(R.drawable.huggingface),
                    contentDescription = null,
                    modifier = Modifier.size(rDp(32.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.id.substringAfterLast("/"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    result.author?.let { author ->
                        CaptionText(text = author)
                    }
                }

                if (isInLibrary) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "In Library",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(rDp(20.dp))
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                result.downloads?.let { downloads ->
                    CaptionText(text = "${formatNumber(downloads)} downloads")
                }
                result.likes?.let { likes ->
                    CaptionText(text = "${formatNumber(likes)} likes")
                }
                result.pipeline_tag?.let { tag ->
                    AssistChip(
                        onClick = { },
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            result.tags?.take(3)?.let { tags ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(4.dp))
                ) {
                    tags.take(3).forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                Button(
                    onClick = onAddToRepos,
                    enabled = !isInLibrary,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isInLibrary) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(rDp(16.dp))
                        )
                        Spacer(modifier = Modifier.width(rDp(4.dp)))
                    }
                    Text(if (isInLibrary) "In Library" else "Add to Repos")
                }

                OutlinedButton(
                    onClick = {
                        if (!expanded) {
                            onBrowseFiles()
                        }
                        expanded = !expanded
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(rDp(16.dp))
                    )
                    Spacer(modifier = Modifier.width(rDp(4.dp)))
                    Text(if (expanded) "Hide Files" else "Browse Files")
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(rDp(4.dp))) {
                    if (files == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(rDp(20.dp)),
                                strokeWidth = rDp(2.dp)
                            )
                        }
                    } else if (files.isEmpty()) {
                        Text(
                            text = "No GGUF files found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        files.forEach { file ->
                            // DEBUG: Log the key being used for download state lookup
                            val downloadKey = "${result.id}_${file.path}".replace("/", "_")
                            android.util.Log.d("HFSearch", "FileDownloadItem: downloadKey='$downloadKey', result.id='${result.id}', file.path='${file.path}'")
                            FileDownloadItem(
                                file = file,
                                onDownload = { onDownloadFile(file) },
                                downloadState = downloadStates[downloadKey]
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileDownloadItem(
    file: HuggingFaceFileResponse,
    onDownload: () -> Unit,
    downloadState: ModelDownloadService.DownloadState?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(rDp(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(8.dp), vertical = rDp(6.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.path.substringAfterLast("/"),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                file.size?.let { size ->
                    CaptionText(text = formatFileSize(size))
                }
            }

            when (downloadState) {
                is ModelDownloadService.DownloadState.Downloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(rDp(24.dp)),
                        strokeWidth = rDp(2.dp)
                    )
                }
                is ModelDownloadService.DownloadState.Extracting,
                is ModelDownloadService.DownloadState.Processing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(rDp(24.dp)),
                        strokeWidth = rDp(2.dp)
                    )
                }
                is ModelDownloadService.DownloadState.Success -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(rDp(20.dp))
                    )
                }
                is ModelDownloadService.DownloadState.Error -> {
                    TextButton(onClick = onDownload) {
                        Text("Retry", style = MaterialTheme.typography.labelSmall)
                    }
                }
                is ModelDownloadService.DownloadState.Cancelled -> {
                    IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                null -> {
                    IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun formatNumber(number: Int): String {
    return when {
        number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
        number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
        else -> number.toString()
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format("%.1f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}
