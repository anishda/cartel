package app.audioguide

import android.app.Application
import app.audioguide.di.initKoin
import app.audioguide.platform.PackConfig
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

class AudioguideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@AudioguideApp)
            modules(module { single { PackConfig(BuildConfig.PACK_BASE_URL) } })
        }
    }
}
