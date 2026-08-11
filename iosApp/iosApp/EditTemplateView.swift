import SwiftUI
import SharedLogic

/// Redigering av en sjekkliste: punkter og sekker, med omsortering,
/// sletting og grenseverdier. Endringer lagres umiddelbart.
struct EditTemplateView: View {
    let template: ChecklistTemplate

    private let repo = AppDependencies.shared.repository

    @State private var bags: [ChecklistTemplate] = []
    @State private var showNewBagAlert = false
    @State private var newBagName = ""
    @State private var editMode: EditMode = .inactive

    var body: some View {
        List {
            EditItemsSection(template: template, isBag: false)

            ForEach(bags, id: \.id) { bag in
                EditItemsSection(template: bag, isBag: true)
            }

            Section {
                Button {
                    newBagName = ""
                    showNewBagAlert = true
                } label: {
                    Label("Legg til sekk/taske", systemImage: "plus")
                        .frame(minHeight: 44)
                }
            }
        }
        .navigationTitle("Rediger: \(template.name)")
        .navigationBarTitleDisplayMode(.inline)
        .environment(\.editMode, $editMode)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(editMode == .active ? "Ferdig" : "Endre rekkefølge") {
                    withAnimation {
                        editMode = editMode == .active ? .inactive : .active
                    }
                }
            }
        }
        .alert("Ny sekk/taske", isPresented: $showNewBagAlert) {
            TextField("Navn, f.eks. Barnetaske", text: $newBagName)
            Button("Avbryt", role: .cancel) {}
            Button("Legg til") {
                let name = newBagName.trimmingCharacters(in: .whitespaces)
                guard !name.isEmpty else { return }
                Task {
                    _ = try? await repo.createTemplate(name: name, type: .bag, parentId: template.id)
                }
            }
        }
        .task {
            for await list in repo.bagsFor(templateId: template.id) {
                bags = list
            }
        }
    }
}

struct EditItemsSection: View {
    let template: ChecklistTemplate
    let isBag: Bool

    private let repo = AppDependencies.shared.repository

    @State private var items: [ChecklistItem] = []
    @State private var editingItem: ChecklistItem?
    @State private var showAddForm = false
    @State private var showRenameAlert = false
    @State private var renameText = ""
    @State private var showDeleteConfirm = false
    @State private var deleteItemTarget: ChecklistItem?

    var body: some View {
        Section {
            ForEach(items, id: \.id) { item in
                HStack(spacing: 0) {
                    Button {
                        editingItem = item
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title)
                                .foregroundStyle(.primary)
                            if let subtitle = subtitle(for: item) {
                                Text(subtitle)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    }
                    .buttonStyle(.borderless)
                    Button {
                        deleteItemTarget = item
                    } label: {
                        Image(systemName: "trash")
                            .foregroundStyle(Color.rkError)
                            .frame(minWidth: 44, minHeight: 44)
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel("Slett \(item.title)")
                }
            }
            .onMove { from, to in
                var reordered = items
                reordered.move(fromOffsets: from, toOffset: to)
                items = reordered
                let ids = reordered.map(\.id)
                Task { try? await repo.reorderItems(itemIds: ids) }
            }
            .onDelete { offsets in
                if let first = offsets.first {
                    deleteItemTarget = items[first]
                }
            }

            Button {
                showAddForm = true
            } label: {
                Label("Legg til punkt", systemImage: "plus")
                    .frame(minHeight: 44)
            }
        } header: {
            HStack {
                Label(template.name, systemImage: isBag ? "backpack" : "list.bullet")
                Spacer()
                if isBag {
                    Menu {
                        Button("Endre navn") {
                            renameText = template.name
                            showRenameAlert = true
                        }
                        Button("Slett sekk", role: .destructive) {
                            showDeleteConfirm = true
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
        }
        .sheet(isPresented: $showAddForm) {
            ItemFormSheet(title: "Nytt punkt") { form in
                _ = try await repo.addItem(
                    templateId: template.id,
                    title: form.title,
                    description: form.description,
                    requiresValue: form.requiresValue,
                    unit: form.unit,
                    minValue: form.minValue.map { KotlinDouble(double: $0) },
                    maxValue: form.maxValue.map { KotlinDouble(double: $0) }
                )
            }
        }
        .sheet(item: Binding(
            get: { editingItem.map { EditingItemBox(item: $0) } },
            set: { if $0 == nil { editingItem = nil } }
        )) { box in
            ItemFormSheet(title: "Rediger punkt", existing: box.item) { form in
                _ = try await repo.updateItem(
                    id: box.item.id,
                    title: form.title,
                    description: form.description,
                    requiresValue: form.requiresValue,
                    unit: form.unit,
                    minValue: form.minValue.map { KotlinDouble(double: $0) },
                    maxValue: form.maxValue.map { KotlinDouble(double: $0) }
                )
            }
        }
        .alert("Endre navn", isPresented: $showRenameAlert) {
            TextField("Navn", text: $renameText)
            Button("Avbryt", role: .cancel) {}
            Button("Lagre") {
                let name = renameText.trimmingCharacters(in: .whitespaces)
                guard !name.isEmpty else { return }
                Task { try? await repo.renameTemplate(id: template.id, name: name) }
            }
        }
        .confirmationDialog(
            "Slette «\(deleteItemTarget?.title ?? "")»?",
            isPresented: Binding(
                get: { deleteItemTarget != nil },
                set: { if !$0 { deleteItemTarget = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Slett punkt", role: .destructive) {
                if let item = deleteItemTarget {
                    Task { _ = try? await repo.deleteItem(id: item.id) }
                }
                deleteItemTarget = nil
            }
            Button("Avbryt", role: .cancel) { deleteItemTarget = nil }
        } message: {
            Text("Punktet fjernes fra lista for alle enheter.")
        }
        .confirmationDialog(
            "Slette \(template.name)?",
            isPresented: $showDeleteConfirm,
            titleVisibility: .visible
        ) {
            Button("Slett sekk og alt innhold", role: .destructive) {
                Task { try? await repo.deleteTemplate(id: template.id) }
            }
            Button("Avbryt", role: .cancel) {}
        }
        .task {
            for await list in repo.itemsFor(templateId: template.id) {
                items = list
            }
        }
    }

    private func subtitle(for item: ChecklistItem) -> String? {
        var parts: [String] = []
        if let description = item.description_, !description.isEmpty { parts.append(description) }
        if item.requiresValue != 0 {
            var measure = "Måling"
            if let unit = item.unit, !unit.isEmpty { measure += " i \(unit)" }
            if let min = item.minValue { measure += " · min \(Self.formatted(min.doubleValue))" }
            if let max = item.maxValue { measure += " · maks \(Self.formatted(max.doubleValue))" }
            parts.append(measure)
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private static func formatted(_ value: Double) -> String {
        value.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int(value))
            : String(value)
    }
}

/// Wrapper så ChecklistItem kan brukes med .sheet(item:)
private struct EditingItemBox: Identifiable {
    let item: ChecklistItem
    var id: String { item.id }
}

struct ItemFormValues {
    var title: String
    var description: String?
    var requiresValue: Bool
    var unit: String?
    var minValue: Double?
    var maxValue: Double?
}

struct ItemFormSheet: View {
    let title: String
    var existing: ChecklistItem?
    let onSave: (ItemFormValues) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var itemTitle = ""
    @State private var itemDescription = ""
    @State private var requiresValue = false
    @State private var unit = ""
    @State private var minText = ""
    @State private var maxText = ""
    @State private var failed = false

    private var canSave: Bool {
        !itemTitle.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Punkt") {
                    TextField("Tittel, f.eks. Brannslukker", text: $itemTitle)
                    TextField("Beskrivelse (valgfritt)", text: $itemDescription)
                }

                Section {
                    Toggle("Krever avlest verdi", isOn: $requiresValue)
                    if requiresValue {
                        TextField("Enhet, f.eks. bar", text: $unit)
                        TextField("Minste tillatte verdi", text: $minText)
                            .keyboardType(.decimalPad)
                        TextField("Største tillatte verdi", text: $maxText)
                            .keyboardType(.decimalPad)
                    }
                } header: {
                    Text("Måling")
                } footer: {
                    if requiresValue {
                        Text("Verdier utenfor grensene flagges automatisk som avvik.")
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Lagre") { save() }
                        .disabled(!canSave)
                }
            }
            .alert("Kunne ikke lagre", isPresented: $failed) {
                Button("OK", role: .cancel) {}
            }
        }
        .onAppear { populate() }
    }

    private func populate() {
        guard let existing else { return }
        itemTitle = existing.title
        itemDescription = existing.description_ ?? ""
        requiresValue = existing.requiresValue != 0
        unit = existing.unit ?? ""
        minText = existing.minValue.map { EditItemsSection.formattedPublic($0.doubleValue) } ?? ""
        maxText = existing.maxValue.map { EditItemsSection.formattedPublic($0.doubleValue) } ?? ""
    }

    private func save() {
        let form = ItemFormValues(
            title: itemTitle.trimmingCharacters(in: .whitespaces),
            description: itemDescription.trimmingCharacters(in: .whitespaces).isEmpty
                ? nil : itemDescription.trimmingCharacters(in: .whitespaces),
            requiresValue: requiresValue,
            unit: requiresValue && !unit.trimmingCharacters(in: .whitespaces).isEmpty
                ? unit.trimmingCharacters(in: .whitespaces) : nil,
            minValue: requiresValue ? Double(minText.replacingOccurrences(of: ",", with: ".")) : nil,
            maxValue: requiresValue ? Double(maxText.replacingOccurrences(of: ",", with: ".")) : nil
        )
        Task {
            do {
                try await onSave(form)
                dismiss()
            } catch {
                failed = true
            }
        }
    }
}

extension EditItemsSection {
    static func formattedPublic(_ value: Double) -> String {
        value.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int(value))
            : String(value)
    }
}
