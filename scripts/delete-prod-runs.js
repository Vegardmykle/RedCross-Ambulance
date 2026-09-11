#!/usr/bin/env node
/**
 * Sletter kontroller og tilhørende svar fra produksjon.
 *
 * Ment for opprydding etter at testing ved et uhell har skrevet til
 * produksjonsprosjektet. Dette er data ambulansegruppa ellers bruker som
 * dokumentasjon, så skriptet er bygget for å gjøre det vanskelig å slette for
 * mye:
 *
 *   - tørrkjøring er standard, sletting krever --delete
 *   - alt som skal slettes skrives til en JSON-fil først, slik at det kan
 *     legges tilbake
 *   - kontroller som har lukket avvik i ELDRE kontroller nektes slettet, for
 *     da ville det gamle avviket stått som løst av noe som ikke finnes
 *
 * Bruk:
 *   node scripts/delete-prod-runs.js --from 2026-09-09 --to 2026-09-12
 *   node scripts/delete-prod-runs.js --from 2026-09-09 --to 2026-09-12 --delete
 *
 * Datoene tolkes i lokal tid; --to er eksklusiv.
 * Krever scripts/keys/prod-service-account.json.
 */

const fs = require('fs');
const path = require('path');
const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

function arg(name) {
  const i = process.argv.indexOf(`--${name}`);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : null;
}
const APPLY = process.argv.includes('--delete');
const fromDate = arg('from');
const toDate = arg('to');
if (!fromDate || !toDate) {
  console.error('Bruk: node scripts/delete-prod-runs.js --from ÅÅÅÅ-MM-DD --to ÅÅÅÅ-MM-DD [--delete]');
  process.exit(1);
}
const from = new Date(`${fromDate}T00:00:00`).getTime();
const to = new Date(`${toDate}T00:00:00`).getTime();

const app = initializeApp({
  credential: cert(require(path.join(__dirname, 'keys', 'prod-service-account.json'))),
});
const db = getFirestore(app);

async function main() {
  const runDocs = (await db.collection('runs').get()).docs
    .filter((d) => d.data().createdAt >= from && d.data().createdAt < to);
  const runIds = runDocs.map((d) => d.id);

  if (!runIds.length) {
    console.log('Ingen kontroller i tidsrommet. Ingenting å gjøre.');
    return;
  }

  const allResponses = (await db.collection('responses').get()).docs;
  const responseDocs = allResponses.filter((d) => runIds.includes(d.data().checklistRunId));

  // Vern: ville slettingen etterlate et eldre avvik som «løst» av en kontroll
  // som ikke lenger finnes?
  const dangling = allResponses.filter(
    (d) => runIds.includes(d.data().resolvedByRunId) && !runIds.includes(d.data().checklistRunId),
  );
  if (dangling.length) {
    console.error(`AVBRUTT: ${dangling.length} eldre svar er lukket av kontroller i tidsrommet.`);
    console.error('Slettes de, står avvikene som løst uten sporbar årsak. Rydd disse først:');
    for (const d of dangling) console.error(`  ${d.id} (kontroll ${d.data().checklistRunId})`);
    process.exitCode = 1;
    return;
  }

  // Sikkerhetskopi før noe røres
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const backup = path.join(__dirname, `prod-backup-${stamp}.json`);
  fs.writeFileSync(
    backup,
    JSON.stringify(
      {
        exportedAt: new Date().toISOString(),
        window: { from: fromDate, to: toDate },
        runs: runDocs.map((d) => d.data()),
        responses: responseDocs.map((d) => d.data()),
      },
      null,
      2,
    ),
  );
  console.log(`Sikkerhetskopi: ${backup}`);
  console.log(`${runIds.length} kontroller og ${responseDocs.length} svar berøres.\n`);

  if (!APPLY) {
    console.log('TØRRKJØRING – ingenting er slettet. Kjør på nytt med --delete for å utføre.');
    return;
  }

  let batch = db.batch();
  let n = 0;
  for (const d of [...responseDocs, ...runDocs]) {
    batch.delete(d.ref);
    if (++n % 400 === 0) {
      await batch.commit();
      batch = db.batch();
    }
  }
  await batch.commit();
  console.log(`SLETTET ${responseDocs.length} svar og ${runDocs.length} kontroller fra produksjon.`);
}

main().then(() => process.exit(process.exitCode ?? 0)).catch((e) => {
  console.error('Feil:', e.message);
  process.exit(1);
});
