import SwiftUI
import SharedLogic

struct HistoryView: View {
    private let repo = AppDependencies.shared.repository

    @State private var runs: [GetRecentRuns] = []

    var body: some View {
        Group {
            if runs.isEmpty {
                ContentUnavailableView(
                    "Ingen tidligere sjekker",
                    systemImage: "clock.arrow.circlepath",
                    description: Text("Fullførte sjekklister vises her.")
                )
            } else {
                List(runs, id: \.id) { run in
                    NavigationLink {
                        RunDetailView(run: run)
                    } label: {
                        HistoryRow(run: run)
                    }
                }
            }
        }
        .navigationTitle("Historikk")
        .task {
            for await list in repo.recentRuns(limit: 50) {
                runs = list
            }
        }
    }
}

struct HistoryRow: View {
    let run: GetRecentRuns

    private var status: RunStatus? { RunStatus.companion.fromDb(value: run.status) }

    // En status appen ikke kjenner kan komme fra en nyere versjon via synk.
    // Den vises som ukjent i stedet for «Pågår», som ville antydet at
    // kontrollen kan tas opp igjen.
    private var statusText: String { status?.label ?? "Ukjent status" }

    private var statusColor: Color {
        switch status {
        case .completed: .green
        case .expired: .orange
        default: .secondary
        }
    }

    private var dateText: String {
        let millis = run.completedAt?.int64Value ?? run.createdAt
        let date = Date(timeIntervalSince1970: Double(millis) / 1000)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "nb_NO")
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    /// Sammendraget regnes ut i sharedLogic – regnestykket bak «åpne avvik»
    /// er ikke opplagt, og skal ikke finnes i to versjoner.
    private var deviation: DeviationSummary {
        RunSummaryKt.deviationSummary(
            total: run.deviationCount,
            resolved: run.resolvedCount,
            superseded: run.supersededCount
        )
    }

    @ViewBuilder
    private var deviationLabel: some View {
        let summary = deviation
        Label(summary.text, systemImage: deviationIcon(summary.outcome))
            .font(.caption)
            .fontWeight(summary.outcome == .noDeviations ? .regular : .medium)
            .foregroundStyle(deviationColor(summary.outcome))
    }

    private func deviationIcon(_ outcome: DeviationOutcome) -> String {
        switch outcome {
        case .open: "exclamationmark.triangle.fill"
        case .carriedOver: "arrow.triangle.2.circlepath"
        default: "checkmark.circle"
        }
    }

    private func deviationColor(_ outcome: DeviationOutcome) -> Color {
        switch outcome {
        case .open: Color.rkError
        case .carriedOver: .orange
        default: .green
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                Text(run.templateName)
                    .font(.body)
                    .fontWeight(.medium)
                Spacer()
                Text(statusText)
                    .font(.caption2)
                    .fontWeight(.medium)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(statusColor.opacity(0.15))
                    .foregroundStyle(statusColor)
                    .clipShape(Capsule())
            }

            Text("\(run.callSign) · \(dateText)")
                .font(.caption)
                .foregroundStyle(.secondary)

            deviationLabel

            // Er lista signert i to trinn, må begge signaturene fram – ellers
            // forsvinner hvem som faktisk kontrollerte bilen før vakta
            if let before = run.beforeSignedByName {
                Text("Før vakt: \(before)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if let name = run.signedByName {
                Text(run.beforeSignedByName != nil
                     ? "Etter vakt: \(name)"
                     : "Signert av \(name)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            // Mannskapet må kunne se at en melding ikke er delt med
            // de andre bilene ennå
            if run.hasUnsyncedChanges == 1 {
                Label(
                    "Ikke synkronisert – ligger bare på denne enheten",
                    systemImage: "icloud.slash"
                )
                .font(.caption)
                .foregroundStyle(.orange)
            }
        }
        .padding(.vertical, 4)
    }
}
