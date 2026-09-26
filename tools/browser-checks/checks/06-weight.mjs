// Part 6: the /weight page, driven like a user (real typing, clicks, mouse and touch) and confirmed against the API.
import { Browser, begin, check, summary, registerUser, sleep, stopBackend, startBackend, backendHealthy } from "../lib.mjs";
const J = JSON.stringify; const PW = "a long enough password"; const TZ = "Asia/Kolkata";
const email = `weight${Date.now()}@example.com`;
const api = await registerUser(email, PW, "Asha Nair", TZ);

// ---- the page's own calendar maths, written independently so a mistake in the page cannot hide -----------
const todayIn = () => new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const addDays = (iso, n) => { const [y, m, d] = iso.split("-").map(Number); return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10); };
const today = todayIn();
const fmt = (v) => `${Number(Number(v).toFixed(2))} kg`;
const seriesApi = async (from, to, g) => (await api.get(`/api/v1/weight/series?from=${from}&to=${to}&granularity=${g}`)).body;

const hasText = (t) => `document.body.innerText.includes(${J(t)})`;
const figure = `document.querySelector('figure[data-chart]')`;
const points = () => b.eval(`Number(${figure}?.getAttribute('data-points') ?? -1)`);
const tableRows = () => b.eval(`${figure} ? ${figure}.querySelectorAll('table tbody tr').length : -1`);
const dots = () => b.eval(`${figure} ? ${figure}.querySelectorAll('.recharts-dot').length : -1`);
const logInput = (i) => `document.querySelector('form[aria-label="Log weight"]').querySelectorAll('input')[${i}]`;
const targetInput = `document.querySelector('form[aria-label="Target weight"] input')`;
const rowOf = (date) => `document.querySelector('[data-entry-row][data-date="${date}"]')`;
const until = async (fn, label, ms = 8000) => { const t = Date.now(); while (Date.now() - t < ms) { if (await fn()) return true; await sleep(200); } throw new Error("timeout: " + label); };

const b = new Browser(); await b.launch();
try {
  await b.viewport(390);
  await b.goto("/login");
  await b.type(`document.querySelector('input[name=email]')`, email, "email"); await b.type(`document.querySelector('input[name=password]')`, PW, "password");
  await b.clickText("button", "Log in"); await b.waitFor(`location.pathname==='/dashboard'`, "dashboard");

  // ------------------------------------------------------------------------------------------------
  begin("Empty state (a brand-new user), 390px");
  await b.goto("/weight");
  await b.waitFor(hasText("No weights logged yet"), "empty state");
  check("empty state invites the first weight", await b.has(hasText("Log your first weight")));
  check("no chart is drawn without data", !(await b.has(figure)));
  check("the log form is there", await b.has(logInput(1)));
  check("no entries list and no averages without data", !(await b.has(hasText("Weekly averages"))) && !(await b.has(`document.querySelector('[data-entry-row]')`)));
  check("the target can still be set before any weight", await b.has(targetInput));
  const o1 = await b.overflow(); check("no horizontal overflow (empty)", !o1.overflow && o1.offenders.length === 0, J(o1));
  await b.shot("weight-empty-390");

  // ------------------------------------------------------------------------------------------------
  begin("Log a weight, then the single-entry state");
  await b.type(logInput(1), "82.4", "weight"); await b.type(logInput(2), "morning, fasted", "note");
  await b.clickText("button", "Log weight");
  await until(async () => (await api.get(`/api/v1/weight-entries/${today}`)).status !== 500 && (await api.get("/api/v1/weight-entries")).body.totalItems === 1, "entry saved");
  const first = (await api.get("/api/v1/weight-entries")).body.items[0];
  check("the entry was stored for today with weight and note", first.date === today && first.weightKg === 82.4 && first.notes === "morning, fasted", J(first));
  await b.waitFor(`/current/i.test(document.body.innerText)`, "stats appear");
  check("current weight is shown", (await b.text()).includes("82.4"));
  check("a saved confirmation is shown", await b.has(hasText("Saved")));
  await b.waitFor(figure, "chart", 15000);
  await until(async () => (await dots()) >= 1, "a dot");
  check("chart has exactly one point and says the trend needs more days", (await points()) === 1 && await b.has(hasText("One entry so far")));
  check("the form was cleared after saving", (await b.eval(`${logInput(1)}.value`)) === "");
  const o2 = await b.overflow(); check("no horizontal overflow (single entry)", !o2.overflow, J(o2));
  await b.shot("weight-single-390");

  begin("Same-day upsert updates instead of duplicating");
  await b.type(logInput(1), "82.9", "weight"); await b.clickText("button", "Log weight");
  await until(async () => (await api.get("/api/v1/weight-entries")).body.items[0]?.weightKg === 82.9, "entry replaced");
  const after = (await api.get("/api/v1/weight-entries")).body;
  check("still one entry, now 82.9 kg", after.totalItems === 1 && after.items[0].weightKg === 82.9, J(after.items));
  check("the note was cleared by the replacement (PUT replaces the day)", after.items[0].notes === null);
  await until(async () => (await b.eval(`document.querySelectorAll('[data-entry-row]').length`)) === 1, "one row");
  check("the list shows one row", true);

  // ------------------------------------------------------------------------------------------------
  // History: 60 recent days (one gap every 7th day) and a reading every 9 days back over a year and a bit.
  for (let i = 1; i <= 60; i++) if (i % 7 !== 0) await api.put(`/api/v1/weight-entries/${addDays(today, -i)}`, { weightKg: Math.round((90 - i * 0.12 + (i % 3) * 0.3) * 10) / 10 });
  for (let i = 70; i <= 430; i += 9) await api.put(`/api/v1/weight-entries/${addDays(today, -i)}`, { weightKg: Math.round((95 - (430 - i) * 0.02) * 10) / 10 });
  const longNote = "unbroken" + "x".repeat(180) + " and then some ordinary words that should wrap onto lines";
  await api.put(`/api/v1/weight-entries/${addDays(today, -1)}`, { weightKg: 88.1, notes: longNote });

  begin("Chart, ranges and the accessible table, 1280px");
  await b.viewport(1280, 900);
  await b.goto("/weight");
  await b.waitFor(figure, "chart", 15000);
  const win = (n) => ({ from: addDays(today, -(n - 1)), to: today });
  const api90 = await seriesApi(win(90).from, win(90).to, "DAILY");
  await until(async () => (await points()) === api90.points.length, "90d points");
  check(`default range is 90 days: ${api90.points.length} points, as the API returns`, (await points()) === api90.points.length && api90.points.length > 40, `${await points()} vs ${api90.points.length}`);
  check("the 90d link is marked current", await b.has(`document.querySelector('nav[aria-label="Range"] a[aria-current="true"]')?.textContent.trim() === '90d'`));
  await until(async () => (await dots()) === api90.points.length, "dots drawn");
  check("one dot is drawn per reading", (await dots()) === api90.points.length, String(await dots()));
  check("a trend line is drawn", await b.has(`${figure}.querySelectorAll('.recharts-line-curve').length >= 1`));
  check("the screen-reader table has one row per point", (await tableRows()) === api90.points.length);
  const lastPoint = api90.points.at(-1);
  const lastRow = await b.eval(`[...${figure}.querySelectorAll('table tbody tr:last-child td')].map((c) => c.textContent)`);
  check("the table's last row matches the API (reading and trend)", lastRow[0] === fmt(lastPoint.weightKg) && lastRow[1] === fmt(lastPoint.trendKg), `${J(lastRow)} vs ${J(lastPoint)}`);
  check("the table's columns are named for the series", J(await b.eval(`[...${figure}.querySelectorAll('table thead th')].map((c) => c.textContent)`)) === J(["Date", "Weight", "7-day trend"]));
  check("the picture is hidden from assistive technology", await b.has(`${figure}.querySelector('div[aria-hidden=true] .recharts-surface')`));
  await b.shot("weight-90d-1280");

  await b.clickText("a", "30d");
  await b.waitFor(`location.search.includes('range=30d')`, "URL changes");
  const api30 = await seriesApi(win(30).from, win(30).to, "DAILY");
  await until(async () => (await points()) === api30.points.length, "30d points");
  check(`30d: URL is ?range=30d and the chart has ${api30.points.length} points`, (await points()) === api30.points.length && (await tableRows()) === api30.points.length);
  await b.goto("/weight?range=6m");
  const from6m = addDays((() => { const [y, m, d] = today.split("-").map(Number); const t = new Date(Date.UTC(y, m - 1 - 6, 1)); const last = new Date(Date.UTC(t.getUTCFullYear(), t.getUTCMonth() + 1, 0)).getUTCDate(); t.setUTCDate(Math.min(d, last)); return t.toISOString().slice(0, 10); })(), 1);
  const api6m = await seriesApi(from6m, today, "DAILY");
  await b.waitFor(figure, "6m chart", 15000); await until(async () => (await points()) === api6m.points.length, "6m points");
  check(`6m (shared link): daily points, ${api6m.points.length}`, (await points()) === api6m.points.length);
  await b.goto("/weight?range=1y");
  const [yy, mm, dd] = today.split("-").map(Number); const from1y = addDays(new Date(Date.UTC(yy - 1, mm - 1, dd)).toISOString().slice(0, 10), 1);
  const api1y = await seriesApi(from1y, today, "WEEKLY");
  await b.waitFor(figure, "1y chart", 15000); await until(async () => (await points()) === api1y.points.length, "1y points");
  check(`1y: weekly averages, ${api1y.points.length} points, one table row each`, (await points()) === api1y.points.length && (await tableRows()) === api1y.points.length && (await b.text()).includes("Long ranges show the average for each week"));
  check("weekly chart has a single 'Weekly average' series and no trend line", J(await b.eval(`[...${figure}.querySelectorAll('table thead th')].map((c) => c.textContent)`)) === J(["Week starting", "Weekly average"]));
  await b.goto("/weight?range=all");
  const apiAll = await seriesApi("2000-01-01", today, "WEEKLY");
  await b.waitFor(figure, "all chart", 15000); await until(async () => (await points()) === apiAll.points.length, "all points");
  check(`All: weekly averages over every entry, ${apiAll.points.length} points`, (await points()) === apiAll.points.length && apiAll.points.length > api1y.points.length);
  await b.goto("/weight?range=bogus");
  await b.waitFor(figure, "fallback chart", 15000);
  check("an unknown ?range= falls back to 90 days", await b.has(`document.querySelector('nav[aria-label="Range"] a[aria-current="true"]')?.textContent.trim() === '90d'`));

  // ------------------------------------------------------------------------------------------------
  begin("Tooltips: mouse hover (1280px) and touch tap (390px)");
  await b.goto("/weight"); await b.waitFor(figure, "chart", 15000); await until(async () => (await dots()) > 30, "dots");
  const centre = await b.eval(`(() => { const s = ${figure}.querySelector('.recharts-surface'); s.scrollIntoView({block:'center'}); const r = s.getBoundingClientRect(); return { x: r.left + r.width * 0.5, y: r.top + r.height * 0.5 }; })()`);
  await sleep(200);
  check("no tooltip before interacting", !(await b.has(`document.querySelector('[data-chart-tooltip]')`)));
  await b.send("Input.dispatchMouseEvent", { type: "mouseMoved", x: centre.x - 40, y: centre.y }); await b.send("Input.dispatchMouseEvent", { type: "mouseMoved", x: centre.x, y: centre.y });
  await b.waitFor(`document.querySelector('[data-chart-tooltip]')`, "tooltip on hover");
  const tip = await b.eval(`document.querySelector('[data-chart-tooltip]').innerText`);
  check("hover shows the date, the reading and the trend", /\d{4}/.test(tip) && tip.includes("Weight") && tip.includes("7-day trend") && tip.includes("kg"), tip);
  await b.shot("weight-tooltip-hover-1280");
  await b.viewport(390); await b.goto("/weight"); await b.waitFor(figure, "chart", 15000); await until(async () => (await dots()) > 30, "dots");
  const c2 = await b.eval(`(() => { const s = ${figure}.querySelector('.recharts-surface'); s.scrollIntoView({block:'center'}); const r = s.getBoundingClientRect(); return { x: r.left + r.width * 0.6, y: r.top + r.height * 0.5 }; })()`);
  await sleep(200);
  await b.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: c2.x, y: c2.y }] }); await sleep(80);
  await b.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  await until(() => b.has(`document.querySelector('[data-chart-tooltip]')`), "tooltip on tap", 4000).catch(() => {});
  const tapTip = await b.eval(`document.querySelector('[data-chart-tooltip]')?.innerText ?? ''`);
  check("tapping the chart shows the tooltip", tapTip.includes("kg"), tapTip);
  await b.shot("weight-tooltip-tap-390");

  // ------------------------------------------------------------------------------------------------
  begin("Summary, weekly and monthly tables agree with the API");
  await b.goto("/weight"); await b.waitFor(hasText("Monthly trend"), "tables");
  const sum = (await api.get("/api/v1/weight/summary")).body;
  const txt = await b.text();
  check("current weight matches the summary", txt.includes(`${Number(sum.current.weightKg)}`) && sum.current.date === today, J(sum.current));
  check("7 and 30 day changes are shown with a real minus sign", (sum.change.last7Days.changeKg < 0) === txt.includes("−") || txt.includes("+"), J(sum.change));
  const weekRows = await b.eval(`[...document.querySelectorAll('section[aria-label="Weekly averages"] [data-period-row]')].map((r) => r.innerText)`);
  const monday = (() => { const [y, m, d] = today.split("-").map(Number); const day = new Date(Date.UTC(y, m - 1, d)).getUTCDay(); return addDays(today, -((day + 6) % 7)); })();
  const weeksApi = await seriesApi(addDays(monday, -49), today, "WEEKLY");
  check(`weekly table has a row per API week (${weeksApi.points.length}), newest first`, weekRows.length === weeksApi.points.length && weekRows[0].includes(fmt(weeksApi.points.at(-1).averageKg)), J(weekRows[0]));
  const monthRows = await b.eval(`[...document.querySelectorAll('section[aria-label="Monthly averages"] [data-period-row]')].map((r) => r.innerText)`);
  const monthStart = `${addDays(today, 0).slice(0, 8)}01`;
  const [my, mm2] = monthStart.split("-").map(Number); const from12 = new Date(Date.UTC(my, mm2 - 1 - 11, 1)).toISOString().slice(0, 10);
  const monthsApi = await seriesApi(from12, today, "MONTHLY");
  check(`monthly table has a row per API month (${monthsApi.points.length}), newest first`, monthRows.length === monthsApi.points.length && monthRows[0].includes(fmt(monthsApi.points.at(-1).averageKg)), J(monthRows[0]));
  await b.shot("weight-tables-390");

  // ------------------------------------------------------------------------------------------------
  begin("Target: set, progress, dashed line, clear");
  await b.type(targetInput, "80", "target"); await b.clickText("button", "Set target");
  await until(async () => (await api.get("/api/v1/weight/target")).status === 200, "target saved");
  check("target stored as 80 kg", (await api.get("/api/v1/weight/target")).body.targetWeightKg === 80);
  await b.waitFor(`document.querySelector('[data-target-progress]')`, "progress view");
  const s2 = (await api.get("/api/v1/weight/summary")).body;
  const prog = await b.eval(`document.querySelector('[data-target-progress]').innerText`);
  check("progress text agrees with the API (direction, remaining, percent)", prog.includes(`${Number(s2.progress.remainingKg)} kg to go`) && prog.includes(`${Number(s2.progress.percent)}%`) && prog.includes("Losing"), `${prog} vs ${J(s2.progress)}`);
  check("the progress bar carries the percentage", (await b.eval(`document.querySelector('[role=progressbar]').getAttribute('aria-valuenow')`)) === String(s2.progress.percent));
  await until(() => b.has(`${figure}?.querySelector('.recharts-reference-line')`), "reference line");
  check("a dashed target line is drawn and named in the legend and table caption", await b.has(`${figure}.querySelector('.recharts-reference-line line')?.getAttribute('stroke-dasharray')`) && (await b.text()).includes("Target") && await b.has(`${figure}.querySelector('caption')?.textContent.includes('Target: 80 kg')`));
  await b.shot("weight-target-390");
  const clear = () => b.click(`document.querySelector('form[aria-label="Target weight"]').querySelector('button[aria-label*="Clear target weight"]')`, "Clear target");
  await clear();
  check("one tap on Clear only arms it (target still exists)", (await api.get("/api/v1/weight/target")).status === 200);
  await clear();
  await until(async () => (await api.get("/api/v1/weight/target")).status === 404, "target cleared");
  await b.waitFor(hasText("No target set"), "no-target state");
  check("cleared: the API says 404, the page says no target, the dashed line is gone", !(await b.has(`${figure}.querySelector('.recharts-reference-line')`)));
  await b.type(targetInput, "999", "bad target"); await b.clickText("button", "Set target");
  await b.waitFor(hasText("20 to 500 kg"), "validation message");
  check("an out-of-range target is rejected with a message and nothing is saved", (await api.get("/api/v1/weight/target")).status === 404);

  // ------------------------------------------------------------------------------------------------
  begin("Entries: inline edit, two-tap delete, pagination, long notes");
  const yday = addDays(today, -1);
  await b.goto("/weight"); await b.waitFor(rowOf(yday), "yesterday's row");
  const noteEl = await b.eval(`(() => { const row = ${rowOf(yday)}; const n = [...row.querySelectorAll('span')].find((s) => s.textContent.startsWith('unbroken')); const r = n.getBoundingClientRect(); return { right: r.right, w: innerWidth, lines: Math.round(r.height / parseFloat(getComputedStyle(n).lineHeight)) }; })()`);
  check("a long note with an unbroken run wraps and stays on screen (390px)", noteEl.right <= noteEl.w && noteEl.lines >= 2, J(noteEl));
  const o3 = await b.overflow(); check("no horizontal overflow with the long note", !o3.overflow && o3.offenders.length === 0, J(o3));
  await b.click(`${rowOf(yday)}.querySelector('button[aria-label^="Edit entry"]')`, "Edit yesterday");
  await b.waitFor(`${rowOf(yday)}.querySelectorAll('input').length === 2`, "edit fields");
  check("the edit form is pre-filled with the current weight and note", (await b.eval(`${rowOf(yday)}.querySelectorAll('input')[0].value`)) === "88.1" && (await b.eval(`${rowOf(yday)}.querySelectorAll('input')[1].value`)) === longNote);
  await b.type(`${rowOf(yday)}.querySelectorAll('input')[0]`, "abc", "bad weight"); await b.clickText("button", "Save");
  check("an invalid edit shows a message and does not save", await b.has(`${rowOf(yday)}.innerText.includes('20 to 500 kg')`) && (await api.get("/api/v1/weight-entries?size=3")).body.items.find((e) => e.date === yday).weightKg === 88.1);
  await b.type(`${rowOf(yday)}.querySelectorAll('input')[0]`, "87.6", "weight"); await b.type(`${rowOf(yday)}.querySelectorAll('input')[1]`, "edited note", "note");
  await b.clickText("button", "Save");
  await until(async () => (await api.get("/api/v1/weight-entries?size=3")).body.items.find((e) => e.date === yday)?.weightKg === 87.6, "edit saved");
  await b.waitFor(`${rowOf(yday)}?.innerText.includes('87.6 kg') && ${rowOf(yday)}.innerText.includes('edited note')`, "row updated");
  check("the edit was saved (API) and the row shows it", (await api.get("/api/v1/weight-entries?size=3")).body.items.find((e) => e.date === yday).notes === "edited note");
  const total = (await api.get("/api/v1/weight-entries")).body.totalItems;
  const delBtn = () => b.click(`[...${rowOf(yday)}.querySelectorAll('button')].at(-1)`, "Delete yesterday");
  await delBtn();
  check("one tap only arms the delete (entry still there)", (await api.get(`/api/v1/weight-entries?size=100`)).body.items.some((e) => e.date === yday));
  await delBtn();
  await until(async () => (await api.get("/api/v1/weight-entries")).body.totalItems === total - 1, "entry deleted");
  await b.waitFor(`!${rowOf(yday)}`, "row gone");
  check("deleted: gone from the API and from the list", !(await api.get("/api/v1/weight-entries?size=100")).body.items.some((e) => e.date === yday));
  // pagination
  const totalPages = (await api.get("/api/v1/weight-entries?size=10")).body.totalPages;
  check("the list is paged by 10 with page links", totalPages > 1 && await b.has(hasText(`Page 1 of ${totalPages}`)) && (await b.eval(`document.querySelectorAll('[data-entry-row]').length`)) === 10);
  await b.clickText("a", "Next");
  await b.waitFor(`location.search.includes('page=1')`, "page 1");
  const p1 = (await api.get("/api/v1/weight-entries?page=1&size=10")).body.items.map((e) => e.date);
  await until(async () => J(await b.eval(`[...document.querySelectorAll('[data-entry-row]')].map((r) => r.dataset.date)`)) === J(p1), "page 1 rows");
  check("page 2 shows the next ten entries, in the API's order, and keeps the range", (await b.url()).includes("range=90d"));

  // ------------------------------------------------------------------------------------------------
  begin("Navigation");
  await b.viewport(1280, 900); await b.goto("/dashboard");
  check("Weight is a live link in the sidebar", await b.has(`document.querySelector('a[href="/weight"]')`));
  check("the other 'Soon' items are still not links", !(await b.has(`document.querySelector('a[href="/tasks"], a[href="/habits"], a[href="/goals"], a[href="/calendar"], a[href="/dsa"]')`)));
  await b.click(`document.querySelector('a[href="/weight"]')`, "sidebar Weight");
  await b.waitFor(`location.pathname === '/weight'`, "weight page");
  check("the Weight link is marked current on /weight", await b.has(`document.querySelector('a[href="/weight"]').getAttribute('aria-current') === 'page'`));
  check("the page title is set", (await b.eval("document.title")).includes("Weight"));

  // ------------------------------------------------------------------------------------------------
  begin("No horizontal overflow on the populated page at 390, 768 and 1280 px");
  for (const w of [390, 768, 1280]) {
    await b.viewport(w, 900); await b.goto("/weight"); await b.waitFor(figure, "chart", 15000); await until(async () => (await dots()) > 30, "dots");
    const o = await b.overflow();
    check(`${w}px: no overflow`, !o.overflow && o.offenders.length === 0, J(o));
    const chartBox = await b.eval(`(() => { const r = ${figure}.getBoundingClientRect(); return { l: r.left, r: r.right, w: innerWidth }; })()`);
    check(`${w}px: the chart fits inside the viewport`, chartBox.l >= 0 && chartBox.r <= chartBox.w, J(chartBox));
    const small = await b.eval(`[...document.querySelectorAll('main a, main button')].filter((e) => __h.visible(e) && e.getBoundingClientRect().height < 40 && !e.closest('nav[aria-label=Sidebar]')).map((e) => (e.getAttribute('aria-label') || e.textContent).trim().slice(0, 30))`);
    check(`${w}px: touch targets are at least 40px tall`, small.length === 0, J(small));
    await b.shot(`weight-full-${w}`);
  }

  // ------------------------------------------------------------------------------------------------
  begin("Backend unavailable, then recovery");
  await b.viewport(390);
  await stopBackend();
  check("backend is down", !(await backendHealthy()));
  await b.goto("/weight");
  await b.waitFor(hasText("We can't reach the server"), "unavailable panel", 15000);
  check("the page shows the unavailable panel, not a login redirect or a crash", (await b.url()).startsWith("/weight") && await b.has(`document.querySelector('[role=alert]')`));
  await b.shot("weight-unavailable-390");
  check("backend restarted", await startBackend());
  await b.clickText("button", "Try again");
  await b.waitFor(hasText("Weekly averages"), "page recovered", 20000);
  check("after the backend returns, Try again brings the page back with the data", await b.has(figure));
} catch (e) { check("section completed without error", false, e.message); await b.shot("FAILED-run6-weight"); }
b.close();
summary();
