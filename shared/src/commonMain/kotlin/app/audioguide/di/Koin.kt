package app.audioguide.di

import app.audioguide.catalog.Catalog
import app.audioguide.favorites.FavoritesStore
import app.audioguide.feature.artwork.ArtworkViewModel
import app.audioguide.feature.favorites.FavoriteViewModel
import app.audioguide.feature.favorites.FavoritesViewModel
import app.audioguide.feature.packs.PackRemote
import app.audioguide.feature.packs.PacksViewModel
import app.audioguide.feature.scan.ScanSession
import app.audioguide.feature.search.SearchViewModel
import app.audioguide.feature.settings.SettingsViewModel
import app.audioguide.packs.PackStore
import app.audioguide.platform.AppPaths
import app.audioguide.platform.currentTimeMillis
import app.audioguide.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Ce que chaque plateforme fournit : OCR, voix, [AppPaths] et [app.audioguide.platform.AppInfo].
 * L'application fournit en plus [app.audioguide.platform.PackConfig] (adresse du serveur de packs).
 */
expect val platformModule: Module

val appModule = module {
    single { PackStore(FileSystem.SYSTEM, get<AppPaths>().packsDir.toPath()) }
    single { Catalog(get()) }
    single {
        FavoritesStore(get<AppPaths>().userDb, ::currentTimeMillis, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
    single { SettingsStore(FileSystem.SYSTEM, get<AppPaths>().settingsFile.toPath()) }
    single { ScanSession(get(), get(), get(), get()) }
    single {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 120_000
            }
        }
    }
    single { PackRemote(get(), get(), FileSystem.SYSTEM) }

    viewModelOf(::SearchViewModel)
    viewModelOf(::PacksViewModel)
    viewModelOf(::FavoritesViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { (museumId: String, reference: String) ->
        ArtworkViewModel(museumId, reference, get(), get(), get(), get(), get())
    }
    viewModel { (favoriteId: Long) -> FavoriteViewModel(favoriteId, get(), get(), get()) }
}

fun initKoin(config: KoinAppDeclaration? = null): KoinApplication = startKoin {
    config?.invoke(this)
    modules(appModule, platformModule)
}
