package com.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cinema.data.api.tmdbImage
import com.cinema.data.model.MediaItem
import com.cinema.ui.theme.CinemaBackground
import com.cinema.ui.theme.CinemaCard
import com.cinema.ui.theme.CinemaHint
import com.cinema.ui.theme.CinemaMuted
import com.cinema.ui.theme.CinemaText
import com.cinema.viewmodel.PersonViewModel

@Composable
fun PersonScreen(
    personId: Int,
    fallbackName: String,
    onBack: () -> Unit,
    onTitleClick: (MediaItem) -> Unit,
    vm: PersonViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    var bioExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(personId) { vm.load(personId) }

    val person = state.person

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground)
            .systemBarsPadding()
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = CinemaText
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(CinemaCard)
            ) {
                tmdbImage(person?.profilePath, "w185")?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = person?.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = person?.name ?: fallbackName,
                    color = CinemaText,
                    style = MaterialTheme.typography.headlineSmall
                )
                person?.knownForDepartment?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, color = CinemaMuted, style = MaterialTheme.typography.bodyMedium)
                }
                val born = listOfNotNull(
                    person?.birthday?.takeIf { it.isNotBlank() },
                    person?.placeOfBirth?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (born.isNotEmpty()) {
                    Text(text = born, color = CinemaHint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        person?.biography?.trim()?.takeIf { it.isNotEmpty() }?.let { bio ->
            Text(
                text = bio,
                color = CinemaMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (bioExpanded) 12 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { bioExpanded = !bioExpanded }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        PosterGrid(
            list = state.titles,
            loadKey = 0,
            loading = state.loading,
            error = state.error,
            emptyText = "No titles found.",
            onLoadMore = null,
            onRetry = { vm.retry(personId) },
            onClick = onTitleClick
        )
    }
}
