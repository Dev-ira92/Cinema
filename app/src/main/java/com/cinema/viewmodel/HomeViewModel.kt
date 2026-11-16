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

data class HomeUiState(
    val loading: Boolean = false,
    val searching: Boolean = false,
    val query: String = "",
    val movies: List<MediaItem> = emptyList(),
    val tvShows: List<MediaItem> = emptyList(),
    val trending: List<MediaItem> = emptyList(),
    val results: List<MediaItem> = emptyList(),
    val error: String? = null
)

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var homeJob: Job? = null

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
        _state.value = _state.value.copy(
            query = value,
            error = null
        )

        searchJob?.cancel()

        val query = value.trim()

        if (query.length < 2) {
            _state.value = _state.value.copy(
                searching = false,
                results = emptyList()
            )
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)

            _state.value = _state.value.copy(
                searching = true,
                error = null
            )

            try {
                val response = TmdbApi.service.searchMulti(
                    query = query,
                    language = "en-US"
                )

                val filtered = response.results.filter {
                    it.mediaType == "movie" ||
                        it.mediaType == "tv"
                }

                _state.value = _state.value.copy(
                    searching = false,
                    results = filtered,
                    error = if (filtered.isEmpty()) {
                        "No matching movies or TV shows were found."
                    } else {
                        null
                    }
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    searching = false,
                    results = emptyList(),
                    error = "Search failed. Please check your connection and try again."
                )
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()

        _state.value = _state.value.copy(
            query = "",
            results = emptyList(),
            searching = false,
            error = null
        )
    }
}
