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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cinema.data.model.MediaItem
import com.cinema.data.model.SubdlItem
import com.cinema.data.model.TvEpisode
import com.cinema.ui.theme.*
import com.cinema.viewmodel.DetailViewModel

private enum class SubtitleFilter { ALL, PERSIAN, ENGLISH }

@Composable
fun DetailScreen(
    item   : MediaItem,
    onBack : () -> Unit,
    onPlay : (String, String, String?) -> Unit,
    vm     : DetailViewModel = viewModel()
) {
    val context          = LocalContext.current
    val state            by vm.state.collectAsState()
    var customSubTitle   by remember(item.id) { mutableStateOf("") }
    var selectedSubtitle by remember(item.id) { mutableStateOf<SubdlItem?>(null) }
    var seasonMenuExpanded by remember { mutableStateOf(false) }
    var subtitleFilter   by remember(item.id) { mutableStateOf(SubtitleFilter.ALL) }

    LaunchedEffect(item.id, item.normalizedType) { vm.load(item) }

    val isTv          = item.normalizedType == "tv"
    val displayTitle  = item.displayTitle
    val overview      = state.movie?.overview ?: state.tv?.overview ?: item.overview
    val seasons       = state.tv?.seasons.orEmpty().filter { it.seasonNumber >= 0 }
    val episodes      = state.season?.episodes.orEmpty()

    // IMDB id — from TMDB detail if available, else from catalog
    val imdbId = when {
        isTv  -> state.tv?.imdbId ?: item.imdbId
        else  -> state.movie?.imdbId ?: item.imdbId
    }?.trim()?.takeIf { it.matches(Regex("^tt[0-9]+$")) }

    // vaplayer embed URL
    val embedUrl = buildEmbedUrl(isTv, imdbId, state.selectedEpisode)

    val visibleSubtitles = remember(state.subtitles, subtitleFilter) {
        val f = state.subtitles.filter {
            when (subtitleFilter) {
                SubtitleFilter.ALL     -> true
                SubtitleFilter.PERSIAN -> isPersianSubtitle(it.language
