import SwiftUI
import UniformTypeIdentifiers
import SharedLogic

struct ResourcesView: View {
    private let repo = AppDependencies.shared.repository
    private let storage = AppDependencies.shared.documentStorage

    @Environment(\.openURL) private var openURL

    @State private var documents: [Document] = []
    @State private var links: [AppLink] = []
    @State private var users: [User] = []
    @State private var showAddUser = false
    @State private var newUserId = ""
    @State private var newUserName = ""
    @State private var newUserRole = "Mannskap"
    @State private var deleteUserTarget: User?
    @State private var ambulances: [Ambulance] = []
    @State private var showAddVehicle = false
    @State private var newVehicleCallSign = ""
    @State private var newVehicleReg = ""
    @State private var deleteVehicleTarget: Ambulance?
    @State private var showImporter = false
    @State private var editingLink: AppLink?
    @State private var showAddLink = false
    @State private var deleteDocTarget: Document?
    @State private var deleteLinkTarget: AppLink?
    @State private var linkTitle = ""
    @State private var linkUrl = ""
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            List {
                documentsSection
                linksSection
                crewSection
                versionSection
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
            .confirmationDialog(
                "Slette «\(deleteDocTarget?.title ?? "")»?",
                isPresented: Binding(
                    get: { deleteDocTarget != nil },
                    set: { if !$0 { deleteDocTarget = nil } }
                ),
                titleVisibility: .visible
            ) {
                Button("Slett PDF", role: .destructive) {
                    if let document = deleteDocTarget { performDeleteDocument(document) }
                    deleteDocTarget = nil
                }
                Button("Avbryt", role: .cancel) { deleteDocTarget = nil }
            } message: {
                Text("PDF-en fjernes fra appen på denne enheten.")
            }
            .confirmationDialog(
                "Slette «\(deleteLinkTarget?.title ?? "")»?",
                isPresented: Binding(
                    get: { deleteLinkTarget != nil },
                    set: { if !$0 { deleteLinkTarget = nil } }
                ),
                titleVisibility: .visible
            ) {
                Button("Slett lenke", role: .destructive) {
                    if let link = deleteLinkTarget { performDeleteLink(link) }
                    deleteLinkTarget = nil
                }
                Button("Avbryt", role: .cancel) { deleteLinkTarget = nil }
            } message: {
                Text("Lenken fjernes for alle enheter.")
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
        .task {
            for await list in repo.users() {
                users = list
            }
        }
        .task {
            for await list in repo.ambulances() {
                ambulances = list
            }
        }
    }

    // MARK: Administrasjon (mannskap + kjøretøy, sammenleggbart)

    /// Versjon og miljø. Gjør det mulig for testere å oppgi nøyaktig hvilken
    /// versjon en feil gjelder, og skiller test fra produksjon.
    private var versionSection: some View {
        Section {
            HStack {
                #if DEBUG
                Text("TESTVERSJON")
                    .font(.caption2)
                    .fontWeight(.medium)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color.orange.opacity(0.15))
                    .foregroundStyle(.orange)
                    .clipShape(Capsule())
                #endif
                Text("Versjon \(Self.appVersion)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
            }
        }
    }

    private static var appVersion: String {
        let info = Bundle.main.infoDictionary
        let name = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(name) (\(build))"
    }

    private var crewSection: some View {
        Section {
            DisclosureGroup {
                ForEach(users, id: \.id) { user in
                    HStack {
                        Image(systemName: "person.fill")
                            .foregroundStyle(Color.rkPrimary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(user.name)
                            Text("ID \(user.id) · \(user.role)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button {
                            deleteUserTarget = user
                        } label: {
                            Image(systemName: "trash")
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("Slett \(user.name)")
                    }
                    .frame(minHeight: 44)
                }
                Button {
                    newUserId = ""
                    newUserName = ""
                    newUserRole = "Mannskap"
                    showAddUser = true
                } label: {
                    Label("Legg til mannskap", systemImage: "person.badge.plus")
                        .frame(minHeight: 44)
                }
            } label: {
                Label("Mannskap (\(users.count))", systemImage: "person.2")
            }

            DisclosureGroup {
                ForEach(ambulances, id: \.id) { ambulance in
                    HStack {
                        Image(systemName: "cross.case.fill")
                            .foregroundStyle(Color.rkPrimary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(ambulance.callSign)
                            if !ambulance.registrationNumber.isEmpty {
                                Text(ambulance.registrationNumber)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        Button {
                            deleteVehicleTarget = ambulance
                        } label: {
                            Image(systemName: "trash")
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("Slett \(ambulance.callSign)")
                    }
                    .frame(minHeight: 44)
                }
                Button {
                    newVehicleCallSign = ""
                    newVehicleReg = ""
                    showAddVehicle = true
                } label: {
                    Label("Legg til kjøretøy", systemImage: "plus")
                        .frame(minHeight: 44)
                }
            } label: {
                Label("Kjøretøy (\(ambulances.count))", systemImage: "cross.case")
            }
        } header: {
            Text("Administrasjon")
        } footer: {
            Text("Endringer gjelder alle enheter.")
        }
        .alert("Nytt kjøretøy", isPresented: $showAddVehicle) {
            TextField("Kallesignal, f.eks. Ambulanse 2", text: $newVehicleCallSign)
            TextField("Registreringsnummer (valgfritt)", text: $newVehicleReg)
            Button("Avbryt", role: .cancel) {}
            Button("Legg til") { addVehicle() }
        }
        .confirmationDialog(
            "Slette \(deleteVehicleTarget?.callSign ?? "")?",
            isPresented: Binding(
                get: { deleteVehicleTarget != nil },
                set: { if !$0 { deleteVehicleTarget = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Slett", role: .destructive) {
                if let vehicle = deleteVehicleTarget {
                    Task { _ = try? await repo.deleteAmbulance(id: vehicle.id) }
                }
                deleteVehicleTarget = nil
            }
            Button("Avbryt", role: .cancel) { deleteVehicleTarget = nil }
        } message: {
            Text("Historikken for kjøretøyet beholdes.")
        }
        .alert("Nytt mannskap", isPresented: $showAddUser) {
            TextField("Mannskaps-ID, f.eks. 12345", text: $newUserId)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
            TextField("Fullt navn", text: $newUserName)
            TextField("Rolle", text: $newUserRole)
            Button("Avbryt", role: .cancel) {}
            Button("Legg til") { addUser() }
        }
        .confirmationDialog(
            "Slette \(deleteUserTarget?.name ?? "")?",
            isPresented: Binding(
                get: { deleteUserTarget != nil },
                set: { if !$0 { deleteUserTarget = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Slett", role: .destructive) {
                if let user = deleteUserTarget {
                    Task { _ = try? await repo.deleteUser(id: user.id) }
                }
                deleteUserTarget = nil
            }
            Button("Avbryt", role: .cancel) { deleteUserTarget = nil }
        } message: {
            Text("Personen kan ikke lenger signere. Historikk beholder navnet.")
        }
    }

    private func addVehicle() {
        let callSign = newVehicleCallSign.trimmingCharacters(in: .whitespaces)
        let reg = newVehicleReg.trimmingCharacters(in: .whitespaces)
        guard !callSign.isEmpty else { return }
        Task { _ = try? await repo.addAmbulance(callSign: callSign, registrationNumber: reg) }
    }

    private func addUser() {
        let id = newUserId.trimmingCharacters(in: .whitespaces)
        let name = newUserName.trimmingCharacters(in: .whitespaces)
        let role = newUserRole.trimmingCharacters(in: .whitespaces)
        guard !id.isEmpty, !name.isEmpty else { return }
        guard !users.contains(where: { $0.id == id }) else {
            errorMessage = "Mannskaps-ID \(id) er allerede i bruk."
            return
        }
        Task {
            do {
                try await repo.addUser(id: id, name: name, role: role.isEmpty ? "Mannskap" : role)
            } catch {
                errorMessage = "Kunne ikke legge til mannskap."
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
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        deleteDocTarget = document
                    } label: {
                        Label("Slett", systemImage: "trash")
                    }
                }
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
                    Button(role: .destructive) {
                        deleteLinkTarget = link
                    } label: {
                        Label("Slett", systemImage: "trash")
                    }
                    Button {
                        startEditing(link)
                    } label: {
                        Label("Rediger", systemImage: "pencil")
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

    private func performDeleteDocument(_ document: Document) {
        Task {
            storage.delete(path: document.uri)
            _ = try? await repo.deleteDocument(id: document.id)
        }
    }

    private func performDeleteLink(_ link: AppLink) {
        Task { _ = try? await repo.deleteLink(id: link.id) }
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
