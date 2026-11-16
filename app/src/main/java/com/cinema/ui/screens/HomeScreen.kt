
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
    onSettingsClick: () -> Unit = {},
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

            TextButton(
                onClick = onSettingsClick
            ) {
                Text(
                    text = "Settings",
                    color = CinemaPrimary
                )
            }

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
                Text("Search movies and TV shows...")
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = vm::clearSearch) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CinemaPrimary,
                unfocusedBorderColor = CinemaCard,
                focusedTextColor = CinemaText,
                unfocusedTextColor = CinemaText
            )
        )

        when {
            state.query.length >= 2 -> {
                if (state.searching) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CinemaPrimary
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            "Search results",
                            color = CinemaText,
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(Modifier.height(16.dp))

                        if (state.results.isEmpty()) {
                            Text(
                                "No results found",
                                color = CinemaMuted
                            )
                        }

                        state.results.forEach { item ->
                            SearchResultRow(
                                item = item,
                                onClick = { onMediaClick(item) }
                            )
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    if (state.loading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = CinemaPrimary
                        )
                    }

                    if (state.error != null) {
                        Text(
                            state.error ?: "",
                            color = CinemaRed,
                            modifier = Modifier.padding(16.dp)
                        )

                        TextButton(onClick = vm::loadHome) {
                            Text("Try again")
                        }
                    }

                    MediaSection(
                        title = "Trending",
                        items = state.trending,
                        onClick = onMediaClick
                    )

                    MediaSection(
                        title = "Popular Movies",
                        items = state.movies,
                        onClick = onMediaClick
                    )

                    MediaSection(
                        title = "Popular TV Shows",
                        items = state.tvShows,
                        onClick = onMediaClick
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaSection(
    title: String,
    items: List<MediaItem>,
    onClick: (MediaItem) -> Unit
) {
    Column(
        modifier = Modifier.padding(top = 22.dp)
    ) {
        Text(
            title,
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
            items(items) { item ->
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
            item.displayTitle,
            color = CinemaText,
            maxLines = 2,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            "${item.year}  •  ★ ${item.ratingText}",
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
                item.displayTitle,
                color = CinemaText,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(5.dp))

            Text(
                "${item.year} • ${item.normalizedType.uppercase()}",
                color = CinemaMuted,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                "★ ${item.ratingText}",
                color = CinemaGold,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
