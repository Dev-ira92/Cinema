
package com.cinema.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinema.data.api.VidApiClient
import com.cinema.data.model.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class HomeUiState(
    val loading: Boolean = false,
    val searching: Boolean = false,
    val query: String = "",
    val movies: List<MediaItem> = emptyList(),
    val tvShows: List<MediaItem> = emptyList(),
    val trending: List<MediaItem> = emptyList(),
    val results: List<MediaItem> = emptyList(),
    val error: String? = null,
    val searchError: String? = null
)

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var homeJob: Job? = null
    private var searchRequestId = 0L

    init {
        loadHome()
    }

    fun loadHome() {
        if (homeJob?.isActive == true) return

        homeJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null
            )

            try {
                val data = coroutineScope {
                    val moviesRequest = async {
                        VidApiClient.service.latestMovies(1)
                    }

                    val tvRequest = async {
                        VidApiClient.service.latestTvShows(1)
                    }

                    Pair(
                        moviesRequest.await(),
                        tvRequest.await()
                    )
                }

                val movies = data.first.items.mapNotNull { item ->
                    val numericId = item.tmdbId
                        ?.toIntOrNull()
                        ?: return@mapNotNull null

                    MediaItem(
                        id = numericId,
                        mediaType = "movie",
                        title = item.displayTitle,
                        releaseDate = item.year,
                        rating = item.rating?.toDoubleOrNull() ?: 0.0,
                        popularity = item.popularity?.toDoubleOrNull() ?: 0.0,
                        imdbId = item.validImdbId,
                        posterUrlOverride = item.displayPoster
                    )
                }.distinctBy { it.id }

                val tvShows = data.second.items.mapNotNull { item ->
                    val numericId = item.tmdbId
                        ?.toIntOrNull()
                        ?: return@mapNotNull null

                    MediaItem(
                        id = numericId,
                        mediaType = "tv",
                        name = item.displayTitle,
                        firstAirDate = item.year,
                        rating = item.rating?.toDoubleOrNull() ?: 0.0,
                        popularity = item.popularity?.toDoubleOrNull() ?: 0.0,
                        imdbId = item.validImdbId,
                        posterUrlOverride = item.displayPoster
                    )
                }.distinctBy { it.id }

                val recentlyAdded = (
                    movies.take(12) + tvShows.take(12)
                ).distinctBy {
                    "${it.normalizedType}:${it.id}"
                }

                _state.value = _state.value.copy(
                    loading = false,
                    movies = movies,
                    tvShows = tvShows,
                    trending = recentlyAdded,
                    error = if (movies.isEmpty() && tvShows.isEmpty()) {
                        "VidAPI returned no usable catalog items."
                    } else {
                        null
                    }
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: HttpException) {
                val message = when (e.code()) {
                    401, 403 ->
                        "VidAPI access was denied. Check service configuration."

                    404 ->
                        "VidAPI catalog endpoint was not found."

                    429 ->
                        "Too many requests. Please try again later."

                    in 500..599 ->
                        "VidAPI is temporarily unavailable."

                    else ->
                        "Unable to load catalog. HTTP ${e.code()}."
                }

                _state.value = _state.value.copy(
                    loading = false,
                    error = message
                )

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Unable to load movies and TV shows. Check your internet connection."
                )
            }
        }
    }

    fun onQueryChanged(value: String) {
        searchRequestId++
        val currentRequestId = searchRequestId

        searchJob?.cancel()

        _state.value = _state.value.copy(
            query = value,
            searching = false,
            results = emptyList(),
            searchError = null
        )

        val query = value.trim()

        if (query.length < 2) return

        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(searching = true)

            try {
                delay(250)

                if (currentRequestId != searchRequestId) {
                    return@launch
                }

                val current = _state.value

                val catalog = (
                    current.movies + current.tvShows
                ).distinctBy {
                    "${it.normalizedType}:${it.id}"
                }

                val normalizedQuery = query.lowercase()

                val filtered = catalog.filter { item ->
                    item.displayTitle.lowercase().contains(normalizedQuery) ||
                    item.originalDisplayTitle.lowercase().contains(normalizedQuery) ||
                    item.overview.lowercase().contains(normalizedQuery) ||
                    item.imdbId?.lowercase()?.contains(normalizedQuery) == true
                }

                if (currentRequestId != searchRequestId) {
                    return@launch
                }

                _state.value = _state.value.copy(
                    searching = false,
                    results = filtered,
                    searchError = if (filtered.isEmpty()) {
                        "No matching titles in the loaded VidAPI catalog."
                    } else {
                        null
                    }
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                if (currentRequestId != searchRequestId) {
                    return@launch
                }

                _state.value = _state.value.copy(
                    searching = false,
                    results = emptyList(),
                    searchError = "Search failed. Please try again."
                )
            }
        }
    }

    fun clearSearch() {
        searchRequestId++

        searchJob?.cancel()
        searchJob = null

        _state.value = _state.value.copy(
            query = "",
            results = emptyList(),
            searching = false,
            searchError = null
        )
    }
}
