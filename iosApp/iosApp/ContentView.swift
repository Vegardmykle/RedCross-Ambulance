import SwiftUI
import SharedLogic

struct ContentView: View {
    var body: some View {
        TabView {
            DashboardView()
                .tabItem { Label("Dashboard", systemImage: "house.fill") }

            ChecklistRunView()
                .tabItem { Label("Sjekk", systemImage: "checklist") }

            DeficienciesView()
                .tabItem { Label("Mangler", systemImage: "exclamationmark.triangle") }

            ResourcesView()
                .tabItem { Label("Ressurser", systemImage: "doc.text") }
        }
    }
}

