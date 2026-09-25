#!/usr/bin/env node
// Runs the browser checks in order and prints one summary. See README.md for prerequisites.
//   node tools/browser-checks/run-all.mjs            all parts
//   node tools/browser-checks/run-all.mjs --only=3   just part 3 (comma-separate for several: --only=1,3)
import { spawn } from "node:child_process";
import { readdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { BACKEND, BASE, MAILHOG, OUT } from "./lib.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const only = (process.argv.find((a) => a.startsWith("--only=")) ?? "").replace("--only=", "").split(",").filter(Boolean).map(Number);

const parts = readdirSync(path.join(HERE, "checks"))
  .filter((f) => /^\d\d-.+\.mjs$/.test(f))
  .sort()
  .filter((f) => only.length === 0 || only.includes(Number(f.slice(0, 2))));

async function reachable(url) {
  try { return (await fetch(url, { signal: AbortSignal.timeout(4000) })).status < 500; } catch { return false; }
}

console.log(`Browser checks against ${BASE} (backend ${BACKEND}, mail ${MAILHOG})\nOutput (screenshots, logs): ${OUT}\n`);
const missing = [];
if (!(await reachable(`${BASE}/login`))) missing.push(`frontend at ${BASE} (start it with: npm run build && npm start)`);
if (!(await reachable(`${BACKEND}/api/v1/health`))) missing.push(`backend at ${BACKEND} (start it with: cd backend && ./mvnw spring-boot:run)`);
if (!(await reachable(`${MAILHOG}/api/v2/messages?limit=1`))) missing.push(`Mailhog at ${MAILHOG} (start it with: docker compose up -d mailhog)`);
if (missing.length > 0) {
  console.error("Cannot start. Not reachable:\n  - " + missing.join("\n  - "));
  process.exit(2);
}
if (parts.length === 0) {
  console.error(`No parts match --only=${only.join(",")}`);
  process.exit(2);
}

const results = [];
for (const file of parts) {
  const started = Date.now();
  console.log(`\n================ ${file} ================`);
  const { code, passed, total } = await new Promise((resolve) => {
    const child = spawn(process.execPath, [path.join(HERE, "checks", file)], { stdio: ["ignore", "pipe", "inherit"], env: process.env });
    let out = "";
    child.stdout.on("data", (chunk) => { process.stdout.write(chunk); out += chunk; });
    child.on("close", (exitCode) => {
      const m = out.match(/=== (\d+)\/(\d+) checks passed ===/);
      resolve({ code: exitCode, passed: m ? Number(m[1]) : 0, total: m ? Number(m[2]) : 0 });
    });
  });
  results.push({ file, code, passed, total, seconds: Math.round((Date.now() - started) / 1000) });
}

console.log("\n================ Summary ================");
for (const r of results) {
  console.log(`${r.code === 0 ? "PASS" : "FAIL"}  ${r.file.padEnd(34)} ${String(r.passed).padStart(3)}/${String(r.total).padEnd(3)} checks   ${r.seconds}s`);
}
const passed = results.reduce((n, r) => n + r.passed, 0);
const total = results.reduce((n, r) => n + r.total, 0);
const failed = results.filter((r) => r.code !== 0).length;
console.log(`\n${passed}/${total} checks passed across ${results.length} part${results.length === 1 ? "" : "s"}${failed ? `, ${failed} part(s) FAILED` : ""}`);
process.exit(failed ? 1 : 0);
