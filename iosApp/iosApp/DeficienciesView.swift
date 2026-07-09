import SwiftUI
import SharedLogic

struct DeficienciesView: View {
    private let repo = AppDependencies.shared.repository

    @State private var deficiencies: [GetOpenDeficiencies] = []
    @State private var resolveCandidate: GetOpenDeficiencies?
    @State private var valueCandidate: GetOpenDeficiencies?
    @State private var newValueText = ""
    @State private var errorMessage: String?

    private var isResolvePresented: Binding<Bool> {
        Binding(
            get: { resolveCandidate != nil },
            set: { if !$0 { resolveCandidate = nil } }
        )
    }

    private var isValuePresented: Binding<Bool> {
        Binding(
            get: { valueCandidate != nil },
            set: { if !$0 { valueCandidate = nil } }
        )
    }

    private var isErrorPresented: Binding<Bool> {
        Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )
    }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Mangler")
        }
        .task {
            for await list in repo.openDeficiencies() {
                deficiencies = list
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        mainList
            .confirmationDialog(
                "Marker som løst?",
                isPresented: isResolvePresented,
                titleVisibility: .visible
            ) {
                resolveDialogButtons
            } message: {
                resolveDialogMessage
            }
            .alert("Ny avlest verdi", isPresented: isValuePresented) {
                valueAlertContent
            } message: {
                valueAlertMessage
            }
            .alert("Kunne ikke løse avviket", isPresented: isErrorPresented) {
                Button("OK", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
    }

    @ViewBuilder
    private var mainList: some View {
        if deficiencies.isEmpty {
            ContentUnavailableView(
                "Ingen åpne avvik",
                systemImage: "checkmark.seal",
                description: Text("Alt utstyr er meldt i orden.")
            )
        } else {
            List(deficiencies, id: \.id) { deficiency in
                DeficiencyRow(deficiency: deficiency) {
                    startResolving(deficiency)
                }
            }
        }
    }

    @ViewBuilder
    private var resolveDialogButtons: some View {
        Button("Marker som løst") {
            if let deficiency = resolveCandidate {
                Task { try? await repo.resolveDeficiency(responseId: deficiency.id, newReading: nil) }
            }
            resolveCandidate = nil
        }
        Button("Avbryt", role: .cancel) { resolveCandidate = nil }
    }

    @ViewBuilder
    private var resolveDialogMessage: some View {
        if let deficiency = resolveCandidate {
            Text("«\(deficiency.itemTitle)» fjernes fra oversikten.")
        }
    }

    @ViewBuilder
    private var valueAlertContent: some View {
        TextField("F.eks. 200", text: $newValueText)
            .keyboardType(.decimalPad)
            .onChange(of: newValueText) { _, newValue in
                let filtered = Self.filterNumeric(newValue)
                if filtered != newValue { newValueText = filtered }
            }
        Button("Avbryt", role: .cancel) { valueCandidate = nil }
        Button("Lagre") { saveNewValue() }
    }

    @ViewBuilder
    private var valueAlertMessage: some View {
        if let deficiency = valueCandidate {
            Text("Skriv inn ny verdi (\(deficiency.unit ?? ""))\(limitsText(deficiency)).")
        }
    }

    private func startResolving(_ deficiency: GetOpenDeficiencies) {
        if deficiency.requiresValue != 0 {
            newValueText = ""
            valueCandidate = deficiency
        } else {
            resolveCandidate = deficiency
        }
    }

    private func saveNewValue() {
        guard let deficiency = valueCandidate else { return }
        var value = newValueText.trimmingCharacters(in: .whitespaces)
        if value.hasSuffix(".") { value = String(value.dropLast()) }
        if value.hasPrefix(".") { value = "0" + value }
        valueCandidate = nil
        let limits = limitsText(deficiency)
        Task {
            do {
                try await repo.resolveDeficiency(responseId: deficiency.id, newReading: value)
            } catch {
                errorMessage = "Sjekk at verdien er et tall innenfor grensene\(limits)."
            }
        }
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

    private func limitsText(_ deficiency: GetOpenDeficiencies) -> String {
        var parts: [String] = []
        if let min = deficiency.minValue { parts.append("min \(formatted(min.doubleValue))") }
        if let max = deficiency.maxValue { parts.append("maks \(formatted(max.doubleValue))") }
        return parts.isEmpty ? "" : " (\(parts.joined(separator: ", ")))"
    }

    private func formatted(_ value: Double) -> String {
        value.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int(value))
            : String(value)
    }
}

struct DeficiencyRow: View {
    let deficiency: GetOpenDeficiencies
    let onResolve: () -> Void

    private var badgeText: String {
        AnswerChoice(rawValue: deficiency.result)?.label ?? deficiency.result
    }

    private var badgeColor: Color {
        deficiency.result == "ODELAGT" ? .rkError : .orange
    }

    private var dateText: String {
        let date = Date(timeIntervalSince1970: Double(deficiency.checkedAt) / 1000)
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

            if let name = deficiency.signedByName {
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
