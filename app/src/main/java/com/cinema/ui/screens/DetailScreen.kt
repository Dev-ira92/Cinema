
package com.cinema.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cinema.data.model.MediaItem
import com.cinema.data.model.SubdlItem
import com.cinema.viewmodel.DetailViewModel

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

    LaunchedEffect(item.id, item.mediaType) {
        vm.load(item)
    }

    val displayTitle =
        item.title.ifBlank { item.name }

    val detailOverview =
        state.movie?.overview
            ?: state.tv?.overview
            ?: item.overview

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            TextButton(onClick = onBack) {
                Text("بازگشت")
            }

            Text(
                text = "جزئیات",
                style = MaterialTheme.typography.titleLarge
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Poster
            item {

                AsyncImage(
                    model = item.posterPath?.let {
                        "https://image.tmdb.org/t/p/w500$it"
                    },
                    contentDescription = displayTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )
            }

            // Title
            item {

                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = if (item.mediaType == "tv") {
                        "سریال"
                    } else {
                        "فیلم"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Overview
            item {

                Text(
                    text = detailOverview,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Loading
            if (state.loading) {

                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Detail error
            if (!state.error.isNullOrBlank()) {

                item {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Video URL
            item {

                HorizontalDivider()

                Text(
                    text = "آدرس ویدئو",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = {
                        videoUrl = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Video URL")
                    },
                    placeholder = {
                        Text("https://example.com/video.mp4")
                    },
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (
                            videoUrl.startsWith("https://") ||
                            videoUrl.startsWith("http://")
                        ) {
                            onPlay(
                                videoUrl,
                                displayTitle,
                                selectedSubtitle?.url
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = videoUrl.startsWith("https://") ||
                        videoUrl.startsWith("http://")
                ) {
                    Text("پخش فیلم")
                }
            }

            // Subtitle search
            item {

                HorizontalDivider()

                Text(
                    text = "جست‌وجوی زیرنویس",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = customSubtitleTitle,
                    onValueChange = {
                        customSubtitleTitle = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("نام فیلم یا نسخه انتشار")
                    },
                    placeholder = {
                        Text(displayTitle)
                    },
                    singleLine = true
                )

                Button(
                    onClick = {
                        selectedSubtitle = null

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
                        Text("جست‌وجوی زیرنویس")
                    }
                }

                if (!state.subtitleError.isNullOrBlank()) {
                    Text(
                        text = state.subtitleError.orEmpty(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Selected subtitle
            if (selectedSubtitle != null) {

                item {

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {

                            Text("زیرنویس انتخاب‌شده")

                            Text(
                                selectedSubtitle?.releaseName.orEmpty()
                            )

                            Text(
                                selectedSubtitle?.language.orEmpty()
                            )

                            TextButton(
                                onClick = {
                                    selectedSubtitle = null
                                }
                            ) {
                                Text("لغو انتخاب")
                            }
                        }
                    }
                }
            }

            // Subtitle results
            items(
                items = state.subtitles,
                key = { subtitle ->
                    subtitle.url
                }
            ) { subtitle ->

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {

                        Text(
                            text = subtitle.releaseName.ifBlank {
                                subtitle.fullName
                            },
                            style = MaterialTheme.typography.titleSmall
                        )

                        Text(
                            text = "زبان: ${subtitle.language}"
                        )

                        Text(
                            text = "فایل: ${subtitle.fileName}"
                        )

                        Button(
                            onClick = {
                                selectedSubtitle = subtitle
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Text(
                                if (selectedSubtitle?.url == subtitle.url) {
                                    "انتخاب شده"
                                } else {
                                    "انتخاب زیرنویس"
                                }
                            )
                        }
                    }
                }
            }

            // Play with selected subtitle
            if (selectedSubtitle != null) {

                item {

                    Button(
                        onClick = {

                            if (
                                videoUrl.startsWith("https://") ||
                                videoUrl.startsWith("http://")
                            ) {

                                onPlay(
                                    videoUrl,
                                    displayTitle,
                                    selectedSubtitle?.url
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = videoUrl.startsWith("https://") ||
                            videoUrl.startsWith("http://")
                    ) {
                        Text("پخش همراه زیرنویس")
                    }
                }
            }
        }
    }
}
