import SwiftUI
import SharedLogic

// OpenDeficiency har allerede `id: String` – gir Identifiable for .sheet(item:)
extension OpenDeficiency: Identifiable {}

struct DeficienciesView: View {
    private let repo = AppDependencies.shared.repository

    @State private var deficiencies: [OpenDeficiency] = []
    @State private var resolveTarget: OpenDeficiency?

    var body: some View {
        NavigationStack {
            Group {
                if deficiencies.isEmpty {
                    ContentUnavailableView(
                        "Ingen åpne avvik",
                        systemImage: "checkmark.seal",
                        description: Text("Alt utstyr er meldt i orden.")
                    )
                } else {
                    List(deficiencies, id: \.id) { deficiency in
                        DeficiencyRow(deficiency: deficiency) {
                            resolveTarget = deficiency
                        }
                    }
                }
            }
            .navigationTitle("Mangler")
            .sheet(item: $resolveTarget) { deficiency in
                ResolveSheetView(deficiency: deficiency)
            }
        }
        .task {
            for await list in repo.openDeficiencies() {
                deficiencies = list
            }
        }
    }
}

/// Signering av manuell løsing: mannskaps-ID (navn hentes automatisk),
/// og ny avlest verdi hvis punktet krever måling.
struct ResolveSheetView: View {
    let deficiency: OpenDeficiency

    private let repo = AppDependencies.shared.repository

    @Environment(\.dismiss) private var dismiss
    @State private var users: [User] = []
    @State private var crewId = ""
    @State private var valueText = ""
    @State private var errorMessage: String?
    @State private var saving = false

    private var requiresValue: Bool { deficiency.requiresValue != 0 }

    private var matchedUser: User? {
        users.first { $0.id == crewId.trimmingCharacters(in: .whitespaces) }
    }

    private var normalizedValue: String {
        var value = valueText.trimmingCharacters(in: .whitespaces)
        if value.hasSuffix(".") { value = String(value.dropLast()) }
        if value.hasPrefix(".") { value = "0" + value }
        return value
    }

    private var canSave: Bool {
        matchedUser != nil && (!requiresValue || Double(normalizedValue) != nil) && !saving
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Avvik") {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(deficiency.itemTitle)
                            .fontWeight(.medium)
                        if let comment = deficiency.comment, !comment.isEmpty {
                            Text(comment)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Text("\(deficiency.listName) · \(deficiency.callSign)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                if requiresValue {
                    Section("Ny avlest verdi (\(deficiency.unit ?? ""))\(limitsText)") {
                        TextField("F.eks. 200", text: $valueText)
                            .keyboardType(.decimalPad)
                            .onChange(of: valueText) { _, newValue in
                                let filtered = Self.filterNumeric(newValue)
                                if filtered != newValue { valueText = filtered }
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
                        save()
                    } label: {
                        if saving {
                            ProgressView().frame(maxWidth: .infinity)
                        } else {
                            Label("Marker som løst", systemImage: "checkmark.circle")
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity, minHeight: 44)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!canSave)
                }
                .listRowBackground(Color.clear)
            }
            .navigationTitle("Løs avvik")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt") { dismiss() }
                }
            }
            .alert("Kunne ikke løse avviket", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
        .task {
            for await list in repo.users() {
                users = list
            }
        }
    }

    private func save() {
        let userId = crewId.trimmingCharacters(in: .whitespaces)
        let reading = requiresValue ? normalizedValue : nil
        saving = true
        Task {
            do {
                try await repo.resolveDeficiency(
                    responseId: deficiency.id,
                    userId: userId,
                    newReading: reading
                )
                saving = false
                Task { try? await AppDependencies.shared.syncService.syncAll() }
                dismiss()
            } catch {
                saving = false
                errorMessage = "Sjekk at verdien er et tall innenfor grensene\(limitsText)."
            }
        }
    }

    private var limitsText: String {
        var parts: [String] = []
        if let min = deficiency.minValue { parts.append("min \(Self.formatted(min.doubleValue))") }
        if let max = deficiency.maxValue { parts.append("maks \(Self.formatted(max.doubleValue))") }
        return parts.isEmpty ? "" : " (\(parts.joined(separator: ", ")))"
    }

    private static func filterNumeric(_ input: String) -> String {
        var filtered = input.replacingOccurrences(of: ",", with: ".")
            .filter { $0.isNumber || $0 == "." }
        if let first = filtered.firstIndex(of: ".") {
            let afterFirst = filtered.index(after: first)
            filtered = String(filtered[..<afterFirst])
                + filtered[afterFirst...].filter { $0.isNumber }
        }
        return filtered
    }

    private static func formatted(_ value: Double) -> String {
        value.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int(value))
            : String(value)
    }
}

struct DeficiencyRow: View {
    let deficiency: OpenDeficiency
    let onResolve: () -> Void

    private var badgeText: String {
        AnswerChoice(rawValue: deficiency.result)?.label ?? deficiency.result
    }

    private var badgeColor: Color {
        deficiency.result == "ODELAGT" ? .rkError : .orange
    }

    private var dateText: String {
        Self.shortDate(deficiency.checkedAt)
    }

    static func shortDate(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(millis) / 1000)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "nb_NO")
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                Text(deficiency.itemTitle)
                    .font(.body)
                    .fontWeight(.medium)
                Spacer()
                Text(badgeText)
                    .font(.caption2)
                    .fontWeight(.medium)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(badgeColor.opacity(0.15))
                    .foregroundStyle(badgeColor)
                    .clipShape(Capsule())
            }

            if let comment = deficiency.comment, !comment.isEmpty {
                Text(comment)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Text("\(deficiency.listName) · \(deficiency.callSign) · \(dateText)")
                .font(.caption2)
                .foregroundStyle(.secondary)

            if let firstAt = deficiency.firstReportedAt {
                let date = Self.shortDate(firstAt.int64Value)
                let by = deficiency.firstReportedByName.map { " av \($0)" } ?? ""
                Label("Videreført · først meldt \(date)\(by)", systemImage: "arrow.triangle.2.circlepath")
                    .font(.caption2)
                    .fontWeight(.medium)
                    .foregroundStyle(.orange)
            } else if let name = deficiency.signedByName {
                Text("Meldt av \(name)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            Button(action: onResolve) {
                Label("Marker som løst", systemImage: "checkmark.circle")
                    .font(.subheadline)
                    .frame(minHeight: 44)
            }
            .buttonStyle(.borderless)
            .tint(.rkPrimary)
        }
        .padding(.vertical, 4)
    }
}
