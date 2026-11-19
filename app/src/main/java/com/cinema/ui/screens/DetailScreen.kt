package com.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cinema.data.AppPrefs
import com.cinema.data.api.VidApiPlayer
import com.cinema.data.api.tmdbImage
import com.cinema.data.model.MediaItem
import com.cinema.ui.theme.*
import com.cinema.viewmodel.DetailViewModel
import java.util.Locale

private data class PersonChip(
    val id      : Int,
    val name    : String,
    val subtitle: String,
    val photoUrl: String?
)

@Composable
fun DetailScreen(
    item          : MediaItem,
    onBack        : () -> Unit,
    onPlay        : (url: String, title: String) -> Unit,
    onInternalPlay: (streamUrl: String, title: String) -> Unit,
    onPersonClick : (id: Int, name: String) -> Unit,
    vm            : DetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val state   by vm.state.collectAsStateWithLifecycle()

    var season       by rememberSaveable { mutableStateOf(1) }
    var episode      by rememberSaveable { mutableStateOf(1) }
    var subtitleLang by rememberSaveable { mutableStateOf(AppPrefs.subtitleLang(context)) }
    var playError    by remember { mutableStateOf<String?>(null) }
    var apiKey       by remember { mutableStateOf(AppPrefs.tmdbKey(context)) }
    var editingKey   by remember { mutableStateOf(false) }

    val details    = state.details
    val maxSeason  = (details?.numberOfSeasons ?: 99).coerceIn(1, 99)

    LaunchedEffect(item.tmdbId, item.imdbId, item.isTv, apiKey) {
        vm.load(item.tmdbId, item.imdbId, item.isTv, apiKey)
    }

    LaunchedEffect(season, details?.id) {
        if (item.isTv && details != null) vm.loadSeason(season)
    }

    // Build vaplayer embed URL for current selection
    val embedUrl: String? = remember(item, season, episode, subtitleLang) {
        val id   = item.embedId ?: return@remember null
        val lang = subtitleLang.ifBlank { null }
        if (item.isTv) VidApiPlayer.episodeUrl(id, season, episode, item.title, lang)
        else           VidApiPlayer.movieUrl(id, item.title, lang)
    }

    val directors = remember(details) {
        if (item.isTv) {
            details?.createdBy.orEmpty().mapNotNull { c ->
                c.id?.let { PersonChip(it, c.name.orEmpty(), "Creator", tmdbImage(c.profilePath, "w185")) }
            }
        } else {
            details?.credits?.crew.orEmpty()
                .filter { it.job == "Director" }
                .distinctBy { it.id }
                .mapNotNull { c ->
                    c.id?.let { PersonChip(it, c.name.orEmpty(), "Director", tmdbImage(c.profilePath, "w185")) }
                }
        }
    }

    val cast = remember(details) {
        details?.credits?.cast.orEmpty().take(15).mapNotNull { c ->
            c.id?.let { PersonChip(it, c.name.orEmpty(), c.character.orEmpty(), tmdbImage(c.profilePath, "w185")) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Back ─────────────────────────────────────────────────────────────
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CinemaText)
        }

        // ── Poster + meta ─────────────────────────────────────────────────────
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
                        model              = item.posterUrl,
                        contentDescription = item.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(item.title, color = CinemaText, style = MaterialTheme.typography.headlineSmall)

                val year    = item.year.ifBlank {
                    (details?.releaseDate ?: details?.firstAirDate).orEmpty().take(4)
                }
                val rating  = details?.voteAverage?.takeIf { it > 0 }
                    ?.let { String.format(Locale.US, "%.1f", it) }
                    ?: item.rating.ifBlank { null }
                val runtime = (details?.runtime ?: details?.episodeRunTime?.firstOrNull())
                    ?.takeIf { it > 0 }?.let { "$it min" }

                val metaLine = listOfNotNull(
                    year.ifBlank { null },
                    rating?.let { "★ $it" },
                    runtime
                ).joinToString("  ·  ")

                if (metaLine.isNotEmpty())
                    Text(metaLine, color = CinemaMuted, style = MaterialTheme.typography.bodyMedium)

                val genreLine = details?.genres.orEmpty()
                    .mapNotNull { it.name }.joinToString(", ").ifBlank { null }
                    ?: item.genre.ifBlank { null }
                if (genreLine != null)
                    Text(genreLine, color = CinemaMuted, style = MaterialTheme.typography.bodySmall)

                Text(if (item.isTv) "TV series" else "Movie",
                    color = CinemaHint, style = MaterialTheme.typography.labelMedium)
            }
        }

        // ── Season / Episode steppers (TV) ────────────────────────────────────
        if (item.isTv) {
            Stepper("Season",  season,  1, maxSeason) { season  = it; episode = 1 }
            Stepper("Episode", episode, 1, 999       ) { episode = it }
        }

        // ── Subtitle language ─────────────────────────────────────────────────
        Text("Subtitles", color = CinemaText, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier              = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppPrefs.subtitleLanguages.forEach { language ->
                FilterChip(
                    selected = subtitleLang == language.code,
                    onClick  = {
                        subtitleLang = language.code
                        AppPrefs.setSubtitleLang(context, language.code)
                        vm.clearExtractedStream()
                    },
                    label  = { Text(language.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor              = CinemaMuted,
                        selectedContainerColor  = CinemaPrimary,
                        selectedLabelColor      = CinemaText
                    )
                )
            }
        }

        // ── Play via WebView (vaplayer) ────────────────────────────────────────
        Button(
            onClick = {
                if (embedUrl == null) {
                    playError = "This title has no valid ID."
                } else {
                    playError = null
                    val label = if (item.isTv) "${item.title} · S${season}E${episode}" else item.title
                    onPlay(embedUrl, label)
                }
            },
            colors   = ButtonDefaults.buttonColors(containerColor = CinemaPrimary),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Play (built-in browser player)")
        }

        playError?.let { Text(it, color = CinemaRed) }

        // ── Internal player section ───────────────────────────────────────────
        HorizontalDivider(color = CinemaCard)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CinemaCard, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Internal Player", color = CinemaText, style = MaterialTheme.typography.titleMedium)
            Text(
                "Extracts the direct stream URL from the player site and plays it with ExoPlayer.",
                color = CinemaHint, style = MaterialTheme.typography.bodySmall
            )

            if (!state.streamLoading) {
                Button(
                    onClick  = {
                        if (embedUrl != null) vm.fetchDirectStream(context, embedUrl)
                        else playError = "This title has no valid ID."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(
                        if (state.extractedStream == null) "Extract & Play (ExoPlayer)"
                        else "Re-extract Stream"
                    )
                }
            } else {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = CinemaPrimary
                    )
                    Text(
                        state.streamStatus,
                        color = CinemaMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Error
            state.streamError?.let {
                Text(it, color = CinemaRed, style = MaterialTheme.typography.bodySmall)
            }

            // Success — show play button
            state.extractedStream?.let { stream ->
                Text(
                    "✓  ${state.streamStatus}",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium
                )
                Button(
                    onClick  = {
                        val label = if (item.isTv) "${item.title} · S${season}E${episode}"
                                    else item.title
                        onInternalPlay(stream.url, label)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("▶  Play with ExoPlayer (${stream.quality})", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }

        // ── Episodes list (TV) ────────────────────────────────────────────────
        if (item.isTv && apiKey.isNotBlank()) {
            Text("Episodes", color = CinemaText, style = MaterialTheme.typography.titleMedium)
            when {
                state.episodesLoading ->
                    CircularProgressIndicator(color = CinemaPrimary)
                state.episodesError != null ->
                    Text(state.episodesError.orEmpty(), color = CinemaMuted)
                state.episodes.isEmpty() ->
                    Text("No episode list available.", color = CinemaMuted)
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.episodes.forEach { ep ->
                        val number   = ep.episodeNumber
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
                            Text("E${number ?: "?"}", color = CinemaText)
                            Text(
                                ep.name?.takeIf { it.isNotBlank() } ?: "Episode",
                                color    = CinemaText,
                                modifier = Modifier.weight(1f)
                            )
                            ep.voteAverage?.takeIf { it > 0 }?.let {
                                Text("★ " + String.format(Locale.US, "%.1f", it), color = CinemaMuted)
                            }
                        }
                    }
                }
            }
        }

        // ── TMDB info ─────────────────────────────────────────────────────────
        when {
            apiKey.isBlank() || editingKey -> KeyCard(
                message = if (apiKey.isBlank())
                    "Add a free TMDB key to see the plot, cast, director and episodes."
                else "Enter a new TMDB key.",
                onSave  = { key ->
                    AppPrefs.setTmdbKey(context, key)
                    apiKey     = key
                    editingKey = false
                }
            )

            state.loading  -> CircularProgressIndicator(color = CinemaPrimary)

            state.error != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.error.orEmpty(), color = CinemaRed)
                Row {
                    TextButton(onClick = { vm.retry(item.tmdbId, item.imdbId, item.isTv, apiKey) }) {
                        Text("Retry")
                    }
                    TextButton(onClick = { editingKey = true }) { Text("Change key") }
                }
            }

            details != null -> {
                details.tagline?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = CinemaMuted, style = MaterialTheme.typography.bodyMedium)
                }

                val overview = state.overview
                if (overview != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Plot", color = CinemaText, style = MaterialTheme.typography.titleMedium)

                        val showPersian = state.showTranslation && state.translation != null
                        val shown       = if (showPersian) state.translation.orEmpty() else overview
                        val rtl         = showPersian || !state.overviewIsEnglish

                        Text(
                            text      = shown,
                            color     = CinemaMuted,
                            style     = MaterialTheme.typography.bodyMedium.copy(
                                textDirection = if (rtl) TextDirection.Content else TextDirection.Ltr
                            ),
                            textAlign = TextAlign.Start,
                            modifier  = Modifier.fillMaxWidth()
                        )

                        if (state.overviewIsEnglish) {
                            TextButton(
                                onClick = vm::toggleTranslation,
                                enabled = !state.translating
                            ) {
                                Text(when {
                                    state.translating   -> "Translating…"
                                    showPersian         -> "Show original"
                                    state.translation != null -> "Show Persian translation"
                                    else                -> "Translate to Persian"
                                })
                            }
                        }

                        state.translateError?.let {
                            Text(it, color = CinemaRed, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                PeopleRow(
                    title   = if (item.isTv) "Created by" else "Director",
                    people  = directors,
                    onClick = onPersonClick
                )
                PeopleRow(title = "Cast", people = cast, onClick = onPersonClick)

                TextButton(onClick = { editingKey = true }) { Text("Change TMDB key") }
            }
        }
    }
}

// ── Small composables ─────────────────────────────────────────────────────────

@Composable
private fun PeopleRow(
    title  : String,
    people : List<PersonChip>,
    onClick: (id: Int, name: String) -> Unit
) {
    if (people.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = CinemaText, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier              = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            people.forEach { person ->
                PersonAvatar(
                    photoUrl = person.photoUrl,
                    name     = person.name,
                    subtitle = person.subtitle,
                    onClick  = { onClick(person.id, person.name) }
                )
            }
        }
    }
}

@Composable
private fun KeyCard(message: String, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CinemaCard, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(message, color = CinemaText, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Free key: themoviedb.org → Settings → API. TMDB may need a VPN in some countries.",
            color = CinemaHint, style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value         = text,
            onValueChange = { text = it.trim() },
            singleLine    = true,
            placeholder   = { Text("TMDB key", color = CinemaHint) },
            colors        = OutlinedTextFieldDefaults.colors(
                focusedTextColor    = CinemaText,
                unfocusedTextColor  = CinemaText,
                focusedBorderColor  = CinemaPrimary,
                unfocusedBorderColor= CinemaHint,
                cursorColor         = CinemaPrimary
            ),
            modifier      = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { if (text.isNotBlank()) onSave(text) },
            colors  = ButtonDefaults.buttonColors(containerColor = CinemaPrimary)
        ) { Text("Save key") }
    }
}

@Composable
private fun Stepper(
    label   : String,
    value   : Int,
    min     : Int,
    max     : Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CinemaCard, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = CinemaText,
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { if (value > min) onChange(value - 1) }) {
            Icon(Icons.Default.Remove, "Decrease $label", tint = CinemaText)
        }
        Text(value.toString(), color = CinemaText,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp))
        IconButton(onClick = { if (value < max) onChange(value + 1) }) {
            Icon(Icons.Default.Add, "Increase $label", tint = CinemaText)
        }
    }
}
