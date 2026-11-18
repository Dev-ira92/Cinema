package com.cinema.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinema.data.api.ImdbSuggestApi
import com.cinema.data.api.PersonItem
import com.cinema.data.api.TmdbApi
import com.cinema.data.api.TmdbConfig
import com.cinema.data.api.toMediaItem as tmdbToMediaItem
import com.cinema.data.api.toPersonItem
import com.cinema.data.api.VidApiClient
import com.cinema.data.model.MediaItem
import com.cinema.data.model.toMediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

enum class HomeTab { MOVIES, TV }

data class GenreChip(val label: String, val keys: List<String>)

/** Genre filters; matched (case-insensitively) against the catalog's "genre" text. */
object Genres {
    val all = listOf(
        GenreChip("Action", listOf("action")),
        GenreChip("Adventure", listOf("adventure")),
        GenreChip("Animation", listOf("animation")),
        GenreChip("Comedy", listOf("comedy")),
        GenreChip("Crime", listOf("crime")),
        GenreChip("Documentary", listOf("documentary")),
        GenreChip("Drama", listOf("drama")),
        GenreChip("Family", listOf("family")),
        GenreChip("Fantasy", listOf("fantasy")),
        GenreChip("Horror", listOf("horror")),
        GenreChip("Mystery", listOf("mystery")),
        GenreChip("Romance", listOf("romance")),
        GenreChip("Sci-Fi", listOf("sci-fi", "science fiction")),
        GenreChip("Thriller", listOf("thriller")),
        GenreChip("War", listOf("war"))
    )
}

data class FeedState(
    val items: List<MediaItem> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 0,
    val loading: Boolean = false,
    val error: String? = null
) {
    /** totalPages == 0 means "unknown" (never treated as the end). */
    val endReached: Boolean
        get() = totalPages > 0 && page >= totalPages
}

data class HomeUiState(
    val tab: HomeTab = HomeTab.MOVIES,
    val genre: String? = null,
    val movies: FeedState = FeedState(),
    val tv: FeedState = FeedState(),
    val query: String = "",
    val searching: Boolean = false,
    val results: List<MediaItem> = emptyList(),
    val people: List<PersonItem> = emptyList(),
    val searchError: String? = null
)

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadNext(HomeTab.MOVIES)
    }

    fun selectTab(tab: HomeTab) {
        _state.update { it.copy(tab = tab) }
        loadNext(tab)
    }

    fun selectGenre(label: String?) {
        _state.update { it.copy(genre = label) }
    }

    fun loadNext(tab: HomeTab) {
        val feed = feedOf(tab)
        if (feed.loading || feed.endReached) return

        updateFeed(tab) { it.copy(loading = true, error = null) }

        viewModelScope.launch {
            val nextPage = feed.page + 1
            try {
                val response = when (tab) {
                    HomeTab.MOVIES -> VidApiClient.service.latestMovies(nextPage)
                    HomeTab.TV -> VidApiClient.service.latestTvShows(nextPage)
                }

                val rawItems = response.items.orEmpty()
                val mapped = rawItems.mapNotNull { it.toMediaItem(isTv = tab == HomeTab.TV) }

                updateFeed(tab) { current ->
                    current.copy(
                        items = (current.items + mapped).distinctBy { it.key },
                        page = nextPage,
                        // An empty page means there is nothing more to load.
                        totalPages = if (rawItems.isEmpty()) nextPage else response.totalPages,
                        loading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateFeed(tab) { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun onQueryChanged(value: String) {
        searchJob?.cancel()

        _state.update {
            it.copy(
                query = value,
                searching = false,
                results = emptyList(),
                people = emptyList(),
                searchError = null
            )
        }

        val query = value.trim()
        if (query.length < 2) return

        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            delay(400) // debounce typing

            try {
                val (people, results) = search(query)
                _state.update {
                    it.copy(
                        searching = false,
                        results = results,
                        people = people,
                        searchError = if (results.isEmpty() && people.isEmpty()) {
                            "No results for \"$query\"."
                        } else {
                            null
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        searching = false,
                        results = emptyList(),
                        people = emptyList(),
                        searchError = e.toUserMessage()
                    )
                }
            }
        }
    }

    fun clearSearch() = onQueryChanged("")

    /** TMDB search (titles + people) when a key is set; falls back to IMDb suggestions. */
    private suspend fun search(query: String): Pair<List<PersonItem>, List<MediaItem>> {
        if (!TmdbConfig.hasKey) {
            return emptyList<PersonItem>() to ImdbSuggestApi.search(query)
        }
        return try {
            val hits = TmdbApi.service.searchMulti(query, "en-US", 1, false).results.orEmpty()
            val people = hits.mapNotNull { it.toPersonItem() }.take(12)
            val titles = hits.mapNotNull { it.tmdbToMediaItem() }
            people to titles
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList<PersonItem>() to ImdbSuggestApi.search(query)
        }
    }

    private fun feedOf(tab: HomeTab): FeedState =
        if (tab == HomeTab.MOVIES) _state.value.movies else _state.value.tv

    private fun updateFeed(tab: HomeTab, transform: (FeedState) -> FeedState) {
        _state.update { s ->
            if (tab == HomeTab.MOVIES) s.copy(movies = transform(s.movies))
            else s.copy(tv = transform(s.tv))
        }
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is HttpException -> when (code()) {
            429 -> "Too many requests. Please try again later."
            in 500..599 -> "The server is temporarily unavailable."
            else -> "Request failed (HTTP ${code()})."
        }
        is IOException -> "Network error. Check your internet connection."
        else -> "Something went wrong."
    }
}
