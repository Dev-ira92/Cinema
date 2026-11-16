
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

private enum class SubtitleFilter {
    ALL,
    PERSIAN,
    ENGLISH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    item: MediaItem,
    onPlay: (String, String, String?) -> Unit,
    onBack: () -> Unit = {},
    vm: DetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    var videoUrl by remember(item.id) {
        mutableStateOf("")
    }

    var customSubtitleTitle by remember(item.id) {
        mutableStateOf("")
    }

    var selectedSubtitle by remember(item.id) {
        mutableStateOf<SubdlItem?>(null)
    }

    var seasonMenuExpanded by remember {
        mutableStateOf(false)
    }

    var subtitleFilter by remember(item.id) {
        mutableStateOf(SubtitleFilter.ALL)
    }

    LaunchedEffect(item.id, item.normalizedType) {
        vm.load(item)
    }

    val displayTitle = item.displayTitle

    val detailOverview =
        state.movie?.overview
            ?: state.tv?.overview
            ?: item.overview

    val seasons = state.tv?.seasons.orEmpty()
        .filter { it.seasonNumber >= 0 }

    val episodes = state.season?.episodes.orEmpty()

    val visibleSubtitles = remember(
        state.subtitles,
        subtitleFilter
    ) {
        val filtered = state.subtitles.filter { subtitle ->
            when (subtitleFilter) {
                SubtitleFilter.ALL -> true
                SubtitleFilter.PERSIAN ->
                    isPersianSubtitle(subtitle.language)
                SubtitleFilter.ENGLISH ->
                    isEnglishSubtitle(subtitle.language)
            }
        }

        if (subtitleFilter == SubtitleFilter.ALL) {
            filtered.sortedBy {
                when {
                    isPersianSubtitle(it.language) -> 0
                    isEnglishSubtitle(it.language) -> 1
                    else -> 2
                }
            }
        } else {
            filtered
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("Back")
                }

                Text(
                    text = "TITLE DETAILS",
                    color = CinemaMuted,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        item {
            AsyncImage(
                model = item.backdropUrl ?: item.posterUrl,
                contentDescription = displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        }

        item {
            Text(
                text = displayTitle,
                color = CinemaText,
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = buildString {
                    append(item.normalizedType.uppercase())
                    if (item.year.isNotBlank()) {
                        append("  •  ")
                        append(item.year)
                    }
                    append("  •  ★ ")
                    append(item.ratingText)
                },
                color = CinemaMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (state.loading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = CinemaPrimary
                )
            }
        }

        if (!state.error.isNullOrBlank()) {
            item {
                Text(
                    text = state.error.orEmpty(),
                    color = CinemaRed
                )
            }
        }

        item {
            Text(
                text = "Overview",
                color = CinemaText,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = detailOverview.ifBlank {
                    "No overview is available."
                },
                color = CinemaMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (item.normalizedType == "movie") {

            item {
                val movie = state.movie

                if (movie != null) {
                    Text(
                        text = "Movie information",
                        color = CinemaText,
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (movie.genreText.isNotBlank()) {
                        Text(
                            text = movie.genreText,
                            color = CinemaMuted
                        )
                    }

                    if (movie.runtimeText.isNotBlank()) {
                        Text(
                            text = "Runtime: ${movie.runtimeText}",
                            color = CinemaMuted
                        )
                    }
                }
            }

        } else {

            item {
                Text(
                    text = "Season",
                    color = CinemaText,
                    style = MaterialTheme.typography.titleMedium
                )

                Box {
                    OutlinedButton(
                        onClick = {
                            seasonMenuExpanded = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Season ${state.selectedSeason}",
                            color = CinemaText
                        )
                    }

                    DropdownMenu(
                        expanded = seasonMenuExpanded,
                        onDismissRequest = {
                            seasonMenuExpanded = false
                        }
                    ) {
                        seasons.forEach { season ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        season.name.ifBlank {
                                            "Season ${season.seasonNumber}"
                                        }
                                    )
                                },
                                onClick = {
                                    seasonMenuExpanded = false
                                    selectedSubtitle = null
                                    vm.selectSeason(season.seasonNumber)
                                }
                            )
                        }
                    }
                }
            }

            if (episodes.isNotEmpty()) {

                item {
                    Text(
                        text = "Episodes",
                        color = CinemaText,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(
                    items = episodes,
                    key = { it.id }
                ) { episode ->

                    EpisodeRow(
                        episode = episode,
                        selected = state.selectedEpisode?.id == episode.id,
                        onClick = {
                            selectedSubtitle = null
                            vm.selectEpisode(episode)
                        }
                    )
                }
            }
        }

        item {
            HorizontalDivider(color = CinemaBorder)

            Text(
                text = "Authorized video URL",
                color = CinemaText,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Enter a video URL that you are authorized to access. Cinema does not scrape or bypass streaming services.",
                color = CinemaMuted,
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = videoUrl,
                onValueChange = {
                    videoUrl = it.trim()
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Video URL")
                },
                placeholder = {
                    Text("https://...")
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CinemaText,
                    unfocusedTextColor = CinemaText,
                    focusedBorderColor = CinemaPrimary,
                    unfocusedBorderColor = CinemaBorder,
                    focusedContainerColor = CinemaSurface,
                    unfocusedContainerColor = CinemaSurface
                )
            )

            Button(
                onClick = {
                    onPlay(
                        videoUrl,
                        displayTitle,
                        selectedSubtitle?.url
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isHttpUrl(videoUrl)
            ) {
                Text("Play video")
            }
        }

        item {
            HorizontalDivider(color = CinemaBorder)

            Text(
                text = "Subtitles",
                color = CinemaText,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Search for Persian and English subtitles.",
                color = CinemaMuted,
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = customSubtitleTitle,
                onValueChange = {
                    customSubtitleTitle = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Title or release name")
                },
                placeholder = {
                    Text(displayTitle)
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CinemaText,
                    unfocusedTextColor = CinemaText,
                    focusedBorderColor = CinemaPrimary,
                    unfocusedBorderColor = CinemaBorder,
                    focusedContainerColor = CinemaSurface,
                    unfocusedContainerColor = CinemaSurface
                )
            )

            Button(
                onClick = {
                    selectedSubtitle = null
                    subtitleFilter = SubtitleFilter.ALL

                    vm.searchSubtitles(
                        context = context,
                        customTitle = customSubtitleTitle
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.subtitleLoading
            ) {
                if (state.subtitleLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Search subtitles")
                }
            }
        }

        if (state.subtitles.isNotEmpty()) {

            item {
                Text(
                    text = "${state.subtitles.size} subtitle results",
                    color = CinemaText,
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = subtitleFilter == SubtitleFilter.ALL,
                        onClick = {
                            subtitleFilter = SubtitleFilter.ALL
                        },
                        label = {
                            Text("All")
                        }
                    )

                    FilterChip(
                        selected = subtitleFilter == SubtitleFilter.PERSIAN,
                        onClick = {
                            subtitleFilter = SubtitleFilter.PERSIAN
                        },
                        label = {
                            Text("Persian")
                        }
                    )

                    FilterChip(
                        selected = subtitleFilter == SubtitleFilter.ENGLISH,
                        onClick = {
                            subtitleFilter = SubtitleFilter.ENGLISH
                        },
                        label = {
                            Text("English")
                        }
                    )
                }

                Text(
                    text = "${visibleSubtitles.size} results shown",
                    color = CinemaMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (visibleSubtitles.isEmpty()) {
                item {
                    Text(
                        text = "No results for this language.",
                        color = CinemaMuted
                    )
                }
            }

            items(
                items = visibleSubtitles,
                key = { it.url }
            ) { subtitle ->

                SubtitleRow(
                    subtitle = subtitle,
                    selected = selectedSubtitle?.url == subtitle.url,
                    onClick = {
                        selectedSubtitle = subtitle
                    }
                )
            }
        }

        if (!state.subtitleError.isNullOrBlank()) {
            item {
                Text(
                    text = state.subtitleError.orEmpty(),
                    color = CinemaRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (selectedSubtitle != null) {

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = CinemaCard
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Selected subtitle",
                            color = CinemaText
                        )

                        Text(
                            text = selectedSubtitle?.releaseName
                                ?.ifBlank {
                                    selectedSubtitle?.fullName.orEmpty()
                                }
                                .orEmpty(),
                            color = CinemaMuted
                        )

                        Text(
                            text = selectedSubtitle?.language.orEmpty(),
                            color = CinemaMuted
                        )

                        TextButton(
                            onClick = {
                                selectedSubtitle = null
                            }
                        ) {
                            Text("Clear selection")
                        }

                        Button(
                            onClick = {
                                if (isHttpUrl(videoUrl)) {
                                    onPlay(
                                        videoUrl,
                                        displayTitle,
                                        selectedSubtitle?.url
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isHttpUrl(videoUrl)
                        ) {
                            Text("Play with subtitle")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: TvEpisode,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                CinemaPrimary.copy(alpha = 0.18f)
            } else {
                CinemaCard
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "${episode.code}  •  ${episode.name}",
                color = CinemaText,
                style = MaterialTheme.typography.titleSmall
            )

            if (episode.overview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))

                Text(
                    text = episode.overview,
                    color = CinemaMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SubtitleRow(
    subtitle: SubdlItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                CinemaPrimary.copy(alpha = 0.18f)
            } else {
                CinemaCard
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = subtitle.releaseName.ifBlank {
                    subtitle.fullName.ifBlank {
                        subtitle.fileName
                    }
                },
                color = CinemaText,
                style = MaterialTheme.typography.titleSmall
            )

            Text(
                text = "Language: ${subtitle.language}",
                color = CinemaMuted
            )

            if (subtitle.fileName.isNotBlank()) {
                Text(
                    text = "File: ${subtitle.fileName}",
                    color = CinemaMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = if (selected) "Selected" else "Select subtitle",
                color = CinemaPrimary
            )
        }
    }
}

private fun isPersianSubtitle(language: String): Boolean {
    val value = language.trim().lowercase()

    return value == "fa" ||
        value == "fas" ||
        value == "per" ||
        value.contains("persian") ||
        value.contains("farsi")
}

private fun isEnglishSubtitle(language: String): Boolean {
    val value = language.trim().lowercase()

    return value == "en" ||
        value == "eng" ||
        value.contains("english")
}

private fun isHttpUrl(value: String): Boolean {
    return value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("http://", ignoreCase = true)
}
