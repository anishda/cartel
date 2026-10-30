package app.audioguide.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.audioguide.feature.artwork.ArtworkScreen
import app.audioguide.feature.favorites.FavoriteScreen
import app.audioguide.feature.favorites.FavoritesScreen
import app.audioguide.feature.packs.PacksScreen
import app.audioguide.feature.scan.LiveScanScreen
import app.audioguide.feature.scan.ScanScreen
import app.audioguide.feature.search.SearchScreen
import app.audioguide.feature.settings.SettingsScreen
import app.audioguide.resources.Res
import app.audioguide.resources.nav_favorites
import app.audioguide.resources.nav_museums
import app.audioguide.resources.nav_scan
import app.audioguide.resources.nav_search
import app.audioguide.resources.nav_settings
import app.audioguide.ui.AppIcons
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Serializable
data object ScanRoute

@Serializable
data object SearchRoute

@Serializable
data object FavoritesRoute

@Serializable
data object MuseumsRoute

@Serializable
data object SettingsRoute

@Serializable
data object LiveScanRoute

@Serializable
data class FavoriteRoute(val favoriteId: Long)

@Serializable
data class ArtworkRoute(val museumId: String, val reference: String)

/**
 * Onglets de la barre du bas. Ajouter une fonctionnalité de premier niveau (favoris, réglages…)
 * se fait en ajoutant une entrée ici et sa destination dans [AppNavigation].
 */
enum class TopLevel(val route: Any, val label: StringResource, val icon: ImageVector) {
    Scan(ScanRoute, Res.string.nav_scan, AppIcons.Camera),
    Search(SearchRoute, Res.string.nav_search, Icons.Default.Search),
    Favorites(FavoritesRoute, Res.string.nav_favorites, Icons.Default.Favorite),
    Museums(MuseumsRoute, Res.string.nav_museums, AppIcons.Museum),
    Settings(SettingsRoute, Res.string.nav_settings, Icons.Default.Settings),
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination
    val topLevel = TopLevel.entries.firstOrNull { current?.hasRoute(it.route::class) == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (topLevel != null) {
                NavigationBar {
                    TopLevel.entries.forEach { item ->
                        val label = stringResource(item.label)
                        NavigationBarItem(
                            selected = item == topLevel,
                            onClick = { navController.navigateTopLevel(item) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Les pages plein écran (scan en direct, fiche) gèrent elles-mêmes les marges système.
        NavHost(
            navController = navController,
            startDestination = ScanRoute,
            modifier = if (topLevel != null) Modifier.padding(padding) else Modifier,
        ) {
            composable<ScanRoute> {
                ScanScreen(
                    onLiveScan = { navController.navigate(LiveScanRoute) },
                    onOpenArtwork = { museumId, reference -> navController.navigate(ArtworkRoute(museumId, reference)) },
                )
            }
            composable<SearchRoute> {
                SearchScreen(
                    onOpenArtwork = { museumId, reference -> navController.navigate(ArtworkRoute(museumId, reference)) },
                    onBrowseMuseums = { navController.navigateTopLevel(TopLevel.Museums) },
                )
            }
            composable<FavoritesRoute> {
                FavoritesScreen(onOpenFavorite = { navController.navigate(FavoriteRoute(it)) })
            }
            composable<MuseumsRoute> { PacksScreen() }
            composable<SettingsRoute> { SettingsScreen() }
            composable<FavoriteRoute> { entry ->
                FavoriteScreen(
                    favoriteId = entry.toRoute<FavoriteRoute>().favoriteId,
                    onBack = { navController.popBackStack() },
                    onOpenArtwork = { museumId, reference -> navController.navigate(ArtworkRoute(museumId, reference)) },
                )
            }
            composable<LiveScanRoute> {
                LiveScanScreen(onClose = { navController.popBackStack() })
            }
            composable<ArtworkRoute> { entry ->
                val route = entry.toRoute<ArtworkRoute>()
                ArtworkScreen(
                    museumId = route.museumId,
                    reference = route.reference,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(target: TopLevel) {
    navigate(target.route) {
        popUpTo<ScanRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
