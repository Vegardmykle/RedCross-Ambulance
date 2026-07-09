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

struct ChecklistRunView: View {
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

    private var allItems: [ChecklistItem] {
        items + bagItems.values.flatMap { $0 }
    }

    private var answeredCount: Int {
        allItems.filter { responses[$0.id] != nil }.count
    }

    private var allAnswered: Bool {
        !allItems.isEmpty && answeredCount == allItems.count
    }

    var body: some View {
        NavigationStack {
            List {
                progressSection

                Section("Utstyr") {
                    ForEach(sortedByAnswer(items), id: \.id) { item in
                        ChecklistItemRow(
                            item: item,
                            response: responses[item.id],
                            hasEarlierDeficiency: earlierDeficiencyItemIds.contains(item.id),
                            onAnswer: { choice, comment, reading in
                                await answer(item: item, choice: choice, comment: comment, reading: reading)
                            }
                        )
                    }
                }

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

                signSection
            }
            .navigationTitle(template?.name ?? "Sjekkliste")
            .sheet(isPresented: $showSignSheet) {
                SignSheetView(
                    deficiencies: deficiencies,
                    onSign: { userId in await complete(userId: userId) }
                )
            }
        }
        .task {
            for await list in repo.ambulances() {
                if selectedAmbulanceId.isEmpty, let first = list.first {
                    selectedAmbulanceId = first.id
                }
            }
        }
        .task {
            for await list in repo.topLevelTemplates() {
                template = list.first { $0.type == templateType }
            }
        }
        .task(id: "\(template?.id ?? "")|\(selectedAmbulanceId)") {
            guard let template, !selectedAmbulanceId.isEmpty else { return }
            await loadRun(templateId: template.id)
        }
        .task(id: template?.id ?? "") {
            guard let template else { return }
            for await list in repo.itemsFor(templateId: template.id) {
                items = list
            }
        }
        .task(id: template?.id ?? "") {
            guard let template else { return }
            for await list in repo.bagsFor(templateId: template.id) {
                bags = list
            }
        }
        .task(id: run?.id ?? "") {
            guard let run else { return }
            for await list in repo.responsesForRun(runId: run.id) {
                responses = Dictionary(uniqueKeysWithValues: list.map { ($0.itemId, $0) })
            }
        }
        .task(id: run?.id ?? "") {
            guard let run else { return }
            for await ids in repo.itemIdsWithOpenDeficiencies(
                ambulanceId: run.ambulanceId,
                excludeRunId: run.id
            ) {
                earlierDeficiencyItemIds = Set(ids)
            }
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

    private var signSection: some View {
        Section {
            Button {
                showSignSheet = true
            } label: {
                Label("Signer og fullfør", systemImage: "signature")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!allAnswered)

            if !allAnswered {
                Text("Du må svare på alle punkter før du kan signere.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .listRowBackground(Color.clear)
    }

    private var deficiencies: [DeficiencySummary] {
        allItems.compactMap { item in
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

    /// Besvarte «Ja»-punkter legger seg nederst; ubesvarte og avvik blir stående øverst.
    private func sortedByAnswer(_ list: [ChecklistItem]) -> [ChecklistItem] {
        list.sorted { a, b in
            let aDone = responses[a.id]?.result == "JA"
            let bDone = responses[b.id]?.result == "JA"
            if aDone != bDone { return !aDone }
            return a.sortOrder < b.sortOrder
        }
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

    private var sortedItems: [ChecklistItem] {
        items.sorted { a, b in
            let aDone = responses[a.id]?.result == "JA"
            let bDone = responses[b.id]?.result == "JA"
            if aDone != bDone { return !aDone }
            return a.sortOrder < b.sortOrder
        }
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
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(matchedUser == nil || signing)
                }
                .listRowBackground(Color.clear)
            }
            .navigationTitle("Signering")
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
