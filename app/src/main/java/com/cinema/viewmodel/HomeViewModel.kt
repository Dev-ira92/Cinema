package com.cinema.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinema.data.api.TmdbApi
import com.cinema.data.model.MediaItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    init {
        loadHome()
    }

    fun loadHome() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null
            )

            try {
                val movies = TmdbApi.service.popularMovies()
                val tv = TmdbApi.service.popularTv()
                val trending = TmdbApi.service.trendingAll()

                _state.value = _state.value.copy(
                    loading = false,
                    movies = movies.results,
                    tvShows = tv.results,
                    trending = trending.results
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "خطا در دریافت اطلاعات"
                )
            }
        }
    }

    fun onQueryChanged(value: String) {
        _state.value = _state.value.copy(query = value)

        searchJob?.cancel()

        if (value.trim().length < 2) {
            _state.value = _state.value.copy(
                searching = false,
                results = emptyList()
            )
            return
        }

        searchJob = viewModelScope.launch {
            delay(350)

            _state.value = _state.value.copy(
                searching = true,
                error = null
            )

            try {
                val response = TmdbApi.service.searchMulti(
                    query = value.trim(),
                    language = "en-US"
                )

                val filtered = response.results.filter {
                    it.mediaType == "movie" || it.mediaType == "tv"
                }

                _state.value = _state.value.copy(
                    searching = false,
                    results = filtered
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    searching = false,
                    error = e.message ?: "جست‌وجو ناموفق بود"
                )
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()

        _state.value = _state.value.copy(
            query = "",
            results = emptyList(),
            searching = false
        )
    }
}
