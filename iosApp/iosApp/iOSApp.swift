import SwiftUI
import SharedLogic

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .tint(.rkPrimary)
                .task {
                    // Legger inn startinnhold første gang appen kjøres
                    try? await AppDependencies.shared.seeder.seedIfEmpty()
                }
        }
    }
}
