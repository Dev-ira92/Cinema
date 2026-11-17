package com.cinema

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cinema.data.model.MediaItem
import com.cinema.ui.screens.DetailScreen
import com.cinema.ui.screens.HomeScreen
import com.cinema.ui.screens.PlayerScreen
import com.cinema.ui.theme.CinemaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            CinemaTheme {
                CinemaNavigation()
            }
        }
    }
}

private const val ROUTE_HOME = "home"

private const val ROUTE_DETAIL =
    "detail?imdb={imdb}&tmdb={tmdb}&tv={tv}&title={title}" +
        "&year={year}&poster={poster}&rating={rating}&genre={genre}"

private const val ROUTE_PLAYER = "player?url={url}&title={title}"

private fun stringArg(name: String): NamedNavArgument =
    navArgument(name) {
        type = NavType.StringType
        defaultValue = ""
    }

private fun detailRoute(item: MediaItem): String =
    "detail?imdb=${Uri.encode(item.imdbId.orEmpty())}" +
        "&tmdb=${Uri.encode(item.tmdbId.orEmpty())}" +
        "&tv=${item.isTv}" +
        "&title=${Uri.encode(item.title)}" +
        "&year=${Uri.encode(item.year)}" +
        "&poster=${Uri.encode(item.posterUrl.orEmpty())}" +
        "&rating=${Uri.encode(item.rating)}" +
        "&genre=${Uri.encode(item.genre)}"

@Composable
fun CinemaNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME
    ) {
        composable(ROUTE_HOME) {
            HomeScreen(
                onMediaClick = { item -> navController.navigate(detailRoute(item)) }
            )
        }

        composable(
            route = ROUTE_DETAIL,
            arguments = listOf(
                stringArg("imdb"),
                stringArg("tmdb"),
                navArgument("tv") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                stringArg("title"),
                stringArg("year"),
                stringArg("poster"),
                stringArg("rating"),
                stringArg("genre")
            )
        ) { entry ->
            val args = entry.arguments

            val item = MediaItem(
                imdbId = args?.getString("imdb")?.ifBlank { null },
                tmdbId = args?.getString("tmdb")?.ifBlank { null },
                title = args?.getString("title").orEmpty(),
                year = args?.getString("year").orEmpty(),
                posterUrl = args?.getString("poster")?.ifBlank { null },
                rating = args?.getString("rating").orEmpty(),
                genre = args?.getString("genre").orEmpty(),
                isTv = args?.getBoolean("tv") ?: false
            )

            if (item.embedId == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                DetailScreen(
                    item = item,
                    onBack = { navController.popBackStack() },
                    onPlay = { url, title ->
                        navController.navigate(
                            "player?url=${Uri.encode(url)}&title=${Uri.encode(title)}"
                        )
                    }
                )
            }
        }

        composable(
            route = ROUTE_PLAYER,
            arguments = listOf(stringArg("url"), stringArg("title"))
        ) { entry ->
            PlayerScreen(
                url = entry.arguments?.getString("url").orEmpty(),
                title = entry.arguments?.getString("title").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
