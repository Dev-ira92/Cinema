package com.cinema.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinema.data.SubtitleKeyStore
import com.cinema.data.api.SubtitleApi
import com.cinema.data.api.TmdbApi
import com.cinema.data.model.MediaItem
import com.cinema.data.model.MovieDetail
import com.cinema.data.model.SubdlItem
import com.cinema.data.model.TvDetail
import com.cinema.data.model.TvEpisode
import com.cinema.data.model.TvSeasonDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading         : Boolean         = false,
    val movie           : MovieDetail?    = null,
    val tv              : TvDetail?       = null,
    val season          : TvSeasonDetail? = null,
    val selectedSeason  : Int             = 1,
    val selectedEpisode : TvEpisode?      = null,
    val subtitles       : List<SubdlItem> = emptyList(),
    val subtitleLoading : Boolean         = false,
    val subtitleError   : String?         = null,
    val error           : String?         = null,
    val streamLoading   : Boolean         = false,
    val streamStatus    : String          = "",
    val streamError     : String?         = null
)

class DetailViewModel : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var currentItem  : MediaItem? = null
    private var detailJob    : Job? = null
    private var seasonJob    : Job? = null
    private var subtitleJob  : Job? = null

    // ── load TMDB detail ─────────────────────────────────────────────────────

    fun load(item: MediaItem) {
        currentItem = item
        detailJob?.cancel()
        seasonJob?.cancel()
        subtitleJob?.cancel()

        detailJob = viewModelScope.launch {
            _state.value = DetailUiState(loading = true)
            try {
                if (item.normalizedType == "movie") {
                    val detail = TmdbApi.service.movieDetail(item.id)
                    _state.value = DetailUiState(loading = false, movie = detail)
                } else {
                    val detail      = TmdbApi.service.tvDetail(item.id)
                    val firstSeason = detail.seasons
                        .firstOrNull { it.seasonNumber > 0 }?.seasonNumber ?: 1
                    val seasonData  = TmdbApi.service.seasonDetail(item.id, firstSeason)
                    _state.value = DetailUiState(
                        loading        = false,
                        tv             = detail,
                        season         = seasonData,
                        selectedSeason = firstSeason
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error   = "Could not load details. Check your internet connection."
                )
            }
        }
    }

    // ── season / episode ─────────────────────────────────────────────────────

    fun selectSeason(seasonNumber: Int) {
        val item = currentItem ?: return
        if (item.normalizedType != "tv" || seasonNumber < 0) return

        seasonJob?.cancel()
        subtitleJob?.cancel()

        _state.value = _state.value.copy(
            selectedSeason  = seasonNumber,
            selectedEpisode = null,
            season          = null,
            subtitles       = emptyList(),
            subtitleError   = null
        )

        seasonJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            try {
                val data = TmdbApi.service.seasonDetail(item.id, seasonNumber)
                _state.value = _state.value.copy(loading = false, season = data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error   = "Could not load episodes for Season $seasonNumber."
                )
            }
        }
    }

    fun selectEpisode(episode: TvEpisode) {
        subtitleJob?.cancel()
        _state.value = _state.value.copy(
            selectedEpisode = episode,
            subtitles       = emptyList(),
            subtitleError   = null,
            subtitleLoading = false
        )
    }

    // ── subtitles ────────────────────────────────────────────────────────────

    fun searchSubtitles(context: Context, customTitle: String = "") {
        val item    = currentItem ?: return
        val current = _state.value

        val apiKey = try {
            SubtitleKeyStore.get(context)
        } catch (e: Exception) {
            _state.value = current.copy(subtitleError = "Unable to access the saved SubDL API key.")
            return
        }

        if (apiKey.isBlank()) {
            _state.value = current.copy(subtitleError = "Please enter your SubDL API key in Settings.")
            return
        }

        val imdbId = when (item.normalizedType) {
            "movie" -> current.movie?.imdbId ?: item.imdbId
            else    -> current.tv?.imdbId    ?: item.imdbId
        }?.trim()?.takeIf { Regex("^tt[0-9]{5,12}$").matches(it) }

        if (imdbId == null) {
            _state.value = current.copy(subtitleError = "IMDb ID is not available for this title.")
            return
        }

        if (item.normalizedType == "tv" && current.selectedEpisode == null) {
            _state.value = current.copy(subtitleError = "Please select an episode first.")
            return
        }

        subtitleJob?.cancel()
        subtitleJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                subtitleLoading = true,
                subtitles       = emptyList(),
                subtitleError   = null
            )
            try {
                val episode      = current.selectedEpisode
                val year         = item.year.toIntOrNull()
                val primaryTitle = customTitle.trim().ifBlank { item.displayTitle }
                val fallback     = listOf(item.displayTitle, item.originalDisplayTitle)
                    .firstOrNull { it.isNotBlank() && !it.equals(primaryTitle, ignoreCase = true) }

                suspend fun req(title: String): List<SubdlItem> =
                    SubtitleApi.service.search(
                        key      = apiKey,
                        imdbId   = imdbId,
                        tmdbId   = null,
                        filmName = title,
                        year     = year,
                        languages= "FA,EN",
                        type     = item.normalizedType,
                        season   = episode?.seasonNumber,
                        episode  = episode?.episodeNumber,
                        perPage  = 30,
                        unpack   = 1
                    ).subtitles.filter { it.url.isNotBlank() }.distinctBy { it.url }

                var results = req(primaryTitle)
                if (results.isEmpty() && fallback != null) results = req(fallback)

                _state.value = _state.value.copy(
                    subtitleLoading = false,
                    subtitles       = results,
                    subtitleError   = if (results.isEmpty())
                        "No subtitles found. Try a different title or release name." else null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: retrofit2.HttpException) {
                _state.value = _state.value.copy(
                    subtitleLoading = false,
                    subtitleError   = when (e.code()) {
                        401, 403 -> "SubDL rejected the API key."
                        429      -> "Too many requests. Please wait."
                        else     -> "Subtitle search failed: HTTP ${e.code()}."
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    subtitleLoading = false,
                    subtitleError   = "Unable to search subtitles. Check your connection."
                )
            }
        }
    }

    fun clearSubtitles() {
        subtitleJob?.cancel()
        _state.value = _state.value.copy(
            subtitles       = emptyList(),
            subtitleError   = null,
            subtitleLoading = false
        )
    }

    fun clear() {
        detailJob?.cancel()
        seasonJob?.cancel()
        subtitleJob?.cancel()
        currentItem  = null
        _state.value = DetailUiState()
    }
}
