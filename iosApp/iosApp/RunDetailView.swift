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

    static func format(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(millis) / 1000)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "nb_NO")
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

struct ResponseDetailRow: View {
    let response: GetResponsesWithItemsForRun

    private var choice: AnswerChoice? { AnswerChoice(rawValue: response.result) }

    private var isResolved: Bool { response.resolved != 0 }

    private var isSuperseded: Bool { response.resolvedVia == "SUPERSEDED" }

    private var badgeText: String {
        if isSuperseded { return "Videreført" }
        return isResolved ? "Løst" : (choice?.label ?? response.result)
    }

    private var badgeColor: Color {
        if isSuperseded { return .orange }
        return isResolved ? .green : (choice?.color ?? .secondary)
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
                HStack(spacing: 4) {
                    Image(systemName: isSuperseded ? "arrow.triangle.2.circlepath" : "checkmark.circle.fill")
                        .foregroundStyle(isSuperseded ? .orange : .green)
                    Text(resolvedText(at: resolvedAt.int64Value))
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    private func resolvedText(at millis: Int64) -> String {
        let was = "Var \(choice?.label.lowercased() ?? "avvik")"
        let how = switch response.resolvedVia {
        case "RECHECK": "OK ved senere kontroll"
        case "SUPERSEDED": "videreført til senere kontroll"
        default: response.resolvedByName.map { "løst av \($0)" } ?? "løst manuelt"
        }
        var text = "\(was) · \(how) \(RunDetailView.format(millis))"
        if let newReading = response.resolvedReading, !newReading.isEmpty {
            text += " · ny verdi \(newReading) \(response.unit ?? "")"
        }
        return text
    }
}
