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
                    HistoryRow(run: run)
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

            if let name = run.signedByName {
                Text("Signert av \(name)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}
