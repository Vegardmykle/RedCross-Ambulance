import SwiftUI
import SharedLogic

struct DashboardView: View {
    private let repo = AppDependencies.shared.repository

    @State private var ambulances: [Ambulance] = []
    @State private var templates: [ChecklistTemplate] = []
    @State private var links: [AppLink] = []
    /// Vakter der før-kontrollen er signert, men avslutningen aldri ble gjort
    @State private var awaitingClosure: [GetRunsAwaitingClosure] = []
    /// Når hver listetype sist ble fullført for valgt kjøretøy
    @State private var latestCompletedByType: [String: KotlinLong?] = [:]
    @AppStorage("selectedAmbulanceId") private var selectedAmbulanceId = ""
    @State private var isSyncing = false
    @State private var syncError: String?
    @State private var isOffline = false

    @Environment(\.openURL) private var openURL

    private func syncNow() async {
        guard !isSyncing else { return }
        isSyncing = true
        syncError = nil
        // syncAll fanger feil internt og rapporterer dem via status-flyten,
        // som observeres i .task nedenfor
        try? await AppDependencies.shared.syncService.syncAll()
        isSyncing = false
    }

    private var selectedAmbulance: Ambulance? {
        ambulances.first { $0.id == selectedAmbulanceId } ?? ambulances.first
    }

    private var dailyTemplate: ChecklistTemplate? {
        templates.first { $0.type == TemplateType.daily.db }
    }

    /// Periodestatus for valgt kjøretøy. Regelen ligger i sharedLogic, så
    /// iOS og Android leser den likt – tidligere hadde iOS ingen regel i det
    /// hele tatt og viste et fastspikret «Ikke påbegynt».
    private var status: DashboardState {
        DashboardStateKt.dashboardState(
            latestCompletedAt: latestCompletedByType,
            startOfToday: TimeUtil_iosKt.startOfTodayMillis(),
            startOfWeek: TimeUtil_iosKt.startOfWeekMillis(),
            startOfMonth: TimeUtil_iosKt.startOfMonthMillis()
        )
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    ambulancePicker
                    syncErrorBanner
                    offlineBanner
                    openShiftCard
                    dailyCard
                    periodicSection
                    quickLinksSection
                }
                .padding()
            }
            .background(Color.rkSurface)
            .navigationTitle("Operativ status")
            .refreshable { await syncNow() }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { await syncNow() }
                    } label: {
                        if isSyncing {
                            ProgressView()
                        } else {
                            Image(systemName: "arrow.clockwise")
                        }
                    }
                    .disabled(isSyncing)
                    .accessibilityLabel("Hent data fra skyen")
                }
            }
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
        .task {
            for await list in repo.runsAwaitingClosure() {
                awaitingClosure = list
            }
        }
        .task(id: selectedAmbulanceId) {
            guard !selectedAmbulanceId.isEmpty else { return }
            for await map in repo.latestCompletedByType(ambulanceId: selectedAmbulanceId) {
                latestCompletedByType = map
            }
        }
        .task {
            // SKIE gjør sealed interface om til en Swift-enum vi kan switche på
            for await status in AppDependencies.shared.syncService.status {
                switch onEnum(of: status) {
                case .error(let error):
                    syncError = error.message
                    isOffline = false
                case .offline:
                    // Normaltilstand uten dekning – ikke en feil
                    syncError = nil
                    isOffline = true
                default:
                    syncError = nil
                    isOffline = false
                }
            }
        }
    }

    /// Neste mannskap skal kunne se at forrige vakt ikke er lukket – ellers
    /// forsvinner det i stillhet, og ingen vet om bilen ble ryddet og
    /// oksygenet skrudd av.
    @ViewBuilder
    private var openShiftCard: some View {
        if let openShift = awaitingClosure.first(where: { $0.ambulanceId == selectedAmbulance?.id }) {
            NavigationLink {
                ChecklistRunScreen(templateType: TemplateType.daily.db)
            } label: {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Label("Vakt ikke avsluttet", systemImage: "clock.badge.exclamationmark")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundStyle(.orange)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .foregroundStyle(.secondary)
                    }
                    Text(openShiftDetail(openShift))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.leading)
                }
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
        }
    }

    private func openShiftDetail(_ run: GetRunsAwaitingClosure) -> String {
        // Spørringen filtrerer på beforeSignedAt IS NOT NULL, så SQLDelight
        // utleder feltet som ikke-nullbart her
        let millis = run.beforeSignedAt
        let date = Date(timeIntervalSince1970: Double(millis) / 1000)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "nb_NO")
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        var text = "Før-kontrollen ble signert \(formatter.string(from: date))"
        if let name = run.beforeSignedByName { text += " av \(name)" }
        return text + ". Etter-vakt-kontrollen gjenstår."
    }

    /// Uten dekning fungerer appen som normalt – alt lagres lokalt og sendes
    /// når nettet er tilbake. Derfor en nøytral opplysning, ikke en feilmelding.
    @ViewBuilder
    private var offlineBanner: some View {
        if isOffline {
            HStack(spacing: 10) {
                Image(systemName: "icloud.slash")
                    .foregroundStyle(.secondary)
                Text("Ingen nettforbindelse. Arbeidet lagres på enheten og sendes automatisk når du får dekning.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
            }
            .padding()
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    /// Synkfeil ble tidligere svelget helt – nå ser mannskapet at data
    /// ikke har nådd de andre enhetene.
    @ViewBuilder
    private var syncErrorBanner: some View {
        if let syncError {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Color.rkError)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Synkronisering feilet")
                        .fontWeight(.semibold)
                    Text(syncError)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }
            .padding()
            .background(Color.rkErrorContainer)
            .clipShape(RoundedRectangle(cornerRadius: 12))
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
                let done = status.daily.isDone
                Label(
                    done ? "Fullført" : "Ikke påbegynt",
                    systemImage: done ? "checkmark.seal.fill" : "exclamationmark.triangle.fill"
                )
                .font(.caption2)
                .fontWeight(.medium)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(done ? Color.green.opacity(0.15) : Color.rkErrorContainer)
                .foregroundStyle(done ? Color.green : Color.rkError)
                .clipShape(Capsule())
            }

            if let daily = dailyTemplate, let ambulance = selectedAmbulance {
                Text("\(daily.name) for \(ambulance.callSign) er klar for gjennomgang.")
                    .font(.body)

                NavigationLink {
                    ChecklistRunScreen(templateType: TemplateType.daily.db)
                } label: {
                    Label("Start sjekkliste", systemImage: "play.fill")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .foregroundStyle(.white)
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
                ForEach(periodicTemplates, id: \.id) { template in
                    NavigationLink {
                        ChecklistRunScreen(templateType: template.type)
                    } label: {
                        VStack(spacing: 4) {
                            Label(
                                template.name,
                                systemImage: template.type == TemplateType.weekly.db
                                    ? "calendar" : "calendar.badge.clock"
                            )
                            Text(isDone(template) ? "Fullført" : "Venter")
                                .font(.caption2)
                                .foregroundStyle(isDone(template) ? .green : .secondary)
                        }
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

    private var periodicTemplates: [ChecklistTemplate] {
        templates.filter {
            $0.type == TemplateType.weekly.db || $0.type == TemplateType.monthly.db
        }
    }

    private func isDone(_ template: ChecklistTemplate) -> Bool {
        guard let type = TemplateType.companion.fromDb(value: template.type),
              let check = status.statusFor(type: type) else { return false }
        return check.isDone
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
                    let normalized = ResourcesView.normalizeUrl(link.url)
                    if !normalized.isEmpty, let url = URL(string: normalized), url.scheme != nil {
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
