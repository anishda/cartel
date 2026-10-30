package app.audioguide

import app.audioguide.di.initKoin
import app.audioguide.platform.PackConfig
import org.koin.dsl.module

/** Point d'entrée appelé depuis Swift : `KoinIosKt.startKoinIos(packBaseUrl:)`. */
fun startKoinIos(packBaseUrl: String) {
    initKoin {
        modules(module { single { PackConfig(packBaseUrl) } })
    }
}
