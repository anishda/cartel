package app.audioguide.di

import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.domain.TextRecognizer
import app.audioguide.platform.AppInfo
import app.audioguide.platform.AppPaths
import app.audioguide.platform.AppleSpeechSynthesizer
import app.audioguide.platform.VisionTextRecognizer
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<TextRecognizer> { VisionTextRecognizer() }
    single<SpeechSynthesizer> { AppleSpeechSynthesizer() }
    single {
        val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: NSTemporaryDirectory()
        AppPaths(
            packsDir = "$support/packs",
            userDb = "$support/user.db",
            settingsFile = "$support/settings.json",
        )
    }
    single {
        AppInfo(NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "")
    }
    // L'adresse du serveur de packs vient de l'app (Info.plist) : voir startKoinIos.
}
