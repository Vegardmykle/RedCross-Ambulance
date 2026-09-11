import Foundation

/// Dato og klokkeslett slik de vises i appen.
///
/// Lå tidligere som fem byte-identiske DateFormatter-oppsett, ett i hver
/// skjerm. Verre enn gjentakelsen var hvor de sto: inne i computed
/// properties, altså konstruert på nytt for hver rad i hver opptegning.
/// DateFormatter er dyr å lage, og historikk- og mangler-listene kan ha
/// mange rader.
///
/// Kotlin-siden formaterer i `formatMillis` (androidUi). De to kan ikke deles
/// – Foundation og java.text er hver sine verdener – men ordlyden rundt dem
/// ligger i sharedLogic, så det er bare selve datoen som er plattformnær.
enum AppDate {
    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "nb_NO")
        f.dateStyle = .medium
        f.timeStyle = .short
        return f
    }()

    /// Millisekunder siden epoch → «15. mars 2026, 14:30».
    static func format(_ millis: Int64) -> String {
        formatter.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }

    /// Som `format`, men med hvem som utførte handlingen når navnet er kjent.
    static func format(_ millis: Int64, by name: String?) -> String {
        let time = format(millis)
        return name.map { "\(time) av \($0)" } ?? time
    }
}
