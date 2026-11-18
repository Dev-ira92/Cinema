package com.cinema.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinema.data.api.TmdbApi
import com.cinema.data.api.TmdbPerson
import com.cinema.data.api.toMediaItem
import com.cinema.data.model.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class PersonUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val person: TmdbPerson? = null,
    val titles: List<MediaItem> = emptyList()
)

class PersonViewModel : ViewModel() {

    private val _state = MutableStateFlow(PersonUiState())
    val state: StateFlow<PersonUiState> = _state.asStateFlow()

    private var loadedId: Int? = null

    fun load(personId: Int) {
        if (personId <= 0 || loadedId == personId) return
        loadedId = personId

        _state.value = PersonUiState(loading = true)

        viewModelScope.launch {
            try {
                val api = TmdbApi.service
                val person = api.person(personId, "en-US")
                val credits = api.personCredits(personId, "en-US")

                val titles = (credits.cast.orEmpty() + credits.crew.orEmpty())
                    .filter { it.mediaType == "movie" || it.mediaType == "tv" }
                    .distinctBy { "${it.mediaType}:${it.id}" }
                    .sortedByDescending { it.popularity ?: 0.0 }
                    .mapNotNull { it.toMediaItem() }

                _state.update { it.copy(loading = false, person = person, titles = titles) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadedId = null
                _state.update { it.copy(loading = false, error = e.toMessage()) }
            }
        }
    }

    fun retry(personId: Int) {
        loadedId = null
        load(personId)
    }

    private fun Throwable.toMessage(): String = when (this) {
        is HttpException -> "Request failed (HTTP ${code()})."
        is IOException -> "Network error. TMDB is blocked in some countries; a VPN may be required."
        else -> "Something went wrong."
    }
}
