
package com.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cinema.data.model.MediaItem
import com.cinema.ui.theme.*
import com.cinema.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onMediaClick: (MediaItem) -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CINEMA",
                color = CinemaPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "MOVIES & SERIES",
                color = CinemaMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = {
                Text(
                    "Search movies and TV shows...",
                    color = CinemaHint
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = CinemaMuted
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = vm::clearSearch) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = CinemaMuted
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CinemaPrimary,
                unfocusedBorderColor = CinemaBorder,
                focusedTextColor = CinemaText,
                unfocusedTextColor = CinemaText,
                cursorColor = CinemaPrimary,
                focusedContainerColor = CinemaSurface,
                unfocusedContainerColor = CinemaSurface
            )
        )

        when {
            state.query.trim().length >= 2 -> {
                SearchContent(
                    loading = state.searching,
                    results = state.results,
                    error = state.error,
                    onMediaClick = onMediaClick
                )
            }

            else -> {
                HomeContent(
                    loading = state.loading,
                    error = state.error,
                    trending = state.trending,
                    movies = state.movies,
                    tvShows = state.tvShows,
                    onRetry = vm::loadHome,
                    onMediaClick = onMediaClick
                )
            }
        }
    }
}

@Composable
private fun SearchContent(
    loading: Boolean,
    results: List<MediaItem>,
    error: String?,
    onMediaClick: (MediaItem) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        when {
            loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = CinemaPrimary
                )
            }

            results.isNotEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp)
                ) {
                    Text(
                        text = "Search results",
                        color = CinemaText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))

                    results.forEach { item ->
                        SearchResultRow(
                            item = item,
                            onClick = { onMediaClick(item) }
                        )
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = CinemaMuted,
                        modifier = Modifier.size(42.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = error ?: "No matching titles found.",
                        color = CinemaMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    loading: Boolean,
    error: String?,
    trending: List<MediaItem>,
    movies: List<MediaItem>,
    tvShows: List<MediaItem>,
    onRetry: () -> Unit,
    onMediaClick: (MediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = CinemaPrimary
            )
        }

        if (!error.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = error,
                    color = CinemaRed
                )

                TextButton(onClick = onRetry) {
                    Text("Try again")
                }
            }
        }

        MediaSection(
            title = "Trending",
            items = trending,
            onClick = onMediaClick
        )

        MediaSection(
            title = "Popular Movies",
            items = movies,
            onClick = onMediaClick
        )

        MediaSection(
            title = "Popular TV Shows",
            items = tvShows,
            onClick = onMediaClick
        )

        if (
            !loading &&
            error == null &&
            trending.isEmpty() &&
            movies.isEmpty() &&
            tvShows.isEmpty()
        ) {
            Text(
                text = "No titles are available right now.",
                color = CinemaMuted,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Composable
private fun MediaSection(
    title: String,
    items: List<MediaItem>,
    onClick: (MediaItem) -> Unit
) {
    if (items.isEmpty()) return

    Column(
        modifier = Modifier.padding(top = 22.dp)
    ) {
        Text(
            text = title,
            color = CinemaText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = items,
                key = { "${it.normalizedType}_${it.id}" }
            ) { item ->
                MediaCard(
                    item = item,
                    onClick = { onClick(item) }
                )
            }
        }
    }
}

@Composable
private fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(145.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.displayTitle,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = item.displayTitle,
            color = CinemaText,
            maxLines = 2,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "${item.year}  •  ★ ${item.ratingText}",
            color = CinemaMuted,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun SearchResultRow(
    item: MediaItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.displayTitle,
            modifier = Modifier
                .size(width = 65.dp, height = 92.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = item.displayTitle,
                color = CinemaText,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(5.dp))

            Text(
                text = "${item.year} • ${item.normalizedType.uppercase()}",
                color = CinemaMuted,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "★ ${item.ratingText}",
                color = CinemaGold,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
