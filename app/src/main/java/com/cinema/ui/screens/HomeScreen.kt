package com.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cinema.data.model.MediaItem
import com.cinema.ui.theme.CinemaBackground
import com.cinema.ui.theme.CinemaCard
import com.cinema.ui.theme.CinemaHint
import com.cinema.ui.theme.CinemaMuted
import com.cinema.ui.theme.CinemaPrimary
import com.cinema.ui.theme.CinemaPrimaryLight
import com.cinema.ui.theme.CinemaRed
import com.cinema.ui.theme.CinemaText
import com.cinema.viewmodel.Genres
import com.cinema.viewmodel.HomeTab
import com.cinema.viewmodel.HomeViewModel

private const val MAX_FILTER_PAGES = 30

@Composable
fun HomeScreen(
    onMediaClick: (MediaItem) -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val searchActive = state.query.trim().length >= 2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground)
            .systemBarsPadding()
    ) {
        Text(
            text = "Cinema",
            color = CinemaText,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChanged,
            singleLine = true,
            placeholder = { Text("Search movies & series", color = CinemaHint) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (state.query.isNotEmpty()) {
                {
                    IconButton(onClick = vm::clearSearch) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CinemaText,
                unfocusedTextColor = CinemaText,
                focusedBorderColor = CinemaPrimary,
                unfocusedBorderColor = CinemaHint,
                cursorColor = CinemaPrimary,
                focusedLeadingIconColor = CinemaMuted,
                unfocusedLeadingIconColor = CinemaMuted,
                focusedTrailingIconColor = CinemaMuted,
                unfocusedTrailingIconColor = CinemaMuted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        if (searchActive) {
            PosterGrid(
                list = state.results,
                loadKey = 0,
                loading = state.searching,
                error = state.searchError,
                emptyText = "",
                onLoadMore = null,
                onRetry = null,
                onClick = onMediaClick
            )
        } else {
            TabRow(
                selectedTabIndex = state.tab.ordinal,
                containerColor = Color.Transparent,
                contentColor = CinemaPrimaryLight
            ) {
                Tab(
                    selected = state.tab == HomeTab.MOVIES,
                    onClick = { vm.selectTab(HomeTab.MOVIES) },
                    text = { Text("Movies") }
                )
                Tab(
                    selected = state.tab == HomeTab.TV,
                    onClick = { vm.selectTab(HomeTab.TV) },
                    text = { Text("TV Shows") }
                )
            }

            val feed = if (state.tab == HomeTab.MOVIES) state.movies else state.tv

            GenreRow(selected = state.genre, onSelect = vm::selectGenre)

            val chip = Genres.all.firstOrNull { it.label == state.genre }
            val shown = remember(feed.items, chip) {
                if (chip == null) {
                    feed.items
                } else {
                    feed.items.filter { media ->
                        val g = media.genre.lowercase()
                        chip.keys.any { g.contains(it) }
                    }
                }
            }

            // key(...) gives every tab/genre its own scroll position.
            key(state.tab, state.genre) {
                PosterGrid(
                    list = shown,
                    loadKey = feed.items.size,
                    loading = feed.loading,
                    error = feed.error,
                    emptyText = if (chip == null) "Nothing to show yet." else "No titles found for this genre yet.",
                    onLoadMore = {
                        if (chip == null || feed.page < MAX_FILTER_PAGES) vm.loadNext(state.tab)
                    },
                    onRetry = { vm.loadNext(state.tab) },
                    onClick = onMediaClick
                )
            }
        }
    }
}

@Composable
private fun PosterGrid(
    list: List<MediaItem>,
    loadKey: Int,
    loading: Boolean,
    error: String?,
    emptyText: String,
    onLoadMore: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    onClick: (MediaItem) -> Unit
) {
    val gridState = rememberLazyGridState()

    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 6
        }
    }

    if (onLoadMore != null) {
        LaunchedEffect(nearEnd, list.size, loadKey) {
            if (nearEnd) onLoadMore()
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(list, key = { it.key }) { item ->
            PosterCard(item = item, onClick = onClick)
        }

        if (loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CinemaPrimary)
                }
            }
        }

        if (error != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = error, color = CinemaRed, textAlign = TextAlign.Center)
                    if (onRetry != null) {
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
            }
        }

        if (!loading && error == null && list.isEmpty() && emptyText.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = emptyText,
                    color = CinemaMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            }
        }
    }
}

@Composable
private fun PosterCard(
    item: MediaItem,
    onClick: (MediaItem) -> Unit
) {
    Column(modifier = Modifier.clickable { onClick(item) }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(CinemaCard)
        ) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = item.title.take(1),
                    color = CinemaMuted,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Text(
            text = item.title,
            color = CinemaText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )

        if (item.subtitleLine.isNotEmpty()) {
            Text(
                text = item.subtitleLine,
                color = CinemaMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GenreRow(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GenreChipItem(label = "All", selected = selected == null) { onSelect(null) }
        Genres.all.forEach { genre ->
            GenreChipItem(label = genre.label, selected = selected == genre.label) {
                onSelect(genre.label)
            }
        }
    }
}

@Composable
private fun GenreChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = CinemaMuted,
            selectedContainerColor = CinemaPrimary,
            selectedLabelColor = CinemaText
        )
    )
}
