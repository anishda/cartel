import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        LiveScannerRegistry.shared.provider = DataScannerProvider()
        KoinIosKt.startKoinIos(packBaseUrl: Bundle.main.object(forInfoDictionaryKey: "PackBaseURL") as? String ?? "")
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
