
package com.cinema.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinema.data.api.TmdbApi
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
                        TmdbApi.service.popularMovies()
                    }

                    val tvRequest = async {
                        TmdbApi.service.popularTv()
                    }

                    val trendingRequest = async {
                        TmdbApi.service.trendingAll()
                    }

                    Triple(
                        moviesRequest.await(),
                        tvRequest.await(),
                        trendingRequest.await()
                    )
                }

                _state.value = _state.value.copy(
                    loading = false,
                    movies = data.first.results,
                    tvShows = data.second.results,
                    trending = data.third.results,
                    error = null
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Unable to load movies and TV shows. Check your internet connection and TMDB API configuration."
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

        if (query.length < 2) {
            return
        }

        // Show loading immediately, including during debounce.
        _state.value = _state.value.copy(
            searching = true
        )

        searchJob = viewModelScope.launch {

            try {
                // Debounce rapid typing.
                delay(350)

                val response = TmdbApi.service.searchMulti(
                    query = query,
                    language = "en-US",
                    page = 1,
                    includeAdult = false
                )

                // Ignore outdated responses.
                if (currentRequestId != searchRequestId) {
                    return@launch
                }

                val filtered = response.results
                    .filter {
                        it.id > 0 &&
                            (it.mediaType == "movie" ||
                                it.mediaType == "tv")
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
                        "TMDB rejected the API credentials. Check your configuration."

                    404 ->
                        "The search service could not be found."

                    429 ->
                        "Too many searches. Please wait a moment and try again."

                    in 500..599 ->
                        "TMDB is temporarily unavailable. Please try again later."

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
