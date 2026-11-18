package com.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cinema.data.AppPrefs
import com.cinema.data.api.OmdbDetails
import com.cinema.data.api.VidApiPlayer
import com.cinema.data.api.omdbClean
import com.cinema.data.model.MediaItem
import com.cinema.ui.theme.CinemaBackground
import com.cinema.ui.theme.CinemaCard
import com.cinema.ui.theme.CinemaHint
import com.cinema.ui.theme.CinemaMuted
import com.cinema.ui.theme.CinemaPrimary
import com.cinema.ui.theme.CinemaRed
import com.cinema.ui.theme.CinemaText
import com.cinema.viewmodel.DetailViewModel

@Composable
fun DetailScreen(
    item: MediaItem,
    onBack: () -> Unit,
    onPlay: (url: String, title: String) -> Unit,
    vm: DetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    var season by rememberSaveable { mutableStateOf(1) }
    var episode by rememberSaveable { mutableStateOf(1) }
    var subtitleLang by rememberSaveable { mutableStateOf(AppPrefs.subtitleLang(context)) }
    var playError by remember { mutableStateOf<String?>(null) }

    var apiKey by remember { mutableStateOf(AppPrefs.omdbKey(context)) }
    var editingKey by remember { mutableStateOf(false) }

    val imdbId = item.imdbId
    val details = state.details

    LaunchedEffect(imdbId, apiKey) { vm.load(imdbId, apiKey) }

    LaunchedEffect(imdbId, season, apiKey, item.isTv) {
        if (item.isTv && imdbId != null && apiKey.isNotBlank()) {
            vm.loadSeason(imdbId, season, apiKey)
        }
    }

    val maxSeason = details?.totalSeasons.omdbClean()?.toIntOrNull()?.coerceIn(1, 99) ?: 99

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = CinemaText
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CinemaCard)
            ) {
                if (item.posterUrl != null) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.title,
                    color = CinemaText,
                    style = MaterialTheme.typography.headlineSmall
                )

                val rating = details?.imdbRating.omdbClean()
                val metaLine = listOfNotNull(
                    item.year.ifBlank { null },
                    rating?.let { "★ $it" },
                    details?.runtime.omdbClean(),
                    details?.rated.omdbClean()
                ).joinToString("  ·  ")
                    .ifBlank { item.subtitleLine }

                if (metaLine.isNotEmpty()) {
                    Text(text = metaLine, color = CinemaMuted, style = MaterialTheme.typography.bodyMedium)
                }

                val genreLine = details?.genre.omdbClean() ?: item.genre.ifBlank { null }
                if (genreLine != null) {
                    Text(text = genreLine, color = CinemaMuted, style = MaterialTheme.typography.bodySmall)
                }

                Text(
                    text = if (item.isTv) "TV series" else "Movie",
                    color = CinemaHint,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (item.isTv) {
            Stepper(
                label = "Season",
                value = season,
                min = 1,
                max = maxSeason,
                onChange = {
                    season = it
                    episode = 1
                }
            )
            Stepper(
                label = "Episode",
                value = episode,
                min = 1,
                max = 999,
                onChange = { episode = it }
            )
        }

        Text(text = "Subtitles", color = CinemaText, style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppPrefs.subtitleLanguages.forEach { language ->
                FilterChip(
                    selected = subtitleLang == language.code,
                    onClick = {
                        subtitleLang = language.code
                        AppPrefs.setSubtitleLang(context, language.code)
                    },
                    label = { Text(language.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = CinemaMuted,
                        selectedContainerColor = CinemaPrimary,
                        selectedLabelColor = CinemaText
                    )
                )
            }
        }

        Button(
            onClick = {
                val id = item.embedId
                val lang = subtitleLang.ifBlank { null }

                val url = when {
                    id == null -> null
                    item.isTv -> VidApiPlayer.episodeUrl(id, season, episode, item.title, lang)
                    else -> VidApiPlayer.movieUrl(id, item.title, lang)
                }

                if (url == null) {
                    playError = "This title has no valid ID."
                } else {
                    playError = null
                    val label = if (item.isTv) "${item.title} · S${season}E${episode}" else item.title
                    onPlay(url, label)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Play")
        }

        playError?.let { Text(text = it, color = CinemaRed) }

        // ---- Episodes (TV) ----
        if (item.isTv && apiKey.isNotBlank()) {
            Text(text = "Episodes", color = CinemaText, style = MaterialTheme.typography.titleMedium)

            when {
                state.episodesLoading -> CircularProgressIndicator(color = CinemaPrimary)
                state.episodesError != null ->
                    Text(text = state.episodesError.orEmpty(), color = CinemaMuted)
                state.episodes.isEmpty() ->
                    Text(text = "No episode list available.", color = CinemaMuted)
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.episodes.forEach { ep ->
                        val number = ep.episode?.toIntOrNull()
                        val selected = number != null && number == episode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) CinemaPrimary else CinemaCard,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(enabled = number != null) { number?.let { episode = it } }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = "E${ep.episode ?: "?"}", color = CinemaText)
                            Text(
                                text = ep.title.omdbClean() ?: "Episode",
                                color = CinemaText,
                                modifier = Modifier.weight(1f)
                            )
                            ep.imdbRating.omdbClean()?.let { Text(text = "★ $it", color = CinemaMuted) }
                        }
                    }
                }
            }
        }

        // ---- Info from OMDb ----
        when {
            imdbId == null ->
                Text(
                    text = "Details need an IMDb ID, which this title does not have.",
                    color = CinemaMuted
                )

            apiKey.isBlank() || editingKey ->
                ApiKeyCard(
                    message = if (apiKey.isBlank()) {
                        "Add a free OMDb key to see the plot, cast, director and episode lists."
                    } else {
                        "Enter a new OMDb key."
                    },
                    onSave = { key ->
                        AppPrefs.setOmdbKey(context, key)
                        apiKey = key
                        editingKey = false
                    }
                )

            state.loading -> CircularProgressIndicator(color = CinemaPrimary)

            state.error != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = state.error.orEmpty(), color = CinemaRed)
                Row {
                    TextButton(onClick = { vm.retry(imdbId, apiKey) }) { Text("Retry") }
                    TextButton(onClick = { editingKey = true }) { Text("Change key") }
                }
            }

            details != null -> {
                InfoSection(
                    details = details,
                    translating = state.translating,
                    translation = state.translation,
                    showTranslation = state.showTranslation,
                    translateError = state.translateError,
                    onToggleTranslation = vm::toggleTranslation
                )
                TextButton(onClick = { editingKey = true }) { Text("Change OMDb key") }
            }
        }
    }
}

@Composable
private fun InfoSection(
    details: OmdbDetails,
    translating: Boolean,
    translation: String?,
    showTranslation: Boolean,
    translateError: String?,
    onToggleTranslation: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val plot = details.plot.omdbClean()
        if (plot != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Plot", color = CinemaText, style = MaterialTheme.typography.titleMedium)

                if (showTranslation && translation != null) {
                    Text(
                        text = translation,
                        color = CinemaMuted,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = TextDirection.Rtl
                        ),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(text = plot, color = CinemaMuted, style = MaterialTheme.typography.bodyMedium)
                }

                TextButton(onClick = onToggleTranslation, enabled = !translating) {
                    Text(
                        when {
                            translating -> "Translating…"
                            showTranslation -> "Show original"
                            translation != null -> "Show Persian translation"
                            else -> "Translate to Persian"
                        }
                    )
                }

                translateError?.let { Text(text = it, color = CinemaRed, style = MaterialTheme.typography.bodySmall) }
            }
        }

        InfoRow("Director", details.director)
        InfoRow("Writer", details.writer)
        InfoRow("Cast", details.actors)
        InfoRow("Language", details.language)
        InfoRow("Country", details.country)
        InfoRow("Released", details.released)
        InfoRow("Awards", details.awards)
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    val text = value.omdbClean() ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, color = CinemaHint, style = MaterialTheme.typography.labelMedium)
        Text(text = text, color = CinemaText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ApiKeyCard(message: String, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CinemaCard, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = message, color = CinemaText, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Free key: omdbapi.com/apikey.aspx (you activate it with a link sent by email).",
            color = CinemaHint,
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it.trim() },
            singleLine = true,
            placeholder = { Text("OMDb API key", color = CinemaHint) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CinemaText,
                unfocusedTextColor = CinemaText,
                focusedBorderColor = CinemaPrimary,
                unfocusedBorderColor = CinemaHint,
                cursorColor = CinemaPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { if (text.isNotBlank()) onSave(text) },
            colors = ButtonDefaults.buttonColors(containerColor = CinemaPrimary)
        ) {
            Text("Save key")
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CinemaCard, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = CinemaText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = { if (value > min) onChange(value - 1) }) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $label", tint = CinemaText)
        }

        Text(
            text = value.toString(),
            color = CinemaText,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp)
        )

        IconButton(onClick = { if (value < max) onChange(value + 1) }) {
            Icon(Icons.Default.Add, contentDescription = "Increase $label", tint = CinemaText)
        }
    }
}
