package com.cinema.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cinema.data.AppPrefs
import com.cinema.data.api.VidApiPlayer
import com.cinema.data.model.MediaItem
import com.cinema.ui.theme.CinemaBackground
import com.cinema.ui.theme.CinemaCard
import com.cinema.ui.theme.CinemaHint
import com.cinema.ui.theme.CinemaMuted
import com.cinema.ui.theme.CinemaPrimary
import com.cinema.ui.theme.CinemaRed
import com.cinema.ui.theme.CinemaText

@Composable
fun DetailScreen(
    item: MediaItem,
    onBack: () -> Unit,
    onPlay: (url: String, title: String) -> Unit
) {
    val context = LocalContext.current

    var season by rememberSaveable { mutableStateOf(1) }
    var episode by rememberSaveable { mutableStateOf(1) }
    var subtitleLang by rememberSaveable { mutableStateOf(AppPrefs.subtitleLang(context)) }
    var error by remember { mutableStateOf<String?>(null) }

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

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.title,
                    color = CinemaText,
                    style = MaterialTheme.typography.headlineSmall
                )
                if (item.subtitleLine.isNotEmpty()) {
                    Text(
                        text = item.subtitleLine,
                        color = CinemaMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (item.genre.isNotEmpty()) {
                    Text(
                        text = item.genre,
                        color = CinemaMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
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
                max = 99,
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

        Text(
            text = "Subtitles",
            color = CinemaText,
            style = MaterialTheme.typography.titleMedium
        )

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

        Text(
            text = "Subtitles are searched by the player itself; availability depends on the title.",
            color = CinemaHint,
            style = MaterialTheme.typography.bodySmall
        )

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
                    error = "This title has no valid ID."
                } else {
                    error = null
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

        error?.let {
            Text(text = it, color = CinemaRed)
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
