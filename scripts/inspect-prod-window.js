#!/usr/bin/env node
/**
 * Viser hva som er skrevet til produksjon i et gitt tidsrom.
 *
 * Leser bare – sletter og endrer ingenting. Ment som første steg før en
 * opprydding: se nøyaktig hva som ligger der, og hvem som skrev det, før noe
 * fjernes fra det som er ambulansegruppas dokumentasjon.
 *
 * Bruk:
 *   node scripts/inspect-prod-window.js --from 2026-09-09 --to 2026-09-12
 *
 * Datoene tolkes i lokal tid. --to er eksklusiv, altså «til midnatt før».
 *
 * Krever scripts/keys/prod-service-account.json. Nøkkelen gir full tilgang
 * og skal aldri deles.
 */

const path = require('path');
const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}

const fromDate = arg('from');
const toDate = arg('to');
if (!fromDate || !toDate) {
  console.error('Bruk: node scripts/inspect-prod-window.js --from ÅÅÅÅ-MM-DD --to ÅÅÅÅ-MM-DD');
  process.exit(1);
}

// Lokal midnatt, ikke UTC – tidsrommet er slik mannskapet opplever døgnet
const from = new Date(`${fromDate}T00:00:00`).getTime();
const to = new Date(`${toDate}T00:00:00`).getTime();

const app = initializeApp({
  credential: cert(require(path.join(__dirname, 'keys', 'prod-service-account.json'))),
});
const db = getFirestore(app);

const fmt = (ms) =>
  ms ? new Date(ms).toLocaleString('nb-NO', { dateStyle: 'short', timeStyle: 'short' }) : '–';

async function main() {
  console.log(`Produksjon: ${new Date(from).toLocaleString('nb-NO')} → ${new Date(to).toLocaleString('nb-NO')}\n`);

  // ---------- Kontroller ----------
  const runs = (await db.collection('runs').get()).docs
    .map((d) => d.data())
    .filter((r) => r.createdAt >= from && r.createdAt < to)
    .sort((a, b) => a.createdAt - b.createdAt);

  const [templates, ambulances, users] = await Promise.all(
    ['templates', 'ambulances', 'users'].map((c) => db.collection(c).get()),
  );
  const nameOf = (snap, id, field = 'name') => {
    const d = snap.docs.find((x) => x.id === id);
    return d ? d.data()[field] : id;
  };

  console.log(`KONTROLLER I TIDSROMMET: ${runs.length}`);
  for (const r of runs) {
    const responses = (await db.collection('responses').where('checklistRunId', '==', r.id).get()).size;
    console.log(
      `  ${r.id}\n` +
      `    ${nameOf(templates, r.templateId)} · ${nameOf(ambulances, r.ambulanceId, 'callSign')}\n` +
      `    opprettet ${fmt(r.createdAt)} · status ${r.status}\n` +
      `    før-signert ${fmt(r.beforeSignedAt)}${r.beforeUserId ? ` av ${nameOf(users, r.beforeUserId)}` : ''}` +
      ` · avsluttet ${fmt(r.completedAt)}${r.userId ? ` av ${nameOf(users, r.userId)}` : ''}\n` +
      `    ${responses} svar`,
    );
  }

  // ---------- Alt annet som er rørt ----------
  console.log('\nANDRE ENDRINGER I TIDSROMMET (ikke foreslått slettet):');
  for (const c of ['templates', 'items', 'users', 'ambulances', 'links']) {
    const touched = (await db.collection(c).get()).docs
      .map((d) => d.data())
      .filter((x) => x.updatedAt >= from && x.updatedAt < to);
    if (touched.length) {
      console.log(`  ${c}: ${touched.length} endret`);
      for (const t of touched.slice(0, 8)) {
        console.log(`    ${t.id} · ${t.name ?? t.title ?? t.callSign ?? ''} · ${fmt(t.updatedAt)}`);
      }
      if (touched.length > 8) console.log(`    … og ${touched.length - 8} til`);
    }
  }

  // ---------- Kontekst: hva finnes ellers? ----------
  const allRuns = (await db.collection('runs').get()).size;
  const allResponses = (await db.collection('responses').get()).size;
  console.log(`\nTOTALT I PRODUKSJON: ${allRuns} kontroller, ${allResponses} svar`);
  console.log(`Utenfor tidsrommet: ${allRuns - runs.length} kontroller blir stående.`);
}

main().then(() => process.exit(0)).catch((e) => {
  console.error('Feil:', e.message);
  process.exit(1);
});
