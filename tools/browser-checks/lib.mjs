// Shared harness for the browser checks: configuration, a tiny API client for test data, and a headless Chrome
// driver that types and clicks like a user (real mouse and keyboard events over the DevTools protocol).
import { spawn, execSync } from "node:child_process";
import { existsSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));

// ---- configuration (all overridable with environment variables) ------------------------------
export const REPO_ROOT = path.resolve(HERE, "../..");
export const BASE = process.env.BASE_URL ?? "http://localhost:3000";                 // the frontend
export const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";           // Spring Boot
export const MAILHOG = process.env.MAILHOG_URL ?? "http://localhost:8025";           // catches verification emails
// Screenshots, the throwaway Chrome profile and logs. Kept outside the repo on purpose: linters and indexers would
// otherwise crawl the Chrome profile.
export const OUT = process.env.BROWSER_CHECKS_OUT ?? path.join(os.tmpdir(), "personal-os-browser-checks");
export const SHOTS = path.join(OUT, "shots");
const DEBUG_PORT = Number(process.env.CHROME_DEBUG_PORT ?? 9333);
const BACKEND_PROCESS = "com.personalos.backend.PersonalOsBackendApplication";
const BACKEND_START_CMD = process.env.BACKEND_START_CMD ?? "./mvnw -B -q spring-boot:run";

const POSTGRES_CONTAINER = process.env.POSTGRES_CONTAINER ?? "personal-os-postgres";
/** Runs SQL against the dev database (through the Docker container). Only for fixtures the API cannot make, such as back-dated workouts. */
export function sql(statement) {
  return execSync(`docker exec -i ${POSTGRES_CONTAINER} psql -U postgres -d personal_os -v ON_ERROR_STOP=1 -q`, { input: statement }).toString();
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function chromePath() {
  const candidates = [
    process.env.CHROME_PATH,
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/usr/bin/google-chrome",
    "/usr/bin/google-chrome-stable",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
  ].filter(Boolean);
  const found = candidates.find((p) => existsSync(p));
  if (!found) throw new Error("Chrome not found. Install Google Chrome or set CHROME_PATH to its executable.");
  return found;
}

// ---- backend control (only the outage checks use these; they act on a locally running backend) ---
export async function backendHealthy() {
  try { return (await fetch(`${BACKEND}/api/v1/health`, { signal: AbortSignal.timeout(2000) })).ok; } catch { return false; }
}
export function backendPid() {
  try { return execSync(`pgrep -f '${BACKEND_PROCESS}' | head -1`).toString().trim(); } catch { return ""; }
}
export async function stopBackend() {
  try { execSync(`pkill -f ${BACKEND_PROCESS}`); } catch { /* not running: nothing to stop */ }
  for (let i = 0; i < 30 && await backendHealthy(); i++) await sleep(500);
}
/** Starts the backend from the repo's backend folder (needs Java 21 available, as for a normal local run). */
export async function startBackend() {
  mkdirSync(OUT, { recursive: true });
  spawn("sh", ["-c", `${BACKEND_START_CMD} > "${path.join(OUT, "backend-restart.log")}" 2>&1`],
    { cwd: path.join(REPO_ROOT, "backend"), detached: true, stdio: "ignore" }).unref();
  for (let i = 0; i < 120; i++) { if (await backendHealthy()) return true; await sleep(1000); }
  return false;
}

// ---- results -------------------------------------------------------------------------------
export const results = [];
let section = "";
export function begin(name) { section = name; console.log(`\n## ${name}`); }
export function check(name, ok, detail = "") {
  results.push({ section, name, ok: !!ok, detail });
  console.log(`${ok ? "  PASS" : "  FAIL"}  ${name}${!ok && detail ? `  -> ${detail}` : ""}`);
  return !!ok;
}
export function summary() {
  const failed = results.filter((r) => !r.ok);
  console.log(`\n=== ${results.length - failed.length}/${results.length} checks passed ===`);
  failed.forEach((f) => console.log(`FAILED [${f.section}] ${f.name}  ${f.detail}`));
  return failed.length;
}

// ---- API client (for test data; the UI is what is being tested) ------------------------------
export class Api {
  constructor() { this.cookies = {}; this.csrf = null; }
  cookieHeader() { return Object.entries(this.cookies).map(([k, v]) => `${k}=${v}`).join("; "); }
  absorb(res) {
    for (const line of res.headers.getSetCookie?.() ?? []) {
      const [pair] = line.split(";");
      const i = pair.indexOf("=");
      const name = pair.slice(0, i), value = pair.slice(i + 1);
      if (value === "" || /max-age=0/i.test(line)) delete this.cookies[name]; else this.cookies[name] = value;
    }
  }
  async call(method, path, body, { csrf = true } = {}) {
    if (csrf && method !== "GET" && !this.csrf) {
      const r = await fetch(BASE + "/api/v1/auth/csrf", { headers: { Cookie: this.cookieHeader() } });
      this.absorb(r); this.csrf = (await r.json()).token;
    }
    const res = await fetch(BASE + path, {
      method,
      headers: { "Content-Type": "application/json", Cookie: this.cookieHeader(), ...(method !== "GET" && this.csrf ? { "X-XSRF-TOKEN": this.csrf } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    this.absorb(res);
    const text = await res.text();
    return { status: res.status, body: text ? JSON.parse(text) : null };
  }
  get(p) { return this.call("GET", p); }
  post(p, b) { return this.call("POST", p, b ?? {}); }
  patch(p, b) { return this.call("PATCH", p, b); }
  put(p, b) { return this.call("PUT", p, b); }
  del(p) { return this.call("DELETE", p); }
}

export async function registerUser(email, password, name = "Test User", timezone = "Asia/Kolkata") {
  const api = new Api();
  await api.post("/api/v1/auth/register", { email, password, displayName: name, timezone });
  await sleep(1500);
  const list = await (await fetch(`${MAILHOG}/api/v2/search?kind=to&query=${encodeURIComponent(email)}`)).text();
  const msg = JSON.parse(list, (k, v) => v).items[0];
  const body = msg.Content.Body.replace(/=\r?\n/g, "");
  const token = body.match(/token=([A-Za-z0-9_-]+)/)[1];
  const verified = await api.post("/api/v1/auth/verify-email", { token });
  if (verified.status !== 200) throw new Error("verify failed " + verified.status);
  const login = await api.post("/api/v1/auth/login", { email, password });
  if (login.status !== 200) throw new Error("login failed " + login.status);
  return api;
}

// ---- browser --------------------------------------------------------------------------------
const HELPERS = `
window.__h = {
  visible(el) { if (!el) return false; const r = el.getBoundingClientRect(); const s = getComputedStyle(el);
    return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none'; },
  norm(t) { return (t || '').replace(/\\s+/g, ' ').trim(); },
  find(sel, text, root) { const scope = root || document;
    return [...scope.querySelectorAll(sel)].filter((e) => this.visible(e))
      .find((e) => !text || this.norm(e.textContent) === text || this.norm(e.getAttribute('aria-label')) === text); },
  // --- redesign-aware helpers: an exercise in the logger is a <section> with an <h3>; a set is a table-like <li>. ---
  section(name) { return [...document.querySelectorAll('main section')].find((s) => this.visible(s) && this.norm(s.querySelector('h3')?.textContent) === name); },
  headings() { return [...document.querySelectorAll('main section h3')].filter((e) => this.visible(e)).map((e) => this.norm(e.textContent)); },
  sets(name) { const s = this.section(name); if (!s) return null;
    return [...s.querySelectorAll('ol > li')].map((li) => { const row = li.querySelector('.num');
      if (!row || li.querySelector('input,select')) return { editing: true };
      const c = [...row.children]; const first = this.norm(c[0].textContent); const rpe = this.norm(c[3].textContent).replace(/^RPE\s*/, '');
      return { warmup: first.startsWith('W'), weight: parseFloat(c[1].textContent), reps: parseInt(c[2].textContent, 10), rpe: rpe ? parseFloat(rpe) : null, text: this.norm(li.innerText) }; }); },
  hasSet(name, weight, reps, extra) { const sets = this.sets(name); return !!sets && sets.some((x) => !x.editing && x.weight === weight && x.reps === reps && (!extra || Object.entries(extra).every(([k, v]) => x[k] === v))); },
  row(name) { return [...document.querySelectorAll('main li')].find((li) => this.visible(li) && [...li.querySelectorAll('a,span')].some((e) => this.norm(e.textContent) === name && !e.closest('button'))); },
  editorNames() { return [...document.querySelectorAll('main ol > li span.font-medium')].filter((e) => this.visible(e)).map((e) => this.norm(e.textContent)); },
  findContains(sel, text, root) { const scope = root || document;
    return [...scope.querySelectorAll(sel)].filter((e) => this.visible(e))
      .find((e) => this.norm(e.textContent).includes(text) || this.norm(e.getAttribute('aria-label')).includes(text)); },
};`;

export class Browser {
  shotDir = SHOTS;

  async launch(profileDir = path.join(OUT, "profile"), port = DEBUG_PORT) {
    rmSync(profileDir, { recursive: true, force: true });
    mkdirSync(this.shotDir, { recursive: true });
    this.chrome = spawn(chromePath(),
      ["--headless=new", "--disable-gpu", "--hide-scrollbars", `--remote-debugging-port=${port}`, `--user-data-dir=${profileDir}`, "about:blank"],
      { stdio: "ignore" });
    let targets;
    for (let i = 0; i < 60; i++) {
      try { targets = await (await fetch(`http://localhost:${port}/json`)).json(); if (targets.find((t) => t.type === "page")) break; } catch {}
      await sleep(250);
    }
    this.ws = new WebSocket(targets.find((t) => t.type === "page").webSocketDebuggerUrl);
    await new Promise((r) => (this.ws.onopen = r));
    this.id = 0; this.pending = new Map(); this.events = [];
    this.ws.onmessage = (m) => {
      const d = JSON.parse(m.data);
      if (d.id && this.pending.has(d.id)) { this.pending.get(d.id)(d); this.pending.delete(d.id); }
      else if (d.method) this.events.push(d);
    };
    await this.send("Page.enable"); await this.send("Runtime.enable"); await this.send("Network.enable");
    await this.send("Page.addScriptToEvaluateOnNewDocument", { source: HELPERS });
    this.consoleErrors = [];
    this.ws.addEventListener("message", (m) => {
      const d = JSON.parse(m.data);
      if (d.method === "Runtime.exceptionThrown") this.consoleErrors.push(d.params.exceptionDetails.exception?.description || d.params.exceptionDetails.text);
      if (d.method === "Runtime.consoleAPICalled" && d.params.type === "error") this.consoleErrors.push(d.params.args.map((a) => a.value ?? a.description).join(" "));
    });
  }
  send(method, params = {}) {
    return new Promise((res) => { const i = ++this.id; this.pending.set(i, res); this.ws.send(JSON.stringify({ id: i, method, params })); });
  }
  async eval(expr) {
    const r = await this.send("Runtime.evaluate", { expression: expr, returnByValue: true, awaitPromise: true });
    if (r.result?.exceptionDetails) throw new Error("eval: " + (r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text) + " in " + expr.slice(0, 120));
    return r.result?.result?.value;
  }
  async viewport(width, height = 800) {
    await this.send("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 1, mobile: width < 600 });
    await this.send("Emulation.setTouchEmulationEnabled", { enabled: width < 600 });
    this.width = width; this.height = height;
  }
  async goto(path) {
    await this.send("Page.navigate", { url: path.startsWith("http") ? path : BASE + path });
    await sleep(600);
    for (let i = 0; i < 40; i++) { if (await this.eval("document.readyState") === "complete") break; await sleep(150); }
    await sleep(500);
  }
  async waitFor(expr, label, timeout = 8000) {
    const start = Date.now();
    while (Date.now() - start < timeout) { try { if (await this.eval(`!!(${expr})`)) return true; } catch {} await sleep(150); }
    throw new Error(`timeout waiting for: ${label}`);
  }
  async has(expr) { try { return !!(await this.eval(`!!(${expr})`)); } catch { return false; } }
  url() { return this.eval("location.pathname + location.search"); }
  text() { return this.eval("document.body.innerText"); }
  async shot(name) {
    const r = await this.send("Page.captureScreenshot", { format: "png" });
    writeFileSync(`${this.shotDir}/${name}.png`, Buffer.from(r.result.data, "base64"));
  }
  /**
   * Real mouse click at the centre of the element the expression returns. Refuses (and reports) when another
   * element is on top of that point, which is how an overlapping sticky bar would show up.
   */
  async click(expr, label) {
    await this.waitFor(`!!(${expr})`, `element for click: ${label}`);
    await this.eval(`(${expr}).scrollIntoView({block:'center', inline:'center'})`);
    await sleep(120);
    const info = await this.eval(`(() => { const el = ${expr}; const r = el.getBoundingClientRect();
      const x = r.left + r.width / 2, y = r.top + r.height / 2; const top = document.elementFromPoint(x, y);
      const ok = top === el || el.contains(top);
      return { x, y, ok, w: r.width, h: r.height, topTag: top ? top.tagName + '.' + String(top.className).slice(0, 60) : 'none' }; })()`);
    if (!info.ok) throw new Error(`click blocked for "${label}": covered by ${info.topTag}`);
    await this.send("Input.dispatchMouseEvent", { type: "mouseMoved", x: info.x, y: info.y });
    await this.send("Input.dispatchMouseEvent", { type: "mousePressed", x: info.x, y: info.y, button: "left", clickCount: 1 });
    await this.send("Input.dispatchMouseEvent", { type: "mouseReleased", x: info.x, y: info.y, button: "left", clickCount: 1 });
    await sleep(250);
    return info;
  }
  clickText(sel, text) { return this.click(`__h.find(${JSON.stringify(sel)}, ${JSON.stringify(text)})`, `${sel} "${text}"`); }
  /** Focus an input, replace its content by typing, like a user would. */
  async type(expr, text, label) {
    await this.waitFor(`!!(${expr})`, `input for typing: ${label}`);
    await this.eval(`(() => { const el = ${expr}; el.scrollIntoView({block:'center'}); el.focus(); if (el.select) el.select(); })()`);
    await sleep(60);
    if (text === "") { await this.send("Input.dispatchKeyEvent", { type: "keyDown", key: "Backspace", code: "Backspace", windowsVirtualKeyCode: 8 }); await this.send("Input.dispatchKeyEvent", { type: "keyUp", key: "Backspace", code: "Backspace", windowsVirtualKeyCode: 8 }); }
    else await this.send("Input.insertText", { text });
    await sleep(150);
  }
  /** Native <select>: headless Chrome cannot open the popup, so set the value the way the browser does on change. */
  async select(expr, value, label) {
    await this.waitFor(`!!(${expr})`, `select: ${label}`);
    await this.eval(`(() => { const el = ${expr}; el.scrollIntoView({block:'center'}); Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(el, ${JSON.stringify(value)}); el.dispatchEvent(new Event('change', {bubbles:true})); })()`);
    await sleep(150);
  }
  async key(key, code, vk) {
    await this.send("Input.dispatchKeyEvent", { type: "keyDown", key, code, windowsVirtualKeyCode: vk });
    await this.send("Input.dispatchKeyEvent", { type: "keyUp", key, code, windowsVirtualKeyCode: vk });
    await sleep(250);
  }
  /** Horizontal overflow: document wider than the viewport, or any visible element sticking out to the right. */
  overflow() {
    return this.eval(`(() => { const de = document.documentElement; const w = ${this.width ?? 'window.innerWidth'};
      const offenders = [...document.querySelectorAll('body *')].filter((e) => { const r = e.getBoundingClientRect(); const s = getComputedStyle(e);
        return r.width > 0 && r.right > w + 1 && s.position !== 'fixed' && !e.closest('[data-slot=sheet-content],[data-slot=dialog-content],.sr-only'); })
        .map((e) => e.tagName + '.' + String(e.className).slice(0, 50)).slice(0, 5);
      return { innerWidth: window.innerWidth, scrollWidth: de.scrollWidth, overflow: de.scrollWidth > w + 1, offenders }; })()`);
  }
  close() { try { this.ws.close(); } catch {} this.chrome.kill(); }
}
