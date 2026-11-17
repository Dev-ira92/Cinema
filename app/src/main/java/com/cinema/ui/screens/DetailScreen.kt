
package com.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cinema.data.model.MediaItem
import com.cinema.ui.theme.CinemaBackground
import com.cinema.ui.theme.CinemaMuted
import com.cinema.ui.theme.CinemaPrimary
import com.cinema.ui.theme.CinemaText

@Composable
fun DetailScreen(
    item: MediaItem,
    onBack: () -> Unit,
    onPlay: (String, String, String?) -> Unit
) {
    var season by remember(item.id) {
        mutableStateOf("1")
    }

    var episode by remember(item.id) {
        mutableStateOf("1")
    }

    val isTv = item.normalizedType == "tv"
    val imdbId = item.imdbId.orEmpty().trim()

    val validImdbId = imdbId.matches(
        Regex("^tt[0-9]+$")
    )

    val seasonNumber = season.toIntOrNull()
    val episodeNumber = episode.toIntOrNull()

    val validEpisodeSelection =
        !isTv || (
            seasonNumber != null &&
            episodeNumber != null &&
            seasonNumber > 0 &&
            episodeNumber > 0
        )

    val canPlay = validImdbId && validEpisodeSelection

    val embedUrl = if (canPlay) {
        if (isTv) {
            "https://vaplayer.ru/embed/tv/$imdbId/$seasonNumber/$episodeNumber"
        } else {
            "https://vaplayer.ru/embed/movie/$imdbId"
        }
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(
            onClick = onBack
        ) {
            Text(
                text = "← Back",
                color = CinemaPrimary
            )
        }

        AsyncImage(
            model = item.backdropUrl ?: item.posterUrl,
            contentDescription = item.displayTitle,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )

        Text(
            text = item.displayTitle,
            color = CinemaText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isTv) "TV Series" else "Movie",
                color = CinemaPrimary
            )

            Spacer(modifier = Modifier.width(16.dp))

            if (item.year.isNotBlank()) {
                Text(
                    text = item.year,
                    color = CinemaMuted
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "★ ${item.ratingText}",
                color = CinemaPrimary
            )
        }

        if (item.overview.isNotBlank()) {
            Text(
                text = "Overview",
                color = CinemaPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = item.overview,
                color = CinemaText,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (isTv) {
            Text(
                text = "Select episode",
                color = CinemaPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = season,
                    onValueChange = {
                        season = it.filter { char -> char.isDigit() }.take(3)
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Season") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )

                OutlinedTextField(
                    value = episode,
                    onValueChange = {
                        episode = it.filter { char -> char.isDigit() }.take(3)
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Episode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            }
        }

        if (!validImdbId) {
            Text(
                text = "IMDb ID is not available for this title. Playback cannot start.",
                color = MaterialTheme.colorScheme.error
            )
        } else if (!validEpisodeSelection) {
            Text(
                text = "Enter a valid season and episode number.",
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = {
                embedUrl?.let {
                    onPlay(it, item.displayTitle, null)
                }
            },
            enabled = canPlay,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Play")
        }

        Text(
            text = "Powered by VidAPI",
            color = CinemaMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
