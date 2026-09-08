#!/usr/bin/env node
/**
 * Kopierer sjekklistene fra produksjon til testmiljøet, slik at du tester
 * mot de listene mannskapet faktisk bruker.
 *
 * Kopierer BARE definisjonene – maler, punkter, kjøretøy, mannskap og lenker.
 * Kontroller og svar (runs/responses) holdes utenfor med vilje: historikk er
 * dokumentasjon og hører ikke hjemme i et testmiljø.
 *
 * Bruk:
 *   npm install firebase-admin
 *   node scripts/copy-lists-to-dev.js --dry-run     # vis hva som ville skjedd
 *   node scripts/copy-lists-to-dev.js
 *
 * Krever to tjenestekontonøkler (Firebase-konsollen → Prosjektinnstillinger →
 * Tjenestekontoer → Generer ny privat nøkkel) lagt i scripts/keys/:
 *   scripts/keys/prod-service-account.json
 *   scripts/keys/dev-service-account.json
 * Mappa er utelatt fra git – nøklene gir full skrivetilgang og skal aldri deles.
 */

const path = require('path');
// Modulære inngangspunkter – rot-modulen eksporterer ikke lenger
// `credential` i CommonJS i nyere versjoner av firebase-admin
const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

// Definisjonene av listene. runs/responses er bevisst utelatt.
const COLLECTIONS = ['templates', 'items', 'ambulances', 'users', 'links'];

const DRY_RUN = process.argv.includes('--dry-run');
const KEYS = path.join(__dirname, 'keys');

function firestoreFor(name, keyFile) {
  let credentials;
  try {
    credentials = require(path.join(KEYS, keyFile));
  } catch {
    console.error(`\nFant ikke ${path.join('scripts/keys', keyFile)}.`);
    console.error('Last ned tjenestekontonøkkelen fra Firebase-konsollen først.\n');
    process.exit(1);
  }
  const app = initializeApp({ credential: cert(credentials) }, name);
  console.log(`${name}: prosjekt ${credentials.project_id}`);
  return { db: getFirestore(app), projectId: credentials.project_id };
}

async function main() {
  const source = firestoreFor('prod', 'prod-service-account.json');
  const target = firestoreFor('dev', 'dev-service-account.json');

  // Lett å bytte om når nedlastingene heter nesten det samme, og
  // konsekvensen ville vært å skrive produksjon over seg selv
  if (source.projectId === target.projectId) {
    console.error('\nBegge nøklene peker på samme prosjekt. Sjekk at du ikke');
    console.error('har lagt samme fil under begge navnene.\n');
    process.exit(1);
  }

  const prod = source.db;
  const dev = target.db;
  console.log('');

  console.log(DRY_RUN ? 'TØRRKJØRING – ingenting skrives\n' : 'Kopierer til dev\n');

  let total = 0;
  for (const name of COLLECTIONS) {
    const snapshot = await prod.collection(name).get();
    console.log(`${name}: ${snapshot.size} dokumenter`);
    total += snapshot.size;

    if (DRY_RUN) continue;

    // Batch tar maks 500 operasjoner
    let batch = dev.batch();
    let count = 0;
    for (const doc of snapshot.docs) {
      batch.set(dev.collection(name).doc(doc.id), doc.data());
      if (++count % 450 === 0) {
        await batch.commit();
        batch = dev.batch();
      }
    }
    await batch.commit();
  }

  console.log(`\n${total} dokumenter ${DRY_RUN ? 'ville blitt kopiert' : 'kopiert'}.`);

  if (!DRY_RUN) {
    console.log(
      '\nTøm appdata på testenhetene før du åpner testappen:\n' +
      '  adb shell pm clear no.oslorodekors.ambulanse.debug\n' +
      'Ellers kan en eldre lokal versjon ligge igjen ved siden av det som ble kopiert.'
    );
  }
}

main().catch((error) => {
  console.error('\nFeilet:', error.message);
  process.exit(1);
});
