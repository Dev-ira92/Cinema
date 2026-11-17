
package com.cinema.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinema.data.SubtitleKeyStore
import com.cinema.data.api.SubtitleApi
import com.cinema.data.model.MediaItem
import com.cinema.data.model.SubdlItem
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
    val selectedSeason: Int = 1,
    val selectedEpisode: TvEpisode? = null,
    val subtitles: List<SubdlItem> = emptyList(),
    val subtitleLoading: Boolean = false,
    val subtitleError: String? = null,
    val error: String? = null,
    val streamLoading: Boolean = false,
    val streamStatus: String = "",
    val streamError: String? = null
)

class DetailViewModel : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var currentItem: MediaItem? = null
    private var subtitleJob: Job? = null

    fun load(item: MediaItem) {
        currentItem = item
        subtitleJob?.cancel()

        _state.value = DetailUiState(
            loading = false,
            selectedSeason = 1
        )
    }

    fun selectSeason(seasonNumber: Int) {
        val item = currentItem ?: return

        if (item.normalizedType != "tv" || seasonNumber < 1) {
            return
        }

        subtitleJob?.cancel()

        _state.value = _state.value.copy(
            selectedSeason = seasonNumber,
            selectedEpisode = null,
            subtitles = emptyList(),
            subtitleLoading = false,
            subtitleError = null,
            streamError = null,
            error = null
        )
    }

    fun selectEpisode(episode: TvEpisode) {
        subtitleJob?.cancel()

        _state.value = _state.value.copy(
            selectedSeason = episode.seasonNumber,
            selectedEpisode = episode,
            subtitles = emptyList(),
            subtitleError = null,
            subtitleLoading = false,
            streamError = null
        )
    }

    fun selectEpisode(
        seasonNumber: Int,
        episodeNumber: Int
    ) {
        if (seasonNumber < 1 || episodeNumber < 1) {
            return
        }

        selectEpisode(
            TvEpisode(
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber
            )
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

        val imdbId = item.imdbId?.trim()?.takeIf {
            Regex("^tt[0-9]{5,12}$").matches(it)
        }

        if (imdbId == null) {
            _state.value = current.copy(
                subtitleError = "IMDb ID is not available for this title."
            )
            return
        }

        val isTv = item.normalizedType == "tv"

        if (isTv && current.selectedEpisode == null) {
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
                val episode = current.selectedEpisode
                val year = item.year.toIntOrNull()

                val primaryTitle = customTitle.trim()
                    .ifBlank { item.displayTitle }

                val fallback = listOf(
                    item.displayTitle,
                    item.originalDisplayTitle
                ).firstOrNull {
                    it.isNotBlank() &&
                    !it.equals(primaryTitle, ignoreCase = true)
                }

                suspend fun requestSubtitles(
                    title: String
                ): List<SubdlItem> {
                    return SubtitleApi.service.search(
                        key = apiKey,
                        imdbId = imdbId,
                        tmdbId = null,
                        filmName = title,
                        year = year,
                        languages = "FA,EN",
                        type = if (isTv) "tv" else "movie",
                        season = episode?.seasonNumber,
                        episode = episode?.episodeNumber,
                        perPage = 30,
                        unpack = 1
                    ).subtitles
                        .filter { it.url.isNotBlank() }
                        .distinctBy { it.url }
                }

                var results = requestSubtitles(primaryTitle)

                if (results.isEmpty() && fallback != null) {
                    results = requestSubtitles(fallback)
                }

                _state.value = _state.value.copy(
                    subtitleLoading = false,
                    subtitles = results,
                    subtitleError = if (results.isEmpty()) {
                        "No subtitles found. Try another title or release name."
                    } else {
                        null
                    }
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: retrofit2.HttpException) {
                val message = when (e.code()) {
                    401, 403 -> "SubDL rejected the API key."
                    404 -> "Subtitle endpoint not found."
                    429 -> "Too many requests. Please wait."
                    else -> "Subtitle search failed: HTTP ${e.code()}."
                }

                _state.value = _state.value.copy(
                    subtitleLoading = false,
                    subtitleError = message
                )

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    subtitleLoading = false,
                    subtitleError = "Unable to search subtitles. Check your connection."
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

    fun clear() {
        subtitleJob?.cancel()
        currentItem = null
        _state.value = DetailUiState()
    }
}
