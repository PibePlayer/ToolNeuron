# HuggingFace Search Feature

## Overview

This feature adds a HuggingFace search icon to the Model Store screen that allows users to search for GGUF models directly from HuggingFace. Users can either add repositories to their library or download individual model files.

## Features

### Search GGUF Models
- Search for GGUF models on HuggingFace directly from the app
- Results are filtered to show only GGUF files
- Results sorted by download count (most popular first)

### Add to Library
- Add any search result as a repository to your library
- Added repos appear in the SETTINGS tab and can be browsed in the MODELS tab
- Already-added repos show a checkmark badge

### Browse and Download Files
- Expand any search result to see available GGUF files
- Download individual files directly without adding the entire repository
- Download progress is tracked in the existing download system

## Usage

1. Open the Model Store screen
2. Look for the HuggingFace icon (orange face) in the top bar
3. Tap the icon to open the search overlay
4. Enter a search query (e.g., "llama", "mistral", "phi")
5. Browse results and either:
   - Tap "Add to Repos" to add the entire repository to your library
   - Tap "Browse Files" to expand and see individual GGUF files
   - Tap the download icon next to any file to download it directly

## GitHub CI Actions

The project includes GitHub Actions workflow (`.github/workflows/android.yml`) that:

1. **Builds Debug and Release APKs** on every push to a tag (e.g., `v1.0.0`)
2. **Creates a GitHub Release** with the built APKs attached

### How to Release

1. Create a new tag:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

2. The workflow will automatically:
   - Build both debug and release APKs
   - Create a GitHub release with the APKs
   - Users can download the APK from the release page

### Manual Build

You can also trigger the workflow manually from the GitHub Actions tab.

## Technical Details

### API
- Uses HuggingFace's `/api/models` endpoint
- Filter: `gguf` (hardcoded for relevance)
- Sort: `downloads` (most popular first)
- Limit: 20 results

### Architecture
- **HuggingFaceApi.kt**: Added `searchModels()` endpoint
- **ModelStoreViewModel.kt**: Added state management for search
- **HuggingFaceSearchScreen.kt**: New composable for the search UI
- **ModelStoreScreen.kt**: Added HuggingFace icon and overlay
- **huggingface.xml**: New drawable for the HuggingFace logo

### State Management
- `hfSearchQuery`: Current search text
- `hfSearchResults`: List of search results
- `hfSearchState`: Loading/Success/Error state
- `showHFSearch`: Visibility of search overlay
- `expandedRepoFiles`: Cache for expanded file lists

### Reuses Existing Infrastructure
- `ModelDownloadService` for downloads
- `addRepository()` flow for adding repos
- `HFModelRepository` data model
