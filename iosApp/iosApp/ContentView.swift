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

// Plassholdere – bygges i neste inkrementer
struct ChecklistRunView: View {
    var body: some View {
        Text("Sjekkliste – bygges i neste steg")
            .foregroundStyle(.secondary)
    }
}

struct DeficienciesView: View {
    var body: some View {
        Text("Mangler – bygges i neste steg")
            .foregroundStyle(.secondary)
    }
}

struct ResourcesView: View {
    var body: some View {
        Text("Ressurser – bygges i neste steg")
            .foregroundStyle(.secondary)
    }
}
