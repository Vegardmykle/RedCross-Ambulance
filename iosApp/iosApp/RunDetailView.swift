import SwiftUI
import SharedLogic

struct RunDetailView: View {
    let run: GetRecentRuns

    private let repo = AppDependencies.shared.repository

    @State private var responses: [GetResponsesWithItemsForRun] = []

    private var groupedByList: [(listName: String, rows: [GetResponsesWithItemsForRun])] {
        var order: [String] = []
        var groups: [String: [GetResponsesWithItemsForRun]] = [:]
        for response in responses {
            if groups[response.listName] == nil { order.append(response.listName) }
            groups[response.listName, default: []].append(response)
        }
        return order.map { ($0, groups[$0] ?? []) }
    }

    var body: some View {
        List {
            Section {
                LabeledContent("Ambulanse", value: run.callSign)
                LabeledContent("Dato", value: Self.format(run.completedAt?.int64Value ?? run.createdAt))
                if let name = run.signedByName {
                    LabeledContent("Signert av", value: name)
                }
                if let comment = run.comment, !comment.isEmpty {
                    LabeledContent("Kommentar", value: comment)
                }
            }

            ForEach(groupedByList, id: \.listName) { group in
                Section(group.listName) {
                    ForEach(group.rows, id: \.id) { response in
                        ResponseDetailRow(response: response)
                    }
                }
            }
        }
        .navigationTitle(run.templateName)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            for await list in repo.responsesWithItems(runId: run.id) {
                responses = list
            }
        }
    }

    static func format(_ millis: Int64) -> String { AppDate.format(millis) }
}

struct ResponseDetailRow: View {
    let response: GetResponsesWithItemsForRun

    private var choice: AnswerChoice? { AnswerChoice(rawValue: response.result) }

    private var isResolved: Bool { response.resolved != 0 }

    /// Merkelappen utledes i sharedLogic: videreført sjekkes før løst, siden
    /// begge har resolved = 1 i databasen men bare den ene er rettet.
    private var badge: ResponseBadge { RunSummaryKt.responseBadge(response: response) }

    private var badgeText: String { badge.text }

    private var badgeColor: Color {
        switch badge.outcome {
        case .carriedOver: .orange
        case .resolved, .ok: .green
        case .deviation: choice?.color ?? .secondary
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                Text(response.itemTitle)
                    .font(.subheadline)
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

            if let reading = response.reading, !reading.isEmpty {
                Text("Avlest: \(reading) \(response.unit ?? "")")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if let comment = response.comment, !comment.isEmpty {
                Text(comment)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if isResolved, let resolvedAt = response.resolvedAt {
                let carriedOver = badge.outcome == .carriedOver
                HStack(spacing: 4) {
                    Image(systemName: carriedOver ? "arrow.triangle.2.circlepath" : "checkmark.circle.fill")
                        .foregroundStyle(carriedOver ? .orange : .green)
                    Text(RunSummaryKt.resolutionText(
                        response: response,
                        resolvedAtText: RunDetailView.format(resolvedAt.int64Value)
                    ))
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

}
