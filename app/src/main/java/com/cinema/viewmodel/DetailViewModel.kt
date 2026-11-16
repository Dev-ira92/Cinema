package com.cinema.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinema.data.StreamSource
import com.cinema.data.SubtitleKeyStore
import com.cinema.data.api.SubtitleApi
import com.cinema.data.api.TmdbApi
import com.cinema.data.extractMovieStreams
import com.cinema.data.extractTvStreams
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
    val loading         : Boolean           = false,
    val movie           : MovieDetail?      = null,
    val tv              : TvDetail?         = null,
    val season          : TvSeasonDetail?   = null,
    val selectedSeason  : Int               = 1,
    val selectedEpisode : TvEpisode?        = null,
    val subtitles       : List<SubdlItem>   = emptyList(),
    val subtitleLoading : Boolean           = false,
    val subtitleError   : String?           = null,
    val error           : String?           = null,
    // ── stream ──────────────────────────────────────
    val streams         : List<StreamSource> = emptyList(),
    val streamLoading   : Boolean            = false,
    val streamStatus    : String             = "",
    val streamError     : String?            = null,
)

class DetailViewModel : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var currentItem  : MediaItem? = null
    private var detailJob    : Job?       = null
    private var subtitleJob  : Job?       = null
    private var streamJob    : Job?       = null

    // ── load detail ──────────────────────────────────────────────────────────

    fun load(item: MediaItem) {
        currentItem = item
        detailJob?.cancel()
        subtitleJob?.cancel()
        streamJob?.cancel()

        detailJob = viewModelScope.launch {
            _state.value = DetailUiState(loading = true, selectedSeason = 1)
            try {
                if (item.normalizedType == "movie") {
                    val movie = TmdbApi.service.movieDetail(item.id)
                    _state.value = DetailUiState(loading = false, movie = movie)
                } else {
                    val tv          = TmdbApi.service.tvDetail(item.id)
                    val firstSeason = tv.seasons
                        .firstOrNull { it.seasonNumber > 0 }?.seasonNumber ?: 1
                    val season      = TmdbApi.service.seasonDetail(item.id, firstSeason)
                    _state.value    = DetailUiState(
                        loading        = false,
                        tv             = tv,
                        season         = season,
                        selectedSeason = firstSeason
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error   = "Unable to load title details. Please try again."
                )
            }
        }
    }

    // ── season / episode ─────────────────────────────────────────────────────

    fun selectSeason(seasonNumber: Int) {
        val item = currentItem ?: return
        if (item.normalizedType != "tv" || seasonNumber < 0) return
        detailJob?.cancel(); subtitleJob?.cancel(); streamJob?.cancel()
        detailJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                loading         = true,
                selectedSeason  = seasonNumber,
                selectedEpisode = null,
                subtitles       = emptyList(),
                subtitleError   = null,
                subtitleLoading = false,
                streams         = emptyList(),
                streamStatus    = "",
                streamError     = null,
                error           = null
            )
            try {
                val season = TmdbApi.service.seasonDetail(item.id, seasonNumber)
                _state.value = _state.value.copy(loading = false, season = season)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error   = "Unable to load episodes for this season."
                )
            }
        }
    }

    fun selectEpisode(episode: TvEpisode) {
        subtitleJob?.cancel(); streamJob?.cancel()
        _state.value = _state.value.copy(
            selectedEpisode = episode,
            subtitles       = emptyList(),
            subtitleError   = null,
            subtitleLoading = false,
            streams         = emptyList(),
            streamStatus    = "",
            streamError     = null
        )
    }

    // ── stream extraction ────────────────────────────────────────────────────

    fun fetchStreams(context: Context) {
        val item    = currentItem ?: return
        val current = _state.value
        if (current.streamLoading) return

        val imdbId = if (item.normalizedType == "movie") current.movie?.imdbId
                     else current.tv?.imdbId

        if (imdbId.isNullOrBlank()) {
            _state.value = current.copy(streamError = "IMDB ID not available for this title.")
            return
        }

        if (item.normalizedType == "tv" && current.selectedEpisode == null) {
            _state.value = current.copy(streamError = "Please select an episode first.")
            return
        }

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                streamLoading = true,
                streams       = emptyList(),
                streamStatus  = "Starting stream search...",
                streamError   = null
            )
            try {
                val sources = if (item.normalizedType == "movie") {
                    extractMovieStreams(context, imdbId) { status ->
                        _state.value = _state.value.copy(streamStatus = status)
                    }
                } else {
                    val ep = current.selectedEpisode!!
                    extractTvStreams(context, imdbId, ep.seasonNumber, ep.episodeNumber) { status ->
                        _state.value = _state.value.copy(streamStatus = status)
                    }
                }

                _state.value = _state.value.copy(
                    streamLoading = false,
                    streams       = sources,
                    streamStatus  = if (sources.isEmpty()) "No streams found" else "${sources.size} stream(s) found",
                    streamError   = if (sources.isEmpty()) "Could not find a stream. Try again or use a manual URL." else null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    streamLoading = false,
                    streamStatus  = "",
                    streamError   = "Stream search failed: ${e.localizedMessage}"
                )
            }
        }
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

        if (item.normalizedType == "tv" && current.selectedEpisode == null) {
            _state.value = current.copy(subtitleError = "Please select a season and episode first.")
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
                val imdbId       = if (item.normalizedType == "movie") current.movie?.imdbId
                                   else current.tv?.imdbId
                val year         = if (item.normalizedType == "movie")
                                       current.movie?.year?.toIntOrNull() ?: item.year.toIntOrNull()
                                   else current.tv?.year?.toIntOrNull() ?: item.year.toIntOrNull()
                val primaryTitle = customTitle.trim().ifBlank { item.displayTitle }
                val fallback     = listOf(item.displayTitle, item.originalDisplayTitle)
                    .firstOrNull { it.isNotBlank() && !it.equals(primaryTitle, ignoreCase = true) }

                suspend fun req(title: String): List<SubdlItem> =
                    SubtitleApi.service.search(
                        key      = apiKey,
                        imdbId   = imdbId,
                        tmdbId   = item.id,
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
                        "No subtitles found. Try another title or release name." else null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: retrofit2.HttpException) {
                val msg = when (e.code()) {
                    401, 403 -> "SubDL rejected the API key."
                    404      -> "Subtitle endpoint not found."
                    429      -> "Too many requests. Please wait."
                    else     -> "Subtitle search failed: HTTP ${e.code()}."
                }
                _state.value = _state.value.copy(subtitleLoading = false, subtitleError = msg)
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
}
