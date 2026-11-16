package com.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cinema.data.model.MediaItem
import com.cinema.data.model.SubdlItem
import com.cinema.data.model.TvEpisode
import com.cinema.ui.theme.*
import com.cinema.viewmodel.DetailViewModel

private enum class SubtitleFilter { ALL, PERSIAN, ENGLISH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    item   : MediaItem,
    onPlay : (String, String, String?) -> Unit,
    onBack : () -> Unit = {},
    vm     : DetailViewModel = viewModel()
) {
    val context          = LocalContext.current
    val state            by vm.state.collectAsState()
    var videoUrl         by remember(item.id) { mutableStateOf("") }
    var customSubTitle   by remember(item.id) { mutableStateOf("") }
    var selectedSubtitle by remember(item.id) { mutableStateOf<SubdlItem?>(null) }
    var seasonMenuExpanded by remember { mutableStateOf(false) }
    var subtitleFilter   by remember(item.id) { mutableStateOf(SubtitleFilter.ALL) }

    LaunchedEffect(item.id, item.normalizedType) { vm.load(item) }

    val displayTitle   = item.displayTitle
    val detailOverview = state.movie?.overview ?: state.tv?.overview ?: item.overview
    val seasons        = state.tv?.seasons.orEmpty().filter { it.seasonNumber >= 0 }
    val episodes       = state.season?.episodes.orEmpty()

    val visibleSubtitles = remember(state.subtitles, subtitleFilter) {
        val filtered = state.subtitles.filter {
            when (subtitleFilter) {
                SubtitleFilter.ALL     -> true
                SubtitleFilter.PERSIAN -> isPersianSubtitle(it.language)
                SubtitleFilter.ENGLISH -> isEnglishSubtitle(it.language)
            }
        }
        if (subtitleFilter == SubtitleFilter.ALL) {
            filtered.sortedBy {
                when { isPersianSubtitle(it.language) -> 0
                       isEnglishSubtitle(it.language) -> 1
                       else                           -> 2 }
            }
        } else filtered
    }

    LazyColumn(
        modifier        = Modifier.fillMaxSize().background(CinemaBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding  = PaddingValues(bottom = 28.dp)
    ) {

        // ── header ───────────────────────────────────────────────────────────
        item {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Text("TITLE DETAILS", color = CinemaMuted, style = MaterialTheme.typography.labelLarge)
            }
        }

        // ── backdrop ─────────────────────────────────────────────────────────
        item {
            AsyncImage(
                model              = item.backdropUrl ?: item.posterUrl,
                contentDescription = displayTitle,
                modifier           = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(16.dp)),
                contentScale       = ContentScale.Crop
            )
        }

        // ── title / rating ───────────────────────────────────────────────────
        item {
            Text(displayTitle, color = CinemaText, style = MaterialTheme.typography.headlineSmall)
            Text(
                text  = buildString {
                    append(item.normalizedType.uppercase())
                    if (item.year.isNotBlank()) append("  •  ${item.year}")
                    append("  •  ★ ${item.ratingText}")
                },
                color = CinemaMuted, style = MaterialTheme.typography.bodyMedium
            )
        }

        if (state.loading) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CinemaPrimary) }
        }
        if (!state.error.isNullOrBlank()) {
            item { Text(state.error.orEmpty(), color = CinemaRed) }
        }

        // ── overview ─────────────────────────────────────────────────────────
        item {
            Text("Overview", color = CinemaText, style = MaterialTheme.typography.titleMedium)
            Text(
                detailOverview.ifBlank { "No overview available." },
                color = CinemaMuted, style = MaterialTheme.typography.bodyMedium
            )
        }

        // ── movie info ───────────────────────────────────────────────────────
        if (item.normalizedType == "movie") {
            item {
                val movie = state.movie
                if (movie != null) {
                    Text("Movie information", color = CinemaText, style = MaterialTheme.typography.titleMedium)
                    if (movie.genreText.isNotBlank())
                        Text(movie.genreText, color = CinemaMuted)
                    if (movie.runtimeText.isNotBlank())
                        Text("Runtime: ${movie.runtimeText}", color = CinemaMuted)
                }
            }
        }

        // ── TV: season picker ─────────────────────────────────────────────────
        if (item.normalizedType != "movie") {
            item {
                Text("Season", color = CinemaText, style = MaterialTheme.typography.titleMedium)
                Box {
                    OutlinedButton(
                        onClick  = { seasonMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Season ${state.selectedSeason}", color = CinemaText) }
                    DropdownMenu(
                        expanded          = seasonMenuExpanded,
                        onDismissRequest  = { seasonMenuExpanded = false }
                    ) {
                        seasons.forEach { season ->
                            DropdownMenuItem(
                                text    = { Text(season.name.ifBlank { "Season ${season.seasonNumber}" }) },
                                onClick = {
                                    seasonMenuExpanded = false
                                    selectedSubtitle   = null
                                    vm.selectSeason(season.seasonNumber)
                                }
                            )
                        }
                    }
                }
            }

            if (episodes.isNotEmpty()) {
                item { Text("Episodes", color = CinemaText, style = MaterialTheme.typography.titleMedium) }
                items(items = episodes, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode  = episode,
                        selected = state.selectedEpisode?.id == episode.id,
                        onClick  = { selectedSubtitle = null; vm.selectEpisode(episode) }
                    )
                }
            }
        }

        // ── AUTO STREAM FINDER ────────────────────────────────────────────────
        item {
            HorizontalDivider(color = CinemaBorder)
            Spacer(Modifier.height(4.dp))
            Text("Stream Finder", color = CinemaText, style = MaterialTheme.typography.titleMedium)
            Text(
                "Automatically finds a playable stream link.",
                color = CinemaMuted, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            // find button
            if (!state.streamLoading) {
                Button(
                    onClick  = { vm.fetchStreams(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.streams.isEmpty()) "Find Stream" else "Search Again")
                }
            }

            // progress
            if (state.streamLoading) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CinemaPrimary)
                    Text(state.streamStatus, color = CinemaMuted, style = MaterialTheme.typography.bodySmall)
                }
            }

            // error
            if (!state.streamError.isNullOrBlank()) {
                Text(state.streamError.orEmpty(), color = CinemaRed, style = MaterialTheme.typography.bodySmall)
            }

            // results — tap to fill the URL field
            state.streams.forEach { source ->
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick  = { videoUrl = source.url },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (videoUrl == source.url) CinemaPrimary.copy(alpha = 0.12f)
                                         else CinemaCard
                    )
                ) {
                    Text(
                        text  = "▶  ${source.server}  •  ${source.quality}",
                        color = if (videoUrl == source.url) CinemaPrimary else CinemaText
                    )
                }
            }

            if (state.streams.isNotEmpty() && !state.streamLoading) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = "${state.streamStatus} — tap a result to load it",
                    color    = CinemaMuted,
                    style    = MaterialTheme.typography.labelSmall
                )
            }
        }

        // ── manual URL ────────────────────────────────────────────────────────
        item {
            HorizontalDivider(color = CinemaBorder)
            Spacer(Modifier.height(4.dp))
            Text("Video URL", color = CinemaText, style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value         = videoUrl,
                onValueChange = { videoUrl = it.trim() },
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("Video URL") },
                placeholder   = { Text("https://...") },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedTextColor        = CinemaText,
                    unfocusedTextColor      = CinemaText,
                    focusedBorderColor      = CinemaPrimary,
                    unfocusedBorderColor    = CinemaBorder,
                    focusedContainerColor   = CinemaSurface,
                    unfocusedContainerColor = CinemaSurface
                )
            )

            Spacer(Modifier.height(6.dp))

            Button(
                onClick  = { onPlay(videoUrl, displayTitle, selectedSubtitle?.url) },
                modifier = Modifier.fillMaxWidth(),
                enabled  = isHttpUrl(videoUrl)
            ) { Text("Play") }
        }

        // ── subtitles ────────────────────────────────────────────────────────
        item {
            HorizontalDivider(color = CinemaBorder)
            Spacer(Modifier.height(4.dp))
            Text("Subtitles", color = CinemaText, style = MaterialTheme.typography.titleMedium)
            Text("Search for Persian and English subtitles.", color = CinemaMuted,
                style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value         = customSubTitle,
                onValueChange = { customSubTitle = it },
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("Title or release name") },
                placeholder   = { Text(displayTitle) },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedTextColor        = CinemaText,
                    unfocusedTextColor      = CinemaText,
                    focusedBorderColor      = CinemaPrimary,
                    unfocusedBorderColor    = CinemaBorder,
                    focusedContainerColor   = CinemaSurface,
                    unfocusedContainerColor = CinemaSurface
                )
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick  = { selectedSubtitle = null; subtitleFilter = SubtitleFilter.ALL
                             vm.searchSubtitles(context, customSubTitle) },
                modifier = Modifier.fillMaxWidth(),
                enabled  = !state.subtitleLoading
            ) {
                if (state.subtitleLoading)
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Search subtitles")
            }
        }

        if (state.subtitles.isNotEmpty()) {
            item {
                Text("${state.subtitles.size} subtitle results", color = CinemaText,
                    style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = subtitleFilter == SubtitleFilter.ALL,
                        onClick = { subtitleFilter = SubtitleFilter.ALL }, label = { Text("All") })
                    FilterChip(selected = subtitleFilter == SubtitleFilter.PERSIAN,
                        onClick = { subtitleFilter = SubtitleFilter.PERSIAN }, label = { Text("Persian") })
                    FilterChip(selected = subtitleFilter == SubtitleFilter.ENGLISH,
                        onClick = { subtitleFilter = SubtitleFilter.ENGLISH }, label = { Text("English") })
                }
                Text("${visibleSubtitles.size} shown", color = CinemaMuted,
                    style = MaterialTheme.typography.bodySmall)
            }

            if (visibleSubtitles.isEmpty()) {
                item { Text("No results for this language.", color = CinemaMuted) }
            }

            items(items = visibleSubtitles, key = { it.url }) { subtitle ->
                SubtitleRow(
                    subtitle = subtitle,
                    selected = selectedSubtitle?.url == subtitle.url,
                    onClick  = { selectedSubtitle = subtitle }
                )
            }
        }

        if (!state.subtitleError.isNullOrBlank()) {
            item { Text(state.subtitleError.orEmpty(), color = CinemaRed,
                style = MaterialTheme.typography.bodySmall) }
        }

        if (selectedSubtitle != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CinemaCard)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Selected subtitle", color = CinemaText)
                        Text(
                            selectedSubtitle?.releaseName?.ifBlank { selectedSubtitle?.fullName.orEmpty() }.orEmpty(),
                            color = CinemaMuted
                        )
                        Text(selectedSubtitle?.language.orEmpty(), color = CinemaMuted)
                        TextButton(onClick = { selectedSubtitle = null }) { Text("Clear selection") }
                        Button(
                            onClick  = { if (isHttpUrl(videoUrl)) onPlay(videoUrl, displayTitle, selectedSubtitle?.url) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled  = isHttpUrl(videoUrl)
                        ) { Text("Play with subtitle") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: TvEpisode, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors   = CardDefaults.cardColors(
            containerColor = if (selected) CinemaPrimary.copy(alpha = 0.18f) else CinemaCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${episode.code}  •  ${episode.name}", color = CinemaText,
                style = MaterialTheme.typography.titleSmall)
            if (episode.overview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(episode.overview, color = CinemaMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SubtitleRow(subtitle: SubdlItem, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors   = CardDefaults.cardColors(
            containerColor = if (selected) CinemaPrimary.copy(alpha = 0.18f) else CinemaCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                subtitle.releaseName.ifBlank { subtitle.fullName.ifBlank { subtitle.fileName } },
                color = CinemaText, style = MaterialTheme.typography.titleSmall
            )
            Text("Language: ${subtitle.language}", color = CinemaMuted)
            if (subtitle.fileName.isNotBlank())
                Text("File: ${subtitle.fileName}", color = CinemaMuted,
                    style = MaterialTheme.typography.bodySmall)
            Text(if (selected) "Selected ✓" else "Select subtitle", color = CinemaPrimary)
        }
    }
}

private fun isPersianSubtitle(language: String): Boolean {
    val v = language.trim().lowercase()
    return v == "fa" || v == "fas" || v == "per" || v.contains("persian") || v.contains("farsi")
}

private fun isEnglishSubtitle(language: String): Boolean {
    val v = language.trim().lowercase()
    return v == "en" || v == "eng" || v.contains("english")
}

private fun isHttpUrl(value: String): Boolean =
    value.startsWith("https://", ignoreCase = true) ||
    value.startsWith("http://",  ignoreCase = true)
