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

    private var statusText: String {
        switch run.status {
        case "COMPLETED": "Signert"
        case "EXPIRED": "Utløpt – ikke signert"
        default: "Pågår"
        }
    }

    private var statusColor: Color {
        switch run.status {
        case "COMPLETED": .green
        case "EXPIRED": .orange
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

    @ViewBuilder
    private var deviationLabel: some View {
        let total = run.deviationCount
        let resolved = run.resolvedCount
        let superseded = run.supersededCount
        let open = total - resolved - superseded

        if total == 0 {
            Label("Ingen avvik", systemImage: "checkmark.circle")
                .font(.caption)
                .foregroundStyle(.green)
        } else if open > 0 {
            Label(openLabelText(open: open, resolved: resolved, superseded: superseded), systemImage: "exclamationmark.triangle.fill")
                .font(.caption)
                .fontWeight(.medium)
                .foregroundStyle(Color.rkError)
        } else if superseded > 0 {
            let text = resolved > 0
                ? "\(superseded) videreført · \(resolved) løst"
                : (superseded == 1 ? "1 avvik videreført" : "\(superseded) avvik videreført")
            Label(text, systemImage: "arrow.triangle.2.circlepath")
                .font(.caption)
                .fontWeight(.medium)
                .foregroundStyle(.orange)
        } else {
            Label(total == 1 ? "Avviket er løst" : "Alle \(total) avvik løst", systemImage: "checkmark.circle")
                .font(.caption)
                .fontWeight(.medium)
                .foregroundStyle(.green)
        }
    }

    private func openLabelText(open: Int64, resolved: Int64, superseded: Int64) -> String {
        var parts = [open == 1 ? "1 åpent avvik" : "\(open) åpne avvik"]
        if resolved > 0 { parts.append("\(resolved) løst") }
        if superseded > 0 { parts.append("\(superseded) videreført") }
        return parts.joined(separator: " · ")
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

            if let name = run.signedByName {
                Text("Signert av \(name)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}
