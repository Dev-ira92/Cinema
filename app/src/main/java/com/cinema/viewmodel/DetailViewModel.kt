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
    val loading: Boolean = false,
    val movie: MovieDetail? = null,
    val tv: TvDetail? = null,
    val season: TvSeasonDetail? = null,
    val selectedSeason: Int = 1,
    val selectedEpisode: TvEpisode? = null,
    val subtitles: List<SubdlItem> = emptyList(),
    val subtitleLoading: Boolean = false,
    val subtitleError: String? = null,
    val error: String? = null
)

class DetailViewModel : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var currentItem: MediaItem? = null
    private var detailJob: Job? = null
    private var subtitleJob: Job? = null

    fun load(item: MediaItem) {
        currentItem = item
        detailJob?.cancel()
        subtitleJob?.cancel()

        detailJob = viewModelScope.launch {
            _state.value = DetailUiState(
                loading = true,
                selectedSeason = 1
            )

            try {
                if (item.normalizedType == "movie") {
                    val movie = TmdbApi.service.movieDetail(item.id)

                    _state.value = DetailUiState(
                        loading = false,
                        movie = movie
                    )
                } else {
                    val tv = TmdbApi.service.tvDetail(item.id)

                    val firstSeason = tv.seasons
                        .firstOrNull { it.seasonNumber > 0 }
                        ?.seasonNumber ?: 1

                    val season = TmdbApi.service.seasonDetail(
                        item.id,
                        firstSeason
                    )

                    _state.value = DetailUiState(
                        loading = false,
                        tv = tv,
                        season = season,
                        selectedSeason = firstSeason
                    )
                }

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Unable to load title details. Please try again."
                )
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        val item = currentItem ?: return

        if (item.normalizedType != "tv") return
        if (seasonNumber < 0) return

        detailJob?.cancel()
        subtitleJob?.cancel()

        detailJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                selectedSeason = seasonNumber,
                selectedEpisode = null,
                subtitles = emptyList(),
                subtitleError = null,
                error = null
            )

            try {
                val season = TmdbApi.service.seasonDetail(
                    item.id,
                    seasonNumber
                )

                _state.value = _state.value.copy(
                    loading = false,
                    season = season
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Unable to load episodes for this season."
                )
            }
        }
    }

    fun selectEpisode(episode: TvEpisode) {
        subtitleJob?.cancel()

        _state.value = _state.value.copy(
            selectedEpisode = episode,
            subtitles = emptyList(),
            subtitleError = null,
            subtitleLoading = false
        )
    }

    fun searchSubtitles(
        context: Context,
        customTitle: String = ""
    ) {
        val item = currentItem ?: return
        val current = _state.value

        val apiKey = try {
            SubtitleKeyStore.get(context)
        } catch (e: Exception) {
            _state.value = current.copy(
                subtitleError = "Unable to access the saved SubDL API key."
            )
            return
        }

        if (apiKey.isBlank()) {
            _state.value = current.copy(
                subtitleError = "Please enter your SubDL API key in Settings."
            )
            return
        }

        if (
            item.normalizedType == "tv" &&
            current.selectedEpisode == null
        ) {
            _state.value = current.copy(
                subtitleError = "Please select a season and episode first."
            )
            return
        }

        subtitleJob?.cancel()

        subtitleJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                subtitleLoading = true,
                subtitles = emptyList(),
                subtitleError = null
            )

            try {
                val title = customTitle.trim().ifBlank {
                    item.displayTitle
                }

                val episode = current.selectedEpisode

                val imdbId = if (item.normalizedType == "movie") {
                    current.movie?.imdbId
                } else {
                    current.tv?.imdbId
                }

                val year = if (item.normalizedType == "movie") {
                    current.movie?.year?.toIntOrNull()
                        ?: item.year.toIntOrNull()
                } else {
                    current.tv?.year?.toIntOrNull()
                        ?: item.year.toIntOrNull()
                }

                val response = SubtitleApi.service.search(
                    key = apiKey,
                    imdbId = imdbId,
                    tmdbId = item.id,
                    filmName = title,
                    year = year,
                    languages = "FA,EN",
                    type = item.normalizedType,
                    season = episode?.seasonNumber,
                    episode = episode?.episodeNumber,
                    perPage = 30,
                    unpack = 1
                )

                val results = response.subtitles
                    .distinctBy { it.url }
                    .filter { it.url.isNotBlank() }

                _state.value = _state.value.copy(
                    subtitleLoading = false,
                    subtitles = results,
                    subtitleError = if (results.isEmpty()) {
                        "No subtitles were found. Try another title or release name."
                    } else {
                        null
                    }
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: retrofit2.HttpException) {
                val message = when (e.code()) {
                    401, 403 -> "SubDL rejected the API key. Check your key and account access."
                    404 -> "The subtitle service endpoint was not found."
                    429 -> "Too many subtitle searches. Please wait and try again."
                    else -> "Subtitle search failed: HTTP ${e.code()}."
                }

                _state.value = _state.value.copy(
                    subtitleLoading = false,
                    subtitles = emptyList(),
                    subtitleError = message
                )

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    subtitleLoading = false,
                    subtitles = emptyList(),
                    subtitleError = "Unable to search subtitles. Check your connection and try again."
                )
            }
        }
    }

    fun clearSubtitles() {
        subtitleJob?.cancel()

        _state.value = _state.value.copy(
            subtitles = emptyList(),
            subtitleError = null,
            subtitleLoading = false
        )
    }
}
