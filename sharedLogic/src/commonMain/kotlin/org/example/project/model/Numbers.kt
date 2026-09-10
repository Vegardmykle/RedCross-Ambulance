package org.example.project.model

/**
 * Tolkning og visning av avleste måleverdier.
 *
 * Dette er domenelogikk, ikke formatering: det er den samme parsingen som
 * avgjør om en avlest verdi havner utenfor punktets grenser og dermed
 * flagges som MANGELFULL. Lå den én gang i Kotlin og én gang i Swift, kunne
 * de to plattformene svare ulikt på om utstyret er i orden.
 */

/**
 * Filtrerer input mens det skrives: kun sifre og ett desimaltegn.
 * Komma gjøres om til punktum, siden det er komma mannskapet taster på et
 * norsk tastatur, men punktum [normalizeNumber] kan tolke.
 */
fun filterNumeric(input: String): String {
    var filtered = input.replace(",", ".").filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    if (firstDot >= 0) {
        filtered = filtered.substring(0, firstDot + 1) +
            filtered.substring(firstDot + 1).filter { it.isDigit() }
    }
    return filtered
}

/**
 * Gjør et halvferdig tall om til et lagringsklart: «.1» → «0.1»,
 * «180.» → «180». Returnerer null hvis det ikke er et gyldig tall, slik at
 * kallstedet kan la være å lagre i stedet for å lagre noe meningsløst.
 */
fun normalizeNumber(input: String): String? {
    var value = input.trim()
    if (value.endsWith(".")) value = value.dropLast(1)
    if (value.startsWith(".")) value = "0$value"
    return if (value.toDoubleOrNull() != null) value else null
}

/** 180.0 → «180», 2.5 → «2.5». Hele tall vises uten desimaler. */
fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
