import SwiftUI
import SharedLogic

// Svaralternativene med visningstekst og farge
enum AnswerChoice: String, CaseIterable {
    case ja = "JA"
    case nei = "NEI"
    case mangelfull = "MANGELFULL"
    case odelagt = "ODELAGT"

    var label: String {
        switch self {
        case .ja: "Ja"
        case .nei: "Nei"
        case .mangelfull: "Mangel"
        case .odelagt: "Ødelagt"
        }
    }

    var color: Color {
        switch self {
        case .ja: .green
        case .nei: .orange
        case .mangelfull: .orange
        case .odelagt: .rkError
        }
    }

    var kotlinValue: ItemResult {
        switch self {
        case .ja: .ja
        case .nei: .nei
        case .mangelfull: .mangelfull
        case .odelagt: .odelagt
        }
    }
}

/// Tab-rot: daglig sjekkliste i egen NavigationStack.
struct ChecklistRunView: View {
    var body: some View {
        NavigationStack {
            ChecklistRunScreen(templateType: "DAILY")
        }
    }
}

/// Selve sjekkliste-skjermen. Kan pushes fra dashboardet med annen listetype.
struct ChecklistRunScreen: View {
    var templateType = "DAILY"

    private let repo = AppDependencies.shared.repository

    @AppStorage("selectedAmbulanceId") private var selectedAmbulanceId = ""

    @State private var template: ChecklistTemplate?
    @State private var run: ChecklistRun?
    @State private var items: [ChecklistItem] = []
    @State private var bags: [ChecklistTemplate] = []
    @State private var bagItems: [String: [ChecklistItem]] = [:]
    @State private var responses: [String: ChecklistResponse] = [:]
    @State private var earlierDeficiencyItemIds: Set<String> = []
    @State private var showSignSheet = false
    @State private var justCompleted = false
    @State private var showEditWarning = false
    @State private var navigateToEdit = false
    @State private var showReopenConfirm = false
    @State private var crew: [User] = []

    /// Hvilken del signeringsarket gjelder
    @State private var signPhase: ChecklistPhase = .before

    private var allItems: [ChecklistItem] {
        items + bagItems.values.flatMap { $0 }
    }

    private var answeredCount: Int {
        allItems.filter { responses[$0.id] != nil }.count
    }

    private var allAnswered: Bool {
        !allItems.isEmpty && answeredCount == allItems.count
    }

    // MARK: - To faser
    //
    // Vakta kontrolleres i to trinn: utstyret før vakt, avslutningen etterpå.
    // Har lista ingen etter-punkter (ukentlig, månedlig) beholdes én signatur.

    private func inPhase(_ list: [ChecklistItem], _ phase: ChecklistPhase) -> [ChecklistItem] {
        list.filter { ChecklistPhase.companion.fromDb(value: $0.phase) == phase }
    }

    private var beforeItems: [ChecklistItem] { inPhase(items, .before) }
    private var afterItems: [ChecklistItem] { inPhase(allItems, .after) }
    private var beforeAll: [ChecklistItem] { inPhase(allItems, .before) }

    private var hasAfterPhase: Bool { !afterItems.isEmpty }

    private var beforeAnswered: Int {
        beforeAll.filter { responses[$0.id] != nil }.count
    }

    private var beforeComplete: Bool {
        !beforeAll.isEmpty && beforeAnswered == beforeAll.count
    }

    private var afterAnswered: Int {
        afterItems.filter { responses[$0.id] != nil }.count
    }

    private var beforeSignedAt: Int64? { run?.beforeSignedAt?.int64Value }
    private var beforeSigned: Bool { beforeSignedAt != nil }

    private var beforeSignedByName: String? {
        guard let id = run?.beforeUserId else { return nil }
        return crew.first { $0.id == id }?.name
    }

    // Nøklene regnes ut her i stedet for inne i modifikator-kjeden.
    // Strenginterpolasjon midt i en lang kjede får typesjekkeren i Swift
    // til å gi opp.
    private var templateKey: String { template?.id ?? "" }
    private var runKey: String { run?.id ?? "" }
    private var runStartKey: String { "\(templateKey)|\(selectedAmbulanceId)" }

    // Delt i to: presentasjon og datainnhenting. Samlet i én kjede ble
    // uttrykket for komplekst til å typesjekkes.
    var body: some View {
        presentedList
            .task { await observeAmbulances() }
            .task { await observeTemplates() }
            .task { await observeCrew() }
            .task(id: runStartKey) { await startRunIfReady() }
            .task(id: templateKey) { await observeItems() }
            .task(id: templateKey) { await observeBags() }
            .task(id: runKey) { await observeResponses() }
            .task(id: runKey) { await observeEarlierDeficiencies() }
    }

    private var presentedList: some View {
        checklistList
            .navigationTitle(template?.name ?? "Sjekkliste")
            .toolbar { editToolbarItem }
            .alert("Redigere sjekklisten?", isPresented: $showEditWarning) {
                Button("Avbryt", role: .cancel) {}
                Button("Fortsett") { navigateToEdit = true }
            } message: {
                Text("Endringer i lista gjelder for alle brukere og alle ambulanser, ikke bare deg.")
            }
            .navigationDestination(isPresented: $navigateToEdit) { editDestination }
            .sheet(isPresented: $showSignSheet) { signSheet }
            .alert("Gjenåpne før-kontrollen?", isPresented: $showReopenConfirm) {
                Button("Avbryt", role: .cancel) {}
                Button("Gjenåpne") { Task { await reopenBefore() } }
            } message: {
                Text("Signaturen fjernes, og du kan endre svarene. Før-kontrollen må signeres på nytt før vakta kan avsluttes.")
            }
    }

    // Delt opp i småbiter fordi typesjekkeren i Swift gir opp på lange
    // view-uttrykk med forgreninger
    private var checklistList: some View {
        List {
            progressSection
            beforePhaseContent
            afterPhaseContent
        }
    }

    @ViewBuilder
    private var beforePhaseContent: some View {
        if beforeSigned {
            beforeSignedSummary
        } else {
            equipmentSection
            bagSectionsView
            beforeSignSection
        }
    }

    @ViewBuilder
    private var afterPhaseContent: some View {
        if hasAfterPhase {
            afterSection
            afterSignSection
        }
    }

    private var equipmentSection: some View {
        Section {
            ForEach(inFixedOrder(beforeItems), id: \.id) { item in
                ChecklistItemRow(
                    item: item,
                    response: responses[item.id],
                    hasEarlierDeficiency: earlierDeficiencyItemIds.contains(item.id),
                    onAnswer: { choice, comment, reading in
                        await answer(item: item, choice: choice, comment: comment, reading: reading)
                    }
                )
            }
        } header: {
            HStack {
                Text(hasAfterPhase ? "Før vakt" : "Utstyr")
                Spacer()
                Text("\(beforeAnswered) av \(beforeAll.count)")
                    .foregroundStyle(.secondary)
            }
        }
    }

    /// Når før-delen er signert erstattes punktene av hvem som signerte og når.
    /// Uten dette møter mannskapet en tilsynelatende tom liste etter vakta og
    /// tror de må begynne på nytt.
    private var beforeSignedSummary: some View {
        Section {
            VStack(alignment: .leading, spacing: 6) {
                Label("Før vakt signert", systemImage: "checkmark.seal.fill")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundStyle(.green)

                if let signedAt = beforeSignedAt {
                    Text(signatureText(at: signedAt, by: beforeSignedByName))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Button {
                    showReopenConfirm = true
                } label: {
                    Label("Gjenåpne og endre", systemImage: "pencil")
                        .font(.caption)
                }
                .frame(minHeight: 44)
            }
        }
    }

    private var afterSection: some View {
        Section {
            ForEach(inFixedOrder(afterItems), id: \.id) { item in
                ChecklistItemRow(
                    item: item,
                    response: responses[item.id],
                    hasEarlierDeficiency: earlierDeficiencyItemIds.contains(item.id),
                    onAnswer: { choice, comment, reading in
                        await answer(item: item, choice: choice, comment: comment, reading: reading)
                    }
                )
            }
        } header: {
            HStack {
                Text("Etter vakt")
                Spacer()
                Text("\(afterAnswered) av \(afterItems.count)")
                    .foregroundStyle(.secondary)
            }
        } footer: {
            Text(beforeSigned
                 ? "Fylles ut når vakta er ferdig."
                 : "Gjøres ved vaktslutt – etter at før-kontrollen er signert.")
        }
    }

    private func signatureText(at millis: Int64, by name: String?) -> String {
        let date = Date(timeIntervalSince1970: Double(millis) / 1000)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "nb_NO")
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        let time = formatter.string(from: date)
        return name.map { "\(time) av \($0)" } ?? time
    }

    private var bagSectionsView: some View {
        ForEach(bags, id: \.id) { bag in
            BagSection(
                bag: bag,
                responses: responses,
                earlierDeficiencyItemIds: earlierDeficiencyItemIds,
                onItemsChange: { bagItems[bag.id] = $0 },
                onAnswer: { item, choice, comment, reading in
                    await answer(item: item, choice: choice, comment: comment, reading: reading)
                }
            )
        }
    }

    @ToolbarContentBuilder
    private var editToolbarItem: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            if template != nil {
                Button {
                    showEditWarning = true
                } label: {
                    Image(systemName: "pencil")
                }
                .accessibilityLabel("Rediger sjekkliste")
            }
        }
    }

    @ViewBuilder
    private var editDestination: some View {
        if let template {
            EditTemplateView(template: template)
        }
    }

    private var signSheet: some View {
        let signingBefore = signPhase == .before
        return SignSheetView(
            title: signingBefore ? "Signer før vakt" : "Signer og avslutt vakt",
            // Vis bare avvikene fra den delen som faktisk signeres
            deficiencies: signingBefore ? deficiencies(in: beforeAll) : deficiencies(in: allItems),
            onSign: { userId in
                signingBefore
                    ? await signBefore(userId: userId)
                    : await complete(userId: userId)
            }
        )
    }

    private func signBefore(userId: String) async -> Bool {
        guard let run, let template else { return false }
        do {
            try await repo.signBeforeShift(runId: run.id, userId: userId)
            await loadRun(templateId: template.id)
            // Synk i bakgrunnen – feiler stille uten dekning
            AppDependencies.shared.syncService.requestSync()
            return true
        } catch {
            return false
        }
    }

    private func reopenBefore() async {
        guard let run, let template else { return }
        try? await repo.reopenBeforeShift(runId: run.id)
        await loadRun(templateId: template.id)
    }

    private func observeCrew() async {
        for await list in repo.users() {
            crew = list
        }
    }

    private func observeAmbulances() async {
        for await list in repo.ambulances() {
            if selectedAmbulanceId.isEmpty, let first = list.first {
                selectedAmbulanceId = first.id
            }
        }
    }

    private func observeTemplates() async {
        for await list in repo.topLevelTemplates() {
            template = list.first { $0.type == templateType }
        }
    }

    private func startRunIfReady() async {
        guard let template, !selectedAmbulanceId.isEmpty else { return }
        await loadRun(templateId: template.id)
    }

    private func observeItems() async {
        guard let template else { return }
        for await list in repo.itemsFor(templateId: template.id) {
            items = list
        }
    }

    private func observeBags() async {
        guard let template else { return }
        for await list in repo.bagsFor(templateId: template.id) {
            bags = list
        }
    }

    private func observeResponses() async {
        guard let run else { return }
        for await list in repo.responsesForRun(runId: run.id) {
            responses = Dictionary(uniqueKeysWithValues: list.map { ($0.itemId, $0) })
        }
    }

    private func observeEarlierDeficiencies() async {
        guard let run else { return }
        for await ids in repo.itemIdsWithOpenDeficiencies(
            ambulanceId: run.ambulanceId,
            excludeRunId: run.id
        ) {
            earlierDeficiencyItemIds = Set(ids)
        }
    }

    private var progressSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                if justCompleted {
                    Label("Sjekkliste signert og lukket", systemImage: "checkmark.seal.fill")
                        .foregroundStyle(.green)
                }
                ProgressView(value: Double(answeredCount), total: Double(max(allItems.count, 1)))
                    .tint(.rkPrimary)
                Text("\(answeredCount) av \(allItems.count) punkter besvart")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var beforeSignSection: some View {
        let canSign = hasAfterPhase ? beforeComplete : allAnswered
        return Section {
            Button {
                signPhase = hasAfterPhase ? .before : .after
                showSignSheet = true
            } label: {
                Label(
                    hasAfterPhase ? "Signer før vakt" : "Signer og fullfør",
                    systemImage: "signature"
                )
                .fontWeight(.semibold)
                .frame(maxWidth: .infinity)
                .foregroundStyle(canSign ? .white : .secondary)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!canSign)

            if !canSign {
                Text("Du må svare på alle punkter før du kan signere.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .listRowBackground(Color.clear)
    }

    private var afterSignSection: some View {
        let canSign = beforeSigned && allAnswered
        return Section {
            Button {
                signPhase = .after
                showSignSheet = true
            } label: {
                Label("Signer og avslutt vakt", systemImage: "signature")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .foregroundStyle(canSign ? .white : .secondary)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!canSign)

            if !beforeSigned {
                Text("Før-kontrollen må signeres først.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else if !allAnswered {
                Text("Du må svare på alle punkter før du kan avslutte vakta.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .listRowBackground(Color.clear)
    }

    private func deficiencies(in list: [ChecklistItem]) -> [DeficiencySummary] {
        list.compactMap { item in
            guard let response = responses[item.id], response.result != "JA" else { return nil }
            return DeficiencySummary(
                id: item.id,
                title: item.title,
                result: AnswerChoice(rawValue: response.result)?.label ?? response.result,
                comment: response.comment
            )
        }
    }

    private func loadRun(templateId: String) async {
        guard !selectedAmbulanceId.isEmpty else { return }
        justCompleted = false
        run = try? await repo.startOrResumeRun(
            templateId: templateId,
            ambulanceId: selectedAmbulanceId
        )
    }

    /// Punktene står i fast rekkefølge mens kontrollen pågår.
    ///
    /// Tidligere sank besvarte punkter til bunnen, men da flyttet innholdet seg
    /// under fingeren på mannskapet: du sikter på ett punkt, lista hopper, og du
    /// treffer et annet. Særlig uheldig med hansker, i bevegelse, eller for
    /// brukere med nedsatt syn eller skjelvinger. Rekkefølgen følger nå
    /// sortOrder, som er den rekkefølgen utstyret ligger i bilen.
    private func inFixedOrder(_ list: [ChecklistItem]) -> [ChecklistItem] {
        list.sorted { $0.sortOrder < $1.sortOrder }
    }

    private func answer(item: ChecklistItem, choice: AnswerChoice, comment: String?, reading: String?) async {
        guard let run else { return }
        try? await repo.setResponse(
            runId: run.id,
            itemId: item.id,
            result: choice.kotlinValue,
            comment: comment,
            reading: reading
        )
    }

    private func complete(userId: String) async -> Bool {
        guard let run, let template else { return false }
        do {
            try await repo.completeRun(runId: run.id, userId: userId, comment: nil)
            justCompleted = true
            await loadRun(templateId: template.id)
            // Synk i bakgrunnen – feiler stille uten dekning, tas igjen ved neste sync
            AppDependencies.shared.syncService.requestSync()
            return true
        } catch {
            return false
        }
    }
}

struct ChecklistItemRow: View {
    let item: ChecklistItem
    let response: ChecklistResponse?
    var hasEarlierDeficiency = false
    let onAnswer: (AnswerChoice, String?, String?) async -> Void

    @State private var showCommentAlert = false
    @State private var showValueAlert = false
    @State private var pendingChoice: AnswerChoice?
    @State private var commentText = ""
    @State private var valueText = ""

    private var requiresValue: Bool { item.requiresValue != 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(item.title)
                .font(.body)
            if hasEarlierDeficiency {
                Label("Åpent avvik fra tidligere kontroll", systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundStyle(.orange)
            }
            if let description = item.description_, !description.isEmpty {
                Text(description)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            HStack(spacing: 8) {
                ForEach(AnswerChoice.allCases, id: \.self) { choice in
                    answerButton(choice)
                }
            }

            if let reading = response?.reading, !reading.isEmpty {
                Label("Avlest: \(reading) \(item.unit ?? "")", systemImage: "gauge")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if let comment = response?.comment, !comment.isEmpty {
                Label(comment, systemImage: "text.bubble")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
        .alert("Kommentar", isPresented: $showCommentAlert) {
            TextField("Beskriv avviket", text: $commentText)
            Button("Avbryt", role: .cancel) {}
            Button("Lagre") {
                if let choice = pendingChoice {
                    let comment = commentText
                    Task { await onAnswer(choice, comment.isEmpty ? nil : comment, nil) }
                }
            }
        } message: {
            Text("Beskriv gjerne hva som mangler eller er ødelagt.")
        }
        .alert("Avlest verdi", isPresented: $showValueAlert) {
            TextField("F.eks. 180", text: $valueText)
                .keyboardType(.decimalPad)
                .onChange(of: valueText) { _, newValue in
                    // Kun sifre og ett desimaltegn
                    var filtered = newValue.replacingOccurrences(of: ",", with: ".")
                        .filter { $0.isNumber || $0 == "." }
                    if let first = filtered.firstIndex(of: ".") {
                        let afterFirst = filtered.index(after: first)
                        filtered = String(filtered[..<afterFirst])
                            + filtered[afterFirst...].filter { $0.isNumber }
                    }
                    if filtered != newValue { valueText = filtered }
                }
            Button("Avbryt", role: .cancel) {}
            Button("Lagre") {
                var value = valueText.trimmingCharacters(in: .whitespaces)
                if value.hasSuffix(".") { value = String(value.dropLast()) }
                if value.hasPrefix(".") { value = "0" + value }
                // Lagres kun hvis det er et gyldig tall
                guard Double(value) != nil else { return }
                Task { await onAnswer(.ja, nil, value) }
            }
        } message: {
            Text("Skriv inn verdien som står på måleren (\(item.unit ?? "")).")
        }
    }

    private func answerButton(_ choice: AnswerChoice) -> some View {
        let isSelected = response?.result == choice.rawValue
        return Button {
            if choice == .ja {
                if requiresValue {
                    valueText = response?.reading ?? ""
                    showValueAlert = true
                } else {
                    Task { await onAnswer(choice, nil, nil) }
                }
            } else {
                pendingChoice = choice
                commentText = response?.comment ?? ""
                showCommentAlert = true
            }
        } label: {
            Text(choice.label)
                .font(.subheadline)
                .fontWeight(isSelected ? .semibold : .regular)
                .frame(maxWidth: .infinity, minHeight: 44)
        }
        .buttonStyle(.bordered)
        .tint(isSelected ? choice.color : .secondary)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(isSelected ? choice.color : .clear, lineWidth: 1.5)
        )
    }
}

struct BagSection: View {
    let bag: ChecklistTemplate
    let responses: [String: ChecklistResponse]
    var earlierDeficiencyItemIds: Set<String> = []
    let onItemsChange: ([ChecklistItem]) -> Void
    let onAnswer: (ChecklistItem, AnswerChoice, String?, String?) async -> Void

    private let repo = AppDependencies.shared.repository
    @State private var items: [ChecklistItem] = []

    private var answeredCount: Int {
        items.filter { responses[$0.id] != nil }.count
    }

    /// Fast rekkefølge – punktene skal ikke flytte seg mens mannskapet svarer.
    private var sortedItems: [ChecklistItem] {
        items.sorted { $0.sortOrder < $1.sortOrder }
    }

    var body: some View {
        Section {
            DisclosureGroup {
                ForEach(sortedItems, id: \.id) { item in
                    ChecklistItemRow(
                        item: item,
                        response: responses[item.id],
                        hasEarlierDeficiency: earlierDeficiencyItemIds.contains(item.id),
                        onAnswer: { choice, comment, reading in
                            await onAnswer(item, choice, comment, reading)
                        }
                    )
                }
            } label: {
                HStack {
                    Label(bag.name, systemImage: "backpack")
                        .fontWeight(.medium)
                    Spacer()
                    Text("\(answeredCount)/\(items.count)")
                        .font(.caption)
                        .foregroundStyle(answeredCount == items.count && !items.isEmpty ? .green : .secondary)
                }
            }
        }
        .task {
            for await list in repo.itemsFor(templateId: bag.id) {
                items = list
                onItemsChange(list)
            }
        }
    }
}

struct DeficiencySummary: Identifiable {
    let id: String
    let title: String
    let result: String
    let comment: String?
}

struct SignSheetView: View {
    /// Sier hvilken del som signeres, så mannskapet vet hva de bekrefter
    var title: String = "Signer sjekkliste"
    let deficiencies: [DeficiencySummary]
    let onSign: (String) async -> Bool

    private let repo = AppDependencies.shared.repository

    @Environment(\.dismiss) private var dismiss
    @State private var users: [User] = []
    @State private var crewId = ""
    @State private var signing = false
    @State private var failed = false

    private var matchedUser: User? {
        users.first { $0.id == crewId.trimmingCharacters(in: .whitespaces) }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Sammendrag av kontroll") {
                    if deficiencies.isEmpty {
                        Label("Ingen avvik registrert", systemImage: "checkmark.circle")
                            .foregroundStyle(.green)
                    } else {
                        ForEach(deficiencies) { deficiency in
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(deficiency.title) – \(deficiency.result)")
                                    .font(.subheadline)
                                    .fontWeight(.medium)
                                if let comment = deficiency.comment {
                                    Text(comment)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }

                Section("Mannskaps-ID (navn hentes automatisk)") {
                    TextField("F.eks. 12345", text: $crewId)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()

                    if let user = matchedUser {
                        Label(user.name, systemImage: "person.fill.checkmark")
                            .foregroundStyle(.green)
                    } else if !crewId.isEmpty {
                        Label("Ukjent mannskaps-ID", systemImage: "person.fill.questionmark")
                            .foregroundStyle(.secondary)
                    }
                }

                Section {
                    Button {
                        Task {
                            signing = true
                            let success = await onSign(crewId.trimmingCharacters(in: .whitespaces))
                            signing = false
                            if success { dismiss() } else { failed = true }
                        }
                    } label: {
                        if signing {
                            ProgressView().frame(maxWidth: .infinity)
                        } else {
                            Label("Fullfør kontroll", systemImage: "paperplane.fill")
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                                .foregroundStyle(matchedUser != nil ? .white : .secondary)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(matchedUser == nil || signing)
                }
                .listRowBackground(Color.clear)
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt") { dismiss() }
                }
            }
            .alert("Kunne ikke signere", isPresented: $failed) {
                Button("OK", role: .cancel) {}
            }
        }
        .task {
            for await list in repo.users() {
                users = list
            }
        }
    }
}
