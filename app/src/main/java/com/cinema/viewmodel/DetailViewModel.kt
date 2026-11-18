package com.cinema.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinema.data.api.TmdbApi
import com.cinema.data.api.TmdbDetails
import com.cinema.data.api.TmdbEpisode
import com.cinema.data.api.TranslateApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class DetailUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val details: TmdbDetails? = null,

    val overview: String? = null,
    val overviewIsEnglish: Boolean = false,

    val translating: Boolean = false,
    val translation: String? = null,
    val showTranslation: Boolean = false,
    val translateError: String? = null,

    val episodes: List<TmdbEpisode> = emptyList(),
    val episodesLoading: Boolean = false,
    val episodesError: String? = null
)

class DetailViewModel : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var loadedToken: String? = null
    private var tmdbId: Int? = null
    private var seasonJob: Job? = null
    private val seasonCache = mutableMapOf<Int, List<TmdbEpisode>>()

    fun load(tmdbIdText: String?, imdbId: String?, isTv: Boolean, key: String) {
        if (key.isBlank() || (tmdbIdText == null && imdbId == null)) return

        val token = "$tmdbIdText|$imdbId|$isTv|$key"
        if (loadedToken == token) return
        loadedToken = token
        tmdbId = null
        seasonCache.clear()

        _state.value = DetailUiState(loading = true)

        viewModelScope.launch {
            try {
                val api = TmdbApi.service

                val id = tmdbIdText?.toIntOrNull()
                    ?: resolveFromImdb(imdbId, isTv)
                    ?: throw IllegalStateException("This title was not found on TMDB.")
                tmdbId = id

                val details = if (isTv) {
                    api.tv(id, "fa-IR", "credits")
                } else {
                    api.movie(id, "fa-IR", "credits")
                }

                var overview = details.overview?.trim().orEmpty()
                var english = false
                if (overview.isBlank()) {
                    val fallback = if (isTv) api.tv(id, "en-US", "") else api.movie(id, "en-US", "")
                    overview = fallback.overview?.trim().orEmpty()
                    english = overview.isNotBlank()
                }

                _state.update {
                    it.copy(
                        loading = false,
                        details = details,
                        overview = overview.ifBlank { null },
                        overviewIsEnglish = english
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadedToken = null
                _state.update { it.copy(loading = false, error = e.toMessage()) }
            }
        }
    }

    fun retry(tmdbIdText: String?, imdbId: String?, isTv: Boolean, key: String) {
        loadedToken = null
        load(tmdbIdText, imdbId, isTv, key)
    }

    fun loadSeason(season: Int) {
        val id = tmdbId ?: return

        seasonCache[season]?.let { cached ->
            _state.update {
                it.copy(episodes = cached, episodesLoading = false, episodesError = null)
            }
            return
        }

        seasonJob?.cancel()
        _state.update { it.copy(episodes = emptyList(), episodesLoading = true, episodesError = null) }

        seasonJob = viewModelScope.launch {
            try {
                val episodes = TmdbApi.service.season(id, season, "en-US").episodes.orEmpty()
                seasonCache[season] = episodes
                _state.update { it.copy(episodes = episodes, episodesLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(episodesLoading = false, episodesError = e.toMessage()) }
            }
        }
    }

    fun toggleTranslation() {
        val current = _state.value
        val text = current.overview ?: return

        if (current.translation != null) {
            _state.update { it.copy(showTranslation = !it.showTranslation) }
            return
        }
        if (current.translating) return

        _state.update { it.copy(translating = true, translateError = null) }

        viewModelScope.launch {
            try {
                val translated = TranslateApi.toPersian(text)
                _state.update {
                    it.copy(translating = false, translation = translated, showTranslation = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(translating = false, translateError = "Translation failed. Try again later.")
                }
            }
        }
    }

    private suspend fun resolveFromImdb(imdbId: String?, isTv: Boolean): Int? {
        if (imdbId == null) return null
        val result = TmdbApi.service.find(imdbId, "imdb_id")
        return (if (isTv) result.tv else result.movies)?.firstOrNull()?.id
    }

    private fun Throwable.toMessage(): String = when (this) {
        is HttpException -> when (code()) {
            401 -> "TMDB rejected the key. Check it and try again."
            404 -> "This title was not found on TMDB."
            429 -> "Too many requests. Try again in a moment."
            else -> "Request failed (HTTP ${code()})."
        }
        is IOException -> "Network error. TMDB is blocked in some countries; a VPN may be required."
        is IllegalStateException -> message ?: "Not found on TMDB."
        else -> "Something went wrong."
    }
}
