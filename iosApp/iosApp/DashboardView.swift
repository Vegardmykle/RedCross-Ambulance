import SwiftUI
import SharedLogic

struct DashboardView: View {
    private let repo = AppDependencies.shared.repository

    @State private var ambulances: [Ambulance] = []
    @State private var templates: [ChecklistTemplate] = []
    @State private var links: [AppLink] = []
    @AppStorage("selectedAmbulanceId") private var selectedAmbulanceId = ""

    @Environment(\.openURL) private var openURL

    private var selectedAmbulance: Ambulance? {
        ambulances.first { $0.id == selectedAmbulanceId } ?? ambulances.first
    }

    private var dailyTemplate: ChecklistTemplate? {
        templates.first { $0.type == "DAILY" }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    ambulancePicker
                    dailyCard
                    periodicSection
                    quickLinksSection
                }
                .padding()
            }
            .background(Color.rkSurface)
            .navigationTitle("Operativ status")
        }
        .task {
            for await list in repo.ambulances() {
                ambulances = list
                if selectedAmbulanceId.isEmpty, let first = list.first {
                    selectedAmbulanceId = first.id
                }
            }
        }
        .task {
            for await list in repo.topLevelTemplates() {
                templates = list
            }
        }
        .task {
            for await list in repo.links() {
                links = list
            }
        }
    }

    @ViewBuilder
    private var ambulancePicker: some View {
        if ambulances.count > 1 {
            Picker("Ambulanse", selection: $selectedAmbulanceId) {
                ForEach(ambulances, id: \.id) { ambulance in
                    Text(ambulance.callSign).tag(ambulance.id)
                }
            }
            .pickerStyle(.menu)
            .onChange(of: ambulances.count) {
                if selectedAmbulanceId.isEmpty, let first = ambulances.first {
                    selectedAmbulanceId = first.id
                }
            }
        } else if let ambulance = ambulances.first {
            Label(ambulance.callSign, systemImage: "cross.case.fill")
                .font(.headline)
        }
    }

    private var dailyCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Dagens gjøremål")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundStyle(.secondary)
                    .textCase(.uppercase)
                Spacer()
                Text("Ikke påbegynt")
                    .font(.caption2)
                    .fontWeight(.medium)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Color.rkErrorContainer)
                    .foregroundStyle(Color.rkError)
                    .clipShape(Capsule())
            }

            if let daily = dailyTemplate, let ambulance = selectedAmbulance {
                Text("\(daily.name) for \(ambulance.callSign) er klar for gjennomgang.")
                    .font(.body)

                NavigationLink {
                    ChecklistRunScreen(templateType: "DAILY")
                } label: {
                    Label("Start sjekkliste", systemImage: "play.fill")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
            } else {
                Text("Ingen daglig sjekkliste funnet.")
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var periodicSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Periodiske sjekker")
                .font(.caption)
                .fontWeight(.medium)
                .foregroundStyle(.secondary)
                .textCase(.uppercase)

            HStack(spacing: 12) {
                ForEach(templates.filter { $0.type == "WEEKLY" || $0.type == "MONTHLY" }, id: \.id) { template in
                    NavigationLink {
                        ChecklistRunScreen(templateType: template.type)
                    } label: {
                        Label(
                            template.name,
                            systemImage: template.type == "WEEKLY" ? "calendar" : "calendar.badge.clock"
                        )
                        .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.bordered)
                }
            }

            NavigationLink {
                HistoryView()
            } label: {
                Label("Se historikk", systemImage: "clock.arrow.circlepath")
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.bordered)
            .tint(.rkPrimary)
        }
    }

    private var quickLinksSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Hurtiglenker")
                .font(.caption)
                .fontWeight(.medium)
                .foregroundStyle(.secondary)
                .textCase(.uppercase)

            ForEach(links, id: \.id) { link in
                Button {
                    if let url = URL(string: link.url), !link.url.isEmpty {
                        openURL(url)
                    }
                } label: {
                    HStack {
                        Image(systemName: "link")
                        Text(link.title)
                        Spacer()
                        if link.url.isEmpty {
                            Text("URL ikke satt")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        } else {
                            Image(systemName: "arrow.up.right.square")
                        }
                    }
                    .padding()
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .disabled(link.url.isEmpty)
            }
        }
    }
}
