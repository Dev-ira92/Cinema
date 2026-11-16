
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

    val state: StateFlow<DetailUiState> =
        _state.asStateFlow()

    private var currentItem: MediaItem? = null

    // --------------------------------------------------
    // Load movie or TV details
    // --------------------------------------------------

    fun load(item: MediaItem) {

        currentItem = item

        viewModelScope.launch {

            _state.value = DetailUiState(
                loading = true,
                selectedSeason = 1
            )

            try {

                if (item.normalizedType == "movie") {

                    val movie =
                        TmdbApi.service.movieDetail(item.id)

                    _state.value = DetailUiState(
                        loading = false,
                        movie = movie
                    )

                } else {

                    val tv =
                        TmdbApi.service.tvDetail(item.id)

                    val firstSeason = tv.seasons
                        .firstOrNull {
                            it.seasonNumber > 0
                        }
                        ?.seasonNumber ?: 1

                    val season =
                        TmdbApi.service.seasonDetail(
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
                    error = e.message
                        ?: "دریافت اطلاعات ناموفق بود"
                )
            }
        }
    }

    // --------------------------------------------------
    // Select TV season
    // --------------------------------------------------

    fun selectSeason(seasonNumber: Int) {

        val item = currentItem ?: return

        if (item.normalizedType != "tv") return

        if (seasonNumber < 0) return

        viewModelScope.launch {

            _state.value = _state.value.copy(
                loading = true,
                selectedSeason = seasonNumber,
                selectedEpisode = null,
                subtitles = emptyList(),
                subtitleError = null,
                error = null
            )

            try {

                val season =
                    TmdbApi.service.seasonDetail(
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
                    error = e.message
                        ?: "دریافت قسمت‌ها ناموفق بود"
                )
            }
        }
    }

    // --------------------------------------------------
    // Select TV episode
    // --------------------------------------------------

    fun selectEpisode(episode: TvEpisode) {

        _state.value = _state.value.copy(
            selectedEpisode = episode,
            subtitles = emptyList(),
            subtitleError = null
        )
    }

    // --------------------------------------------------
    // Search subtitles
    // --------------------------------------------------

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
                subtitleError =
                    "دریافت کلید SubDL ناموفق بود: ${e.message}"
            )

            return
        }

        if (apiKey.isBlank()) {

            _state.value = current.copy(
                subtitleError =
                    "ابتدا کلید SubDL را وارد کنید"
            )

            return
        }

        if (
            item.normalizedType == "tv" &&
            current.selectedEpisode == null
        ) {

            _state.value = current.copy(
                subtitleError =
                    "ابتدا فصل و قسمت موردنظر را انتخاب کنید"
            )

            return
        }

        viewModelScope.launch {

            _state.value = _state.value.copy(
                subtitleLoading = true,
                subtitles = emptyList(),
                subtitleError = null
            )

            try {

                val title = customTitle
                    .trim()
                    .ifBlank {
                        item.displayTitle
                    }

                val episode =
                    current.selectedEpisode

                // Prefer IMDb ID when available.
                val imdbId = if (
                    item.normalizedType == "movie"
                ) {
                    current.movie?.imdbId
                } else {
                    current.tv?.imdbId
                }

                // Prefer the year from the detailed TMDB response.
                val year = if (
                    item.normalizedType == "movie"
                ) {
                    current.movie?.year
                        ?.toIntOrNull()
                        ?: item.year.toIntOrNull()
                } else {
                    current.tv?.year
                        ?.toIntOrNull()
                        ?: item.year.toIntOrNull()
                }

                val response =
                    SubtitleApi.service.search(

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

                val results =
                    response.subtitles.distinctBy {
                        it.url
                    }

                _state.value = _state.value.copy(
                    subtitleLoading = false,
                    subtitles = results,
                    subtitleError = if (
                        results.isEmpty()
                    ) {
                        "زیرنویسی پیدا نشد. نام فیلم یا نسخه انتشار را تغییر دهید."
                    } else {
                        null
                    }
                )

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                _state.value = _state.value.copy(
                    subtitleLoading = false,
                    subtitles = emptyList(),
                    subtitleError = e.message
                        ?: "خطا در جست‌وجوی زیرنویس"
                )
            }
        }
    }

    // --------------------------------------------------
    // Clear subtitle results
    // --------------------------------------------------

    fun clearSubtitles() {

        _state.value = _state.value.copy(
            subtitles = emptyList(),
            subtitleError = null
        )
    }
}
