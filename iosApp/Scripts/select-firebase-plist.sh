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
