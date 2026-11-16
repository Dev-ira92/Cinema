
package com.cinema

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
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
import com.google.gson.Gson

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CinemaTheme {
                CinemaNavigation()
            }
        }
    }
}

@Composable
fun CinemaNavigation() {

    val navController = rememberNavController()
    val gson = Gson()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {

        // صفحه اصلی
        composable("home") {

            HomeScreen(
                onItemClick = { item: MediaItem ->

                    val json = gson.toJson(item)
                    val encodedItem = Uri.encode(json)

                    navController.navigate("detail/$encodedItem")
                }
            )
        }

        // صفحه جزئیات فیلم یا سریال
        composable(
            route = "detail/{itemJson}",
            arguments = listOf(
                navArgument("itemJson") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val encodedItem =
                backStackEntry.arguments?.getString("itemJson").orEmpty()

            val item = runCatching {
                val json = Uri.decode(encodedItem)
                gson.fromJson(json, MediaItem::class.java)
            }.getOrNull()

            if (item == null) {
                navController.popBackStack()
            } else {

                DetailScreen(
                    item = item,
                    onBack = {
                        navController.popBackStack()
                    },
                    onPlay = { videoUrl, title, subtitleUrl ->

                        val route =
                            "player?videoUrl=${Uri.encode(videoUrl)}" +
                            "&title=${Uri.encode(title)}" +
                            "&subtitleUrl=${Uri.encode(subtitleUrl)}"

                        navController.navigate(route)
                    }
                )
            }
        }

        // صفحه پخش فیلم
        composable(
            route = "player?videoUrl={videoUrl}&title={title}&subtitleUrl={subtitleUrl}",
            arguments = listOf(
                navArgument("videoUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = "Cinema"
                },
                navArgument("subtitleUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->

            val videoUrl =
                backStackEntry.arguments?.getString("videoUrl").orEmpty()

            val title =
                backStackEntry.arguments?.getString("title").orEmpty()

            val subtitleUrl =
                backStackEntry.arguments?.getString("subtitleUrl").orEmpty()

            PlayerScreen(
                videoUrl = videoUrl,
                title = title,
                subtitleUrl = subtitleUrl,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
