// Generates the iso_countries seed INSERT and appends it to V1__init.sql.
// Country names come from the JS Intl database (no hardcoded list). Run once:
//   node scripts/gen_countries.mjs
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const migration = join(here, "..", "src", "main", "resources", "db", "migration", "V1__init.sql");
const MARKER = "-- iso_countries seed (generated; appended below)";

// CLDR maps codes beyond the current ISO 3166-1 list; drop them so we get the
// canonical ~249 countries with no duplicate names.
const SKIP = new Set([
  "EU", "EZ", "UN", "QO", "QU", "XA", "XB", "XZ", "ZZ", // aggregates / specials
  "AC", "CP", "DG", "EA", "IC", "TA",                     // CLDR exceptional reservations
  // formerly-used / transitionally-reserved codes that resolve to a current name:
  "AN", "BU", "CS", "DD", "DY", "FX", "HV", "NH", "NT", "RH",
  "SU", "TP", "VD", "WK", "YD", "YU", "ZR", "JT", "MI", "PC",
  "PU", "PZ", "CT", "FQ", "GE2", "NQ", "BQ2",
]);

const names = new Intl.DisplayNames(["en"], { type: "region" });
const rows = [];
for (let a = 65; a <= 90; a++) {
  for (let b = 65; b <= 90; b++) {
    const code = String.fromCharCode(a, b);
    if (SKIP.has(code)) continue;
    let name;
    try { name = names.of(code); } catch { continue; }
    if (!name || name === code || /unknown/i.test(name)) continue;
    rows.push({ code, name });
  }
}
rows.sort((x, y) => x.name.localeCompare(y.name));

// Safety net: drop any remaining duplicate names (keep first).
const seen = new Set();
const deduped = rows.filter((r) => (seen.has(r.name) ? false : seen.add(r.name)));
rows.length = 0;
rows.push(...deduped);

const values = rows
  .map((r) => `  ('${r.code}', '${r.name.replace(/'/g, "''")}')`)
  .join(",\n");
const insert =
  `\ninsert into public.iso_countries (code, name) values\n${values};\n`;

const sql = readFileSync(migration, "utf8");
const idx = sql.indexOf(MARKER);
if (idx === -1) throw new Error(`marker not found in ${migration}`);
const head = sql.slice(0, idx + MARKER.length) + "\n";
writeFileSync(migration, head + insert);
console.log(`Wrote ${rows.length} countries into V1__init.sql`);
