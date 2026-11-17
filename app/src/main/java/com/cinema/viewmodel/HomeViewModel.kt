package com.cinema.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinema.data.api.TmdbApi
import com.cinema.data.api.VidApi
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

    // -----------------------------------------------------
    // HOME CATALOG - VIDAPI
    // -----------------------------------------------------

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
                        VidApi.service.latestMovies(1)
                    }

                    val tvRequest = async {
                        VidApi.service.latestTvShows(1)
                    }

                    Pair(
                        moviesRequest.await(),
                        tvRequest.await()
                    )
                }

                val movies = data.first.items
                    .mapNotNull { item ->

                        val numericId = item.tmdbId?.toIntOrNull()
                            ?: return@mapNotNull null

                        MediaItem(
                            id = numericId,
                            mediaType = "movie",
                            title = item.title,
                            releaseDate = item.year,
                            rating = item.rating.toDoubleOrNull() ?: 0.0,
                            popularity = item.popularity.toDoubleOrNull() ?: 0.0,
                            imdbId = item.imdbId,
                            posterUrlOverride = item.posterUrl
                        )
                    }
                    .distinctBy { it.id }

                val tvShows = data.second.items
                    .mapNotNull { item ->

                        val numericId = item.tmdbId?.toIntOrNull()
                            ?: return@mapNotNull null

                        MediaItem(
                            id = numericId,
                            mediaType = "tv",
                            name = item.title,
                            firstAirDate = item.year,
                            rating = item.rating.toDoubleOrNull() ?: 0.0,
                            popularity = item.popularity.toDoubleOrNull() ?: 0.0,
                            imdbId = item.imdbId,
                            posterUrlOverride = item.posterUrl
                        )
                    }
                    .distinctBy { it.id }

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
                    error = if (
                        movies.isEmpty() && tvShows.isEmpty()
                    ) {
                        "VidAPI returned no catalog items."
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

    // -----------------------------------------------------
    // SEARCH - TEMPORARILY TMDB
    // -----------------------------------------------------

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

        if (query.length < 2) {
            return
        }

        _state.value = _state.value.copy(
            searching = true
        )

        searchJob = viewModelScope.launch {

            try {

                delay(350)

                val response = TmdbApi.service.searchMulti(
                    query = query,
                    language = "en-US",
                    page = 1,
                    includeAdult = false
                )

                if (currentRequestId != searchRequestId) {
                    return@launch
                }

                val filtered = response.results
                    .filter {
                        it.id > 0 &&
                            (
                                it.mediaType == "movie" ||
                                    it.mediaType == "tv"
                            )
                    }
                    .distinctBy {
                        "${it.mediaType}:${it.id}"
                    }

                _state.value = _state.value.copy(
                    searching = false,
                    results = filtered,
                    searchError = if (filtered.isEmpty()) {
                        "No matching movies or TV shows were found. Try another title."
                    } else {
                        null
                    }
                )

            } catch (e: CancellationException) {

                throw e

            } catch (e: HttpException) {

                if (currentRequestId != searchRequestId) {
                    return@launch
                }

                val message = when (e.code()) {

                    401, 403 ->
                        "TMDB search is unavailable. Search migration is not complete yet."

                    404 ->
                        "The search service could not be found."

                    429 ->
                        "Too many searches. Please wait a moment and try again."

                    in 500..599 ->
                        "Search service is temporarily unavailable."

                    else ->
                        "Search failed: HTTP ${e.code()}."
                }

                _state.value = _state.value.copy(
                    searching = false,
                    results = emptyList(),
                    searchError = message
                )

            } catch (e: Exception) {

                if (currentRequestId != searchRequestId) {
                    return@launch
                }

                _state.value = _state.value.copy(
                    searching = false,
                    results = emptyList(),
                    searchError = "Search failed. Check your internet connection and try again."
                )
            }
        }
    }

    // -----------------------------------------------------
    // CLEAR SEARCH
    // -----------------------------------------------------

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
