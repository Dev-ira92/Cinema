package com.cinema.ui.screens

import android.content.Context
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
import com.cinema.data.api.SubtitleApi
import com.cinema.data.model.MediaItem
import com.cinema.data.model.TvEpisode
import com.cinema.ui.theme.*
import com.cinema.viewmodel.DetailViewModel

@Composable
fun DetailScreen(
    item: MediaItem,
    onPlay: (String, String, String?) -> Unit,
    vm: DetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    var customTitle by remember(item.id) {
        mutableStateOf(item.displayTitle)
    }

    var showSubtitleSearch by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(item.id, item.mediaType) {
        vm.load(item)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text(
                item.displayTitle,
                color = CinemaText,
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(12.dp))

            AsyncImage(
                model = item.backdropUrl ?: item.posterUrl,
                contentDescription = item.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "${item.year} • ${item.normalizedType.uppercase()} • ★ ${item.ratingText}",
                color = CinemaSecondary
            )

            Spacer(Modifier.height(12.dp))

            Text(
                item.overview,
                color = CinemaMuted
            )

            Spacer(Modifier.height(20.dp))
        }

        if (state.loading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = CinemaPrimary
                )
            }
        }

        state.error?.let { error ->
            item {
                Text(error, color = CinemaRed)
            }
        }

        if (state.tv != null) {
            item {
                Text(
                    "Seasons",
                    color = CinemaText,
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.tv?.seasons
                        ?.filter { it.seasonNumber > 0 }
                        ?.forEach { season ->
                            FilterChip(
                                selected = state.selectedSeason ==
                                    season.seasonNumber,
                                onClick = {
                                    vm.selectSeason(season.seasonNumber)
                                },
                                label = {
                                    Text("Season ${season.seasonNumber}")
                                }
                            )
                        }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    "Episodes",
                    color = CinemaText,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            items(state.season?.episodes ?: emptyList()) { episode ->
                EpisodeRow(
                    episode = episode,
                    selected = state.selectedEpisode?.id == episode.id,
                    onClick = { vm.selectEpisode(episode) }
                )
            }

            if (state.selectedEpisode != null) {
                item {
                    Button(
                        onClick = {
                            showSubtitleSearch = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Search episode subtitles")
                    }
                }
            }
        }

        if (state.movie != null) {
            item {
                Button(
                    onClick = {
                        showSubtitleSearch = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Search movie subtitles")
                }
            }
        }

        if (showSubtitleSearch) {
            item {
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("Subtitle search title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        vm.searchSubtitles(context, customTitle)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.subtitleLoading
                ) {
                    Text(
                        if (state.subtitleLoading) {
                            "Searching..."
                        } else {
                            "Search Subtitles"
                        }
                    )
                }
            }
        }

        if (state.subtitleLoading) {
            item {
                CircularProgressIndicator(
                    color = CinemaPrimary
                )
            }
        }

        state.subtitleError?.let { message ->
            item {
                Text(
                    message,
                    color = CinemaRed,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }

        items(state.subtitles) { subtitle ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CinemaCard)
                    .padding(12.dp)
            ) {
                Text(
                    subtitle.releaseName.ifBlank {
                        subtitle.fileName.ifBlank {
                            subtitle.fullName
                        }
                    },
                    color = CinemaText
                )

                Text(
                    "Language: ${subtitle.language} • ${subtitle.author}",
                    color = CinemaMuted,
                    style = MaterialTheme.typography.bodySmall
                )

                TextButton(
                    onClick = {
                        val subtitleUrl = when {
                            subtitle.url.startsWith("http") ->
                                subtitle.url

                            subtitle.url.isNotBlank() ->
                                "${SubtitleApi.DOWNLOAD_BASE}${subtitle.url}"

                            else -> ""
                        }

                        if (subtitleUrl.isNotBlank()) {
                            onPlay("", item.displayTitle, subtitleUrl)
                        }
                    }
                ) {
                    Text("Use subtitle")
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) CinemaCard
                else CinemaSurface
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = episode.stillUrl,
            contentDescription = episode.name,
            modifier = Modifier
                .size(width = 90.dp, height = 58.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                episode.code,
                color = CinemaSecondary
            )

            Text(
                episode.name,
                color = CinemaText,
                maxLines = 2
            )
        }
    }
}
