import SwiftUI
import FirebaseCore
import SharedLogic

@main
struct iOSApp: App {
    init() {
        // Uten GoogleService-Info.plist (bygget rett fra GitHub) krasjer
        // configure(). Da kjører appen kun lokalt, og synken hopper over seg selv.
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }
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
