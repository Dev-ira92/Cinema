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

        // -------------------------------------------------
        // HEADER
        // -------------------------------------------------

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                    vertical = 18.dp
                ),
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

        // -------------------------------------------------
        // SEARCH FIELD
        // -------------------------------------------------

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),

            placeholder = {
                Text(
                    text = "Search movies and TV shows...",
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

                    IconButton(
                        onClick = vm::clearSearch
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = CinemaMuted
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
                unfocusedTextColor = CinemaText,
                cursorColor = CinemaPrimary,
                focusedContainerColor = CinemaSurface,
                unfocusedContainerColor = CinemaSurface
            )
        )

        // -------------------------------------------------
        // SEARCH RESULTS OR HOME CONTENT
        // -------------------------------------------------

        when {

            state.query.trim().length >= 2 -> {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(16.dp)
                ) {

                    Text(
                        text = "Search results",
                        color = CinemaText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    when {

                        state.searching -> {

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),

                                horizontalArrangement =
                                    Arrangement.Center,

                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                CircularProgressIndicator(
                                    color = CinemaPrimary,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )

                                Spacer(
                                    Modifier.width(12.dp)
                                )

                                Text(
                                    text = "Searching...",
                                    color = CinemaMuted
                                )
                            }
                        }

                        !state.searchError.isNullOrBlank() -> {

                            Text(
                                text = state.searchError.orEmpty(),
                                color = CinemaMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(
                                Modifier.height(12.dp)
                            )

                            TextButton(
                                onClick = {
                                    vm.onQueryChanged(state.query)
                                }
                            ) {
                                Text(
                                    text = "Try again",
                                    color = CinemaPrimary
                                )
                            }
                        }

                        state.results.isEmpty() -> {

                            Text(
                                text = "No matching movies or TV shows were found.",
                                color = CinemaMuted
                            )
                        }

                        else -> {

                            Text(
                                text = "${state.results.size} results",
                                color = CinemaMuted,
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(
                                Modifier.height(8.dp)
                            )

                            state.results.forEach { item ->

                                SearchResultRow(
                                    item = item,
                                    onClick = {
                                        onMediaClick(item)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            else -> {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(bottom = 24.dp)
                ) {

                    if (state.loading) {

                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = CinemaPrimary
                        )
                    }

                    if (!state.error.isNullOrBlank()) {

                        Text(
                            text = state.error.orEmpty(),
                            color = CinemaRed,
                            modifier = Modifier.padding(16.dp)
                        )

                        TextButton(
                            onClick = vm::loadHome
                        ) {
                            Text(
                                text = "Try again",
                                color = CinemaPrimary
                            )
                        }
                    }

                    // Recently Added

                    MediaSection(
                        title = "Recently Added",
                        items = state.trending,
                        onClick = onMediaClick
                    )

                    // Latest Movies

                    MediaSection(
                        title = "Latest Movies",
                        items = state.movies,
                        onClick = onMediaClick
                    )

                    // Latest TV Shows

                    MediaSection(
                        title = "Latest TV Shows",
                        items = state.tvShows,
                        onClick = onMediaClick
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------
// MEDIA SECTION
// ---------------------------------------------------------

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
            text = title,
            color = CinemaText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(
            Modifier.height(12.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(
                horizontal = 16.dp
            ),

            horizontalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            items(
                items = items,
                key = {
                    "${it.normalizedType}:${it.id}"
                }
            ) { item ->

                MediaCard(
                    item = item,
                    onClick = {
                        onClick(item)
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------
// MEDIA CARD
// ---------------------------------------------------------

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
                .clip(
                    RoundedCornerShape(14.dp)
                ),

            contentScale = ContentScale.Crop
        )

        Spacer(
            Modifier.height(8.dp)
        )

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

// ---------------------------------------------------------
// SEARCH RESULT ROW
// ---------------------------------------------------------

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
                .size(
                    width = 65.dp,
                    height = 92.dp
                )
                .clip(
                    RoundedCornerShape(8.dp)
                ),

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

            Spacer(
                Modifier.height(5.dp)
            )

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
