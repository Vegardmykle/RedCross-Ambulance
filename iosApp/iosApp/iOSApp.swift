import SwiftUI
import FirebaseCore
import SharedLogic

@main
struct iOSApp: App {
    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .tint(.rkPrimary)
                .task {
                    // Pull først (unngår duplikat-seeding), seed hvis fortsatt tomt, push deretter
                    try? await AppDependencies.shared.syncService.syncAll()
                    try? await AppDependencies.shared.seeder.seedIfEmpty()
                    try? await AppDependencies.shared.syncService.syncAll()
                }
        }
    }
}
