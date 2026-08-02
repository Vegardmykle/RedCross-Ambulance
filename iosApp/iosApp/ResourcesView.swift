import SwiftUI
import UniformTypeIdentifiers
import SharedLogic

struct ResourcesView: View {
    private let repo = AppDependencies.shared.repository
    private let storage = AppDependencies.shared.documentStorage

    @Environment(\.openURL) private var openURL

    @State private var documents: [Document] = []
    @State private var links: [AppLink] = []
    @State private var showImporter = false
    @State private var editingLink: AppLink?
    @State private var showAddLink = false
    @State private var linkTitle = ""
    @State private var linkUrl = ""
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            List {
                documentsSection
                linksSection
            }
            .navigationTitle("Ressurser og skjema")
            .fileImporter(
                isPresented: $showImporter,
                allowedContentTypes: [.pdf]
            ) { result in
                importPdf(result)
            }
            .alert("Rediger lenke", isPresented: Binding(
                get: { editingLink != nil },
                set: { if !$0 { editingLink = nil } }
            )) {
                linkFormFields
                Button("Avbryt", role: .cancel) { editingLink = nil }
                Button("Lagre") { saveEditedLink() }
            }
            .alert("Ny lenke", isPresented: $showAddLink) {
                linkFormFields
                Button("Avbryt", role: .cancel) {}
                Button("Legg til") { saveNewLink() }
            }
            .alert("Feil", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
        .task {
            for await list in repo.documents() {
                documents = list
            }
        }
        .task {
            for await list in repo.links() {
                links = list
            }
        }
    }

    // MARK: Interne instrukser (PDF)

    private var documentsSection: some View {
        Section {
            ForEach(documents, id: \.id) { document in
                Button {
                    openDocument(document)
                } label: {
                    HStack {
                        Image(systemName: "doc.richtext")
                            .foregroundStyle(Color.rkPrimary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(document.title)
                                .foregroundStyle(.primary)
                            if !storage.exists(path: document.uri) {
                                Text("Fil mangler på enheten")
                                    .font(.caption)
                                    .foregroundStyle(.orange)
                            }
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .frame(minHeight: 44)
                }
            }
            .onDelete { offsets in
                deleteDocuments(at: offsets)
            }

            Button {
                showImporter = true
            } label: {
                Label("Legg til PDF fra Filer", systemImage: "plus")
                    .frame(minHeight: 44)
            }
        } header: {
            Text("Interne instrukser")
        } footer: {
            Text("PDF-ene lagres i appen og er tilgjengelige uten dekning.")
        }
    }

    // MARK: Registrering og skjema

    private var linksSection: some View {
        Section("Registrering og skjema") {
            ForEach(links, id: \.id) { link in
                Button {
                    openLink(link)
                } label: {
                    HStack {
                        Image(systemName: "link")
                            .foregroundStyle(Color.rkPrimary)
                        Text(link.title)
                            .foregroundStyle(.primary)
                        Spacer()
                        if link.url.isEmpty {
                            Text("URL ikke satt")
                                .font(.caption)
                                .foregroundStyle(.orange)
                        } else {
                            Image(systemName: "arrow.up.right.square")
                                .foregroundStyle(.secondary)
                        }
                    }
                    .frame(minHeight: 44)
                }
                .swipeActions(edge: .trailing) {
                    Button("Rediger") {
                        startEditing(link)
                    }
                    .tint(.rkPrimary)
                }
            }

            Button {
                linkTitle = ""
                linkUrl = ""
                showAddLink = true
            } label: {
                Label("Legg til lenke", systemImage: "plus")
                    .frame(minHeight: 44)
            }
        }
    }

    // MARK: Handlinger

    @ViewBuilder
    private var linkFormFields: some View {
        TextField("Tittel", text: $linkTitle)
        TextField("https://…", text: $linkUrl)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .keyboardType(.URL)
    }

    private func openDocument(_ document: Document) {
        if !storage.openPdf(path: document.uri) {
            errorMessage = "Fant ikke PDF-en på enheten. Slett raden og legg den til på nytt."
        }
    }

    private func deleteDocuments(at offsets: IndexSet) {
        let targets = offsets.map { documents[$0] }
        Task {
            for document in targets {
                storage.delete(path: document.uri)
                _ = try? await repo.deleteDocument(id: document.id)
            }
        }
    }

    private func importPdf(_ result: Result<URL, Error>) {
        guard case .success(let url) = result else { return }
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        do {
            let fileManager = FileManager.default
            let dir = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("documents", isDirectory: true)
            try fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
            let destination = dir.appendingPathComponent(url.lastPathComponent)
            if fileManager.fileExists(atPath: destination.path) {
                try fileManager.removeItem(at: destination)
            }
            try fileManager.copyItem(at: url, to: destination)
            let title = url.deletingPathExtension().lastPathComponent
            Task {
                _ = try? await repo.addDocument(title: title, uri: destination.path, sortOrder: 0)
            }
        } catch {
            errorMessage = "Kunne ikke importere PDF-en."
        }
    }

    private func openLink(_ link: AppLink) {
        if link.url.isEmpty {
            startEditing(link)
        } else if let url = URL(string: Self.normalizeUrl(link.url)), url.scheme != nil {
            openURL(url)
        } else {
            errorMessage = "Ugyldig URL – rediger lenken."
        }
    }

    /// Legger på https:// hvis skjema mangler – ellers åpner ikke systemet lenken.
    static func normalizeUrl(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return trimmed }
        if trimmed.contains("://") { return trimmed }
        return "https://\(trimmed)"
    }

    private func startEditing(_ link: AppLink) {
        linkTitle = link.title
        linkUrl = link.url
        editingLink = link
    }

    private func saveEditedLink() {
        guard let link = editingLink else { return }
        let title = linkTitle.trimmingCharacters(in: .whitespaces)
        let url = Self.normalizeUrl(linkUrl)
        editingLink = nil
        guard !title.isEmpty else { return }
        Task { _ = try? await repo.updateLink(id: link.id, title: title, url: url) }
    }

    private func saveNewLink() {
        let title = linkTitle.trimmingCharacters(in: .whitespaces)
        let url = Self.normalizeUrl(linkUrl)
        guard !title.isEmpty else { return }
        Task { _ = try? await repo.addLink(title: title, url: url, sortOrder: Int64(links.count)) }
    }
}
