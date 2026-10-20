#!/bin/sh
#
# Velger riktig Firebase-konfigurasjon ut fra byggekonfigurasjon, slik at
# testkjøringer aldri havner i den ekte historikken.
#
#   Debug   -> GoogleService-Info-Dev.plist   (redcross-ambulanse-dev)
#   Release -> GoogleService-Info-Prod.plist  (redcross-ambulanse)
#
# Kjøres som «Run Script»-fase i Xcode, FØR «Copy Bundle Resources».
# De to kildefilene skal ligge i iosApp/iosApp/Firebase/ og skal IKKE være
# medlem av target-et – ellers kopieres begge inn og Firebase plukker feil.

set -e

SOURCE_DIR="${SRCROOT}/iosApp/Firebase"

# Mappa iosApp/iosApp er synkronisert med target-et: alt som ligger der
# kopieres inn i appen av «Copy Bundle Resources», som kjører ETTER dette
# skriptet. En løs GoogleService-Info.plist der overskriver derfor valget
# under – det er slik TEST-appen havnet mot produksjon i september 2026.
STRAY="${SRCROOT}/iosApp/GoogleService-Info.plist"
if [ -f "${STRAY}" ]; then
    echo "error: ${STRAY} må fjernes. Den overskriver miljøvalget og kan få"
    echo "error: testappen til å skrive til produksjon. Filene hører hjemme i"
    echo "error: iosApp/iosApp/Firebase/ som -Dev.plist og -Prod.plist."
    exit 1
fi

# Ingen av filene: bygget rett fra GitHub uten Firebase. Appen fungerer da
# fullt ut lokalt, bare uten synkronisering. Ligger én av dem der, er
# Firebase ment å være satt opp, og en manglende fil er en feil (under).
if [ ! -f "${SOURCE_DIR}/GoogleService-Info-Dev.plist" ] && [ ! -f "${SOURCE_DIR}/GoogleService-Info-Prod.plist" ]; then
    echo "warning: Ingen Firebase-konfigurasjon i iosApp/iosApp/Firebase/ – bygger uten synkronisering (kun lokal lagring)."
    exit 0
fi

if [ "${CONFIGURATION}" = "Release" ]; then
    SOURCE="${SOURCE_DIR}/GoogleService-Info-Prod.plist"
    ENVIRONMENT="produksjon"
else
    SOURCE="${SOURCE_DIR}/GoogleService-Info-Dev.plist"
    ENVIRONMENT="test"
fi

if [ ! -f "${SOURCE}" ]; then
    echo "error: Fant ikke ${SOURCE}. Last ned GoogleService-Info.plist fra"
    echo "error: Firebase-konsollen og legg den i iosApp/Firebase/ med riktig navn."
    exit 1
fi

DESTINATION="${BUILT_PRODUCTS_DIR}/${PRODUCT_NAME}.app/GoogleService-Info.plist"
cp "${SOURCE}" "${DESTINATION}"

echo "Firebase-konfigurasjon: ${ENVIRONMENT} (${CONFIGURATION})"
