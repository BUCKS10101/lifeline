// Part 9: the sleep, water and protein pages and the dashboard's wellness card, driven like a user and confirmed against the
// API and against numbers worked out independently from the seed.
import { execSync } from "node:child_process";
import { Browser, begin, check, summary, registerUser, sleep, backendPid } from "../lib.mjs";
const J = JSON.stringify; const PW = "a long enough password"; const TZ = "Asia/Kolkata";
const email = `wpages${Date.now()}@example.com`;
const api = await registerUser(email, PW, "Asha Nair", TZ);

const addDays = (iso, n) => { const [y, m, d] = iso.split("-").map(Number); return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10); };
const today = new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const dayAgo = (i) => addDays(today, -i);
const fmtNum = (v) => String(Number(Number(v).toFixed(2)));
const fmtMin = (m) => { const h = Math.floor(m / 60), r = m % 60; return h === 0 ? `${r} min` : r === 0 ? `${h} h` : `${h} h ${r} min`; };
const fmtMl = (ml) => (ml < 1000 ? `${ml} ml` : `${fmtNum(ml / 1000)} L`);
const fmtG = (g) => `${g} g`;
const hasText = (t) => `document.body.innerText.includes(${J(t)})`;
const figure = `document.querySelector('figure[data-chart]')`;
const points = () => b.eval(`Number(${figure}?.getAttribute('data-points') ?? -1)`);
const tableRows = () => b.eval(`${figure} ? [...${figure}.querySelectorAll('table tbody tr')].map((r) => [...r.children].map((c) => c.textContent)) : null`);
const tableHead = () => b.eval(`${figure} ? [...${figure}.querySelectorAll('table thead th')].map((c) => c.textContent) : null`);
const averageText = () => b.eval(`document.querySelector('[data-average]')?.innerText.replace(/\\s+/g, ' ').trim() ?? null`);
const until = async (fn, label, ms = 10000) => { const t = Date.now(); while (Date.now() - t < ms) { if (await fn()) return true; await sleep(200); } throw new Error("timeout: " + label); };
/** Headless Chrome cannot use the native pickers, so values are set the way the browser does when a picker commits. */
const setValue = (sel, value) => b.eval(`(() => { const el = ${sel}; Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, ${J(value)}); el.dispatchEvent(new Event('input', { bubbles: true })); })()`);
const input = (name) => `document.querySelector('input[name=${name}]')`;
const submitOf = (formLabel) => `document.querySelector('form[aria-label=${J(formLabel)}] button[type=submit]')`;

// ---- the seed: 25 mornings, and the numbers expected from it, worked out here and not asked of the app -----------------
const PAIRS = [["23:00", "07:00", 480], ["23:30", "07:15", 465], ["00:15", "07:40", 445], ["22:45", "07:00", 495]];
const LABELS = ["Whey shake", "Chicken breast", "Greek yogurt"];
const nightMinutes = (i) => PAIRS[i % 4][2];
const NIGHTS = 25;   // mornings 0 to 24 days ago are seeded; anything older has no entries at all
// Older mornings (14 and more days ago) are deliberately different from recent ones, so an average that mixes up the
// current period and the previous one cannot go unnoticed.
const waterDrinks = (i) => (i >= 14 ? [250, 500] : [250, 500, i % 2 ? 330 : 250]);
const proteinLabelled = (i) => (i >= 14 ? 10 : 25) + (i % 4) * 10;
const waterTotal = (i) => (i >= NIGHTS || i % 7 === 6 ? null : waterDrinks(i).reduce((a, x) => a + x, 0));
const proteinTotal = (i) => (i >= NIGHTS || i % 5 === 4 ? null : proteinLabelled(i) + 30);
/** Oldest first, for the last {days} days ending today; days without entries are 0, like the API. */
const dailySeries = (fn, days) => Array.from({ length: days }, (_, k) => fn(days - 1 - k) ?? 0);
const mean = (values) => { const v = values.filter((x) => x > 0); return v.length === 0 ? null : { amount: Math.round(v.reduce((a, b) => a + b, 0) / v.length), days: v.length }; };
const range = (from, to) => Array.from({ length: to - from + 1 }, (_, k) => from + k);

const b = new Browser(); await b.launch();
try {
  await b.viewport(390);
  await b.goto("/login");
  await b.type(input("email"), email, "email"); await b.type(input("password"), PW, "password");
  await b.clickText("button", "Log in"); await b.waitFor(`location.pathname==='/dashboard'`, "dashboard");

  // ------------------------------------------------------------------------------------------------
  begin("Empty states on all three pages and on the dashboard card (a brand-new user, 390px)");
  for (const [path, title, emptyChart, form, noEntries] of [
    ["/wellness/sleep", "Sleep", "No nights in this range", "Log sleep", "No nights logged yet"],
    ["/wellness/water", "Water", "No water logged in this range", "Custom amount", "Nothing logged today"],
    ["/wellness/protein", "Protein", "No protein logged in this range", "Protein entry", "Nothing logged today"]]) {
    await b.goto(path); await b.waitFor(hasText(title), path); await sleep(500);
    check(`${title}: empty chart state, empty list state and no chart drawn`, await b.has(hasText(emptyChart)) && await b.has(hasText(noEntries)) && !(await b.has(figure)), path);
    check(`${title}: the log form is there and the page has a link back to Today`, await b.has(`document.querySelector('form[aria-label=${J(form)}]')`) && await b.has(`document.querySelector('main a[href="/wellness"]')`));
    const o = await b.overflow(); check(`${title}: no horizontal overflow`, !o.overflow && o.offenders.length === 0, J(o));
  }
  await b.goto("/dashboard"); await b.waitFor(`document.querySelector('[data-wellness-card]')`, "wellness card");
  check("dashboard card with nothing logged says so and offers Log", await b.has(hasText("Nothing logged today")) && await b.has(`document.querySelector('[data-wellness-card] a[href="/wellness"]')?.textContent.trim() === 'Log'`));
  await b.shot("wellness-pages-empty-dashboard-390");

  // ------------------------------------------------------------------------------------------------
  for (let i = 0; i < NIGHTS; i++) {
    await api.put(`/api/v1/sleep-entries/${dayAgo(i)}`, { bedtime: PAIRS[i % 4][0], wakeTime: PAIRS[i % 4][1] });
    if (waterTotal(i) !== null) for (const ml of waterDrinks(i)) await api.post("/api/v1/water-entries", { amountMl: ml, date: dayAgo(i) });
    if (proteinTotal(i) !== null) { await api.post("/api/v1/protein-entries", { grams: proteinLabelled(i), label: LABELS[i % 3], date: dayAgo(i) }); await api.post("/api/v1/protein-entries", { grams: 30, date: dayAgo(i) }); }
  }
  await api.put("/api/v1/wellness/preferences", { sleepEnabled: true, waterEnabled: true, proteinEnabled: true, waterGoalMl: 2500, proteinGoalG: 140, sleepGoalMinutes: 480 });

  // ------------------------------------------------------------------------------------------------
  begin("Today lines and the dashboard card (1280px)");
  await b.viewport(1280, 900);
  await b.goto("/wellness"); await b.waitFor(`document.querySelector('[data-metric=sleep]')`, "lines");
  await b.click(`document.querySelector('[data-metric=water] a')`, "water link"); await b.waitFor(`location.pathname === '/wellness/water'`, "water page");
  check("tapping a Today line opens that metric's page", true);
  await b.goto("/wellness");
  await b.click(`document.querySelector('[data-metric=water] button[aria-label^="Add 250"]')`, "+250 on Today");
  await until(async () => (await api.get("/api/v1/water-entries")).body.entries.length === 4, "+250 saved");
  check("the line's own button still adds and stays on Today (the link and the button are separate)", (await b.url()) === "/wellness");
  await api.del(`/api/v1/water-entries/${(await api.get("/api/v1/water-entries")).body.entries[0].id}`);   // put today's water back to the seed

  await b.goto("/dashboard"); await b.waitFor(`document.querySelector('[data-wellness-card]')`, "card");
  const t = (await api.get("/api/v1/wellness/today")).body;
  const cardRows = await b.eval(`Object.fromEntries([...document.querySelectorAll('[data-wellness-card] [data-wellness-row]')].map((r) => [r.dataset.wellnessRow, r.innerText.replace(/\\s+/g, ' ').trim()]))`);
  check("the card shows one line per metric, with today's values from the API and the goals", cardRows.sleep === `Sleep ${fmtMin(t.sleep.entry.durationMinutes)} of 8 h` && cardRows.water === `Water ${fmtMl(t.water.totalMl)} of 2.5 L` && cardRows.protein === `Protein ${fmtG(t.protein.totalG)} of 140 g`, J(cardRows));
  check("no inline logging on the dashboard: the card's only control is the Log link", (await b.eval(`document.querySelectorAll('[data-wellness-card] button, [data-wellness-card] input').length`)) === 0 && await b.has(`document.querySelector('[data-wellness-card] a[href="/wellness"]')`));
  const gridCols = await b.eval(`(() => { const card = document.querySelector('[data-wellness-card]'); const grid = card.closest('.grid'); const cols = getComputedStyle(grid).gridTemplateColumns.split(' ').length; const cards = [...grid.children].map((c) => Math.round(c.getBoundingClientRect().top)); return { cols, tops: cards }; })()`);
  check("the Today section is two columns wide: four cards form a 2 x 2 grid", gridCols.cols === 2 && gridCols.tops.length === 4 && gridCols.tops[0] === gridCols.tops[1] && gridCols.tops[2] === gridCols.tops[3] && gridCols.tops[2] > gridCols.tops[0], J(gridCols));
  await b.shot("wellness-dashboard-1280");
  await b.viewport(768, 900); await b.goto("/dashboard"); await b.waitFor(`document.querySelector('[data-wellness-card]')`, "card");
  const cols768 = await b.eval(`getComputedStyle(document.querySelector('[data-wellness-card]').closest('.grid')).gridTemplateColumns.split(' ').length`);
  await b.viewport(390, 900); await b.goto("/dashboard"); await b.waitFor(`document.querySelector('[data-wellness-card]')`, "card");
  const cols390 = await b.eval(`getComputedStyle(document.querySelector('[data-wellness-card]').closest('.grid')).gridTemplateColumns.split(' ').length`);
  check("two columns at 768px, one column on a phone", cols768 === 2 && cols390 === 1, J({ cols768, cols390 }));

  // ------------------------------------------------------------------------------------------------
  begin("Sleep page: chart, ranges, average, tooltip");
  await b.viewport(1280, 900);
  await b.goto("/wellness/sleep"); await b.waitFor(figure, "chart", 15000);
  const s30 = (await api.get(`/api/v1/sleep/series?from=${dayAgo(29)}&to=${today}`)).body;
  await until(async () => (await points()) === s30.points.length, "points");
  check(`default range is 30 days: ${NIGHTS} logged nights, as the API returns`, (await points()) === NIGHTS && s30.points.length === NIGHTS);
  check("the 30d link is marked current", await b.has(`document.querySelector('nav[aria-label="Range"] a[aria-current="true"]')?.textContent.trim() === '30d'`));
  const rows = await tableRows();
  check("the table's columns are Date and Sleep", J(await tableHead()) === J(["Date", "Sleep"]));
  check("each night's duration in the table equals the seed, oldest first (hours and minutes)", J(rows.map((r) => r[1])) === J(range(0, NIGHTS - 1).reverse().map((i) => fmtMin(nightMinutes(i)))), J(rows.slice(0, 3)));
  check("the goal is drawn as a dashed line and named in the caption", await b.has(`${figure}.querySelector('.recharts-reference-line line')?.getAttribute('stroke-dasharray')`) && await b.has(`${figure}.querySelector('caption')?.textContent.includes('Goal: 8 h')`));
  check("the picture is hidden from assistive technology", await b.has(`${figure}.querySelector('div[aria-hidden=true] .recharts-surface')`));
  const avg30 = `Average ${fmtMin(Math.round(range(0, NIGHTS - 1).map(nightMinutes).reduce((a, x) => a + x, 0) / NIGHTS))} over ${NIGHTS} nights.`;
  check("the average text equals the API and the seed (no previous period yet)", (await averageText()) === avg30 && s30.average.entries === NIGHTS && s30.previousAverage === null, await averageText());
  await b.shot("sleep-page-1280");
  await b.clickText("a", "14d"); await b.waitFor(`location.search.includes('range=14d')`, "URL");
  await until(async () => (await points()) === 14, "14 points");
  const s14 = (await api.get(`/api/v1/sleep/series?from=${dayAgo(13)}&to=${today}`)).body;
  const m14 = Math.round(range(0, 13).map(nightMinutes).reduce((a, x) => a + x, 0) / 14), p14 = Math.round(range(14, 24).map(nightMinutes).reduce((a, x) => a + x, 0) / 11);
  check("14d: 14 nights, and the average compares with the 14 days before (11 nights logged)", (await averageText()) === `Average ${fmtMin(m14)} over 14 nights. Previous 14 days: ${fmtMin(p14)} over 11 nights.` && s14.previousAverage.entries === 11 && s14.previousAverage.durationMinutes === p14, await averageText());
  await b.goto("/wellness/sleep?range=1y"); await b.waitFor(figure, "1y", 15000);
  check("1y (shared link): every night", (await points()) === NIGHTS);
  await b.goto("/wellness/sleep?range=nonsense"); await b.waitFor(figure, "fallback", 15000);
  check("an unknown ?range= falls back to 30 days", await b.has(`document.querySelector('nav[aria-label="Range"] a[aria-current="true"]')?.textContent.trim() === '30d'`));
  await sleep(500);
  const centre = await b.eval(`(() => { const s = ${figure}.querySelector('.recharts-surface'); s.scrollIntoView({block:'center'}); const r = s.getBoundingClientRect(); return { x: r.left + r.width * 0.5, y: r.top + r.height * 0.5 }; })()`);
  await sleep(200);
  await b.send("Input.dispatchMouseEvent", { type: "mouseMoved", x: centre.x - 30, y: centre.y }); await b.send("Input.dispatchMouseEvent", { type: "mouseMoved", x: centre.x, y: centre.y });
  await b.waitFor(`document.querySelector('[data-chart-tooltip]')`, "tooltip");
  const tip = await b.eval(`document.querySelector('[data-chart-tooltip]').innerText`);
  check("hovering shows the date and the duration as hours and minutes", /\d{4}/.test(tip) && /\d h/.test(tip) && tip.includes("Sleep"), tip);

  begin("Sleep page: logging, replacing, editing and deleting a night");
  await b.goto("/wellness/sleep"); await b.waitFor(input("bedtime"), "form");
  check("the log form has a date and two times", J(await b.eval(`[...document.querySelectorAll('form[aria-label="Log sleep"] input')].map((i) => i.name)`)) === J(["date", "bedtime", "wakeTime"]) && (await b.eval(`${input("date")}.value`)) === today);
  const logDay = dayAgo(30);
  await setValue(input("date"), logDay); await setValue(input("bedtime"), "23:10"); await setValue(input("wakeTime"), "06:50");
  const preview = await b.eval(`document.querySelector('[data-sleep-preview]').innerText.trim()`);
  await b.click(submitOf("Log sleep"), "Log sleep");
  await b.waitFor(`document.querySelector('[role=status]')`, "saved notice");
  const saved = (await api.get(`/api/v1/sleep-entries?from=${logDay}&to=${logDay}`)).body.items[0];
  check(`a night 30 days ago was saved; the preview (${preview}) equals the server's duration (${fmtMin(saved.durationMinutes)}), and the notice says so`, preview === fmtMin(saved.durationMinutes) && saved.bedtime === "23:10" && (await b.eval(`document.querySelector('[role=status]').innerText`)).includes(fmtMin(saved.durationMinutes)), J(saved));
  await b.goto("/wellness/sleep?range=90d"); await b.waitFor(figure, "90d", 15000);
  check("with a 90d range the new night is on the chart (26 nights, as the API says)", (await points()) === NIGHTS + 1 && (await api.get(`/api/v1/sleep/series?from=${dayAgo(89)}&to=${today}`)).body.points.length === NIGHTS + 1);
  await setValue(input("date"), today); await setValue(input("bedtime"), "22:00"); await setValue(input("wakeTime"), "06:00");
  await b.click(submitOf("Log sleep"), "Replace today");
  await until(async () => (await api.get(`/api/v1/sleep-entries?from=${today}&to=${today}`)).body.items[0].durationMinutes === 480 && (await api.get(`/api/v1/sleep-entries?from=${today}&to=${today}`)).body.items[0].bedtime === "22:00", "replaced");
  check("logging a morning that already has a night replaces it (still one for the day)", (await api.get("/api/v1/sleep-entries")).body.totalItems === NIGHTS + 1);
  await setValue(input("bedtime"), "11:59"); await setValue(input("wakeTime"), "08:00");
  await b.click(submitOf("Log sleep"), "Log invalid"); await sleep(600);
  check("an implausible night shows the server's message and saves nothing", (await b.eval(`document.querySelector('form[aria-label="Log sleep"] [role=alert]')?.innerText ?? ''`)).includes("longer than 20 hours") && (await api.get(`/api/v1/sleep-entries?from=${today}&to=${today}`)).body.items[0].bedtime === "22:00");
  const list = (await api.get("/api/v1/sleep-entries?size=10")).body;
  const nightRows = await b.eval(`document.querySelectorAll('[data-night-row]').length`);
  check("recent nights are listed 10 per page, newest first, with page links", nightRows === 10 && list.totalPages === 3 && await b.has(hasText("Page 1 of 3")) && (await b.eval(`document.querySelector('[data-night-row]').dataset.date`)) === today);
  const rowDate = dayAgo(1);
  await b.click(`document.querySelector('[data-night-row][data-date="${rowDate}"] button[aria-label^="Edit night"]')`, "Edit");
  await b.waitFor(`document.querySelector('[data-night-row][data-date="${rowDate}"] input[type=time]')`, "edit form");
  const inputs = `document.querySelector('[data-night-row][data-date="${rowDate}"]').querySelectorAll('input')`;
  check("the edit form is pre-filled with the night's times", (await b.eval(`${inputs}[0].value`)) === PAIRS[1][0] && (await b.eval(`${inputs}[1].value`)) === PAIRS[1][1]);
  await setValue(`${inputs}[0]`, "11:59"); await setValue(`${inputs}[1]`, "08:00");
  await b.clickText("button", "Save"); await sleep(600);
  check("an invalid edit shows the server's message and changes nothing", (await b.eval(`document.querySelector('[data-night-row][data-date="${rowDate}"]').innerText`)).includes("longer than 20 hours") && (await api.get(`/api/v1/sleep-entries?from=${rowDate}&to=${rowDate}`)).body.items[0].durationMinutes === 465);
  await setValue(`${inputs}[0]`, "22:00"); await setValue(`${inputs}[1]`, "07:00");
  await b.clickText("button", "Save");
  await until(async () => (await api.get(`/api/v1/sleep-entries?from=${rowDate}&to=${rowDate}`)).body.items[0].durationMinutes === 540, "edit saved");
  await until(async () => (await b.eval(`document.querySelector('[data-night-row][data-date="${rowDate}"]')?.innerText.includes('9 h')`)), "row shows 9 h");
  check("a valid edit was saved (API) and the row shows 9 h", true);
  const delDate = dayAgo(2);
  const del = () => b.click(`[...document.querySelector('[data-night-row][data-date="${delDate}"]').querySelectorAll('button')].at(-1)`, "Delete night");
  await del();
  check("one tap only arms the delete (the night still exists)", (await api.get(`/api/v1/sleep-entries?from=${delDate}&to=${delDate}`)).body.items.length === 1);
  await del();
  await until(async () => (await api.get(`/api/v1/sleep-entries?from=${delDate}&to=${delDate}`)).body.items.length === 0, "deleted");
  await b.waitFor(`!document.querySelector('[data-night-row][data-date="${delDate}"]')`, "row gone");
  check("deleted: gone from the API and from the list", true);
  await b.clickText("a", "Next"); await b.waitFor(`location.search.includes('page=1')`, "page 1");
  const p1 = (await api.get("/api/v1/sleep-entries?page=1&size=10")).body.items.map((e) => e.date);
  await until(async () => J(await b.eval(`[...document.querySelectorAll('[data-night-row]')].map((r) => r.dataset.date)`)) === J(p1), "page 1 rows");
  check("page 2 shows the next ten nights in the API's order and keeps the range", (await b.url()).includes("range=90d"), await b.url());   // the range in effect since the 90d step above

  // ------------------------------------------------------------------------------------------------
  for (const kind of ["water", "protein"]) {
    const water = kind === "water"; const total = water ? waterTotal : proteinTotal; const fmt = water ? fmtMl : fmtG; const Title = water ? "Water" : "Protein";
    const goal = water ? 2500 : 140; const dayApi = async (d = "") => (await api.get(`/api/v1/${kind}-entries${d ? `?date=${d}` : ""}`)).body;
    const totalKey = water ? "totalMl" : "totalG"; const seriesApi = async (n) => (await api.get(`/api/v1/${kind}/series?from=${dayAgo(n - 1)}&to=${today}`)).body;

    begin(`${Title} page: chart, zero-filled days, goal, ranges, average, tooltip (1280px)`);
    await b.viewport(1280, 900); await b.goto(`/wellness/${kind}`); await b.waitFor(figure, "chart", 15000);
    await until(async () => (await points()) === 14, "14 bars");
    check("default range is 14 days, and the 14d link is marked current", (await points()) === 14 && await b.has(`document.querySelector('nav[aria-label="Range"] a[aria-current="true"]')?.textContent.trim() === '14d'`));
    const expected14 = dailySeries(total, 14);
    check("the table has one row per day, oldest first, and the totals equal the seed (days with nothing are zero)", J((await tableRows()).map((r) => r[1])) === J(expected14.map(fmt)), J((await tableRows()).slice(0, 4)));
    check("the table is headed Day and the metric", J(await tableHead()) === J(["Day", Title]));
    check("the goal is drawn as a dashed line and named in the caption", await b.has(`${figure}.querySelector('.recharts-reference-line line')?.getAttribute('stroke-dasharray')`) && await b.has(`${figure}.querySelector('caption')?.textContent.includes('Goal: ${fmt(goal)}')`));
    check("the picture is hidden from assistive technology", await b.has(`${figure}.querySelector('div[aria-hidden=true] .recharts-surface')`));
    const a14 = mean(expected14), prev = mean(dailySeries((i) => total(i + 14), 14));
    const s14 = await seriesApi(14);
    check("the average text equals the API and the seed (per day with an entry, and the previous 14 days)", (await averageText()) === `Average ${fmt(a14.amount)} on days with an entry (${a14.days} days). Previous 14 days: ${fmt(prev.amount)} (${prev.days} days).` && s14.average.amount === a14.amount && s14.previousAverage.days === prev.days, await averageText());
    await b.shot(`${kind}-page-1280`);
    await sleep(400);
    const centre = await b.eval(`(() => { const s = ${figure}.querySelector('.recharts-surface'); s.scrollIntoView({block:'center'}); const r = s.getBoundingClientRect(); return { x: r.left + r.width * 0.6, y: r.top + r.height * 0.5 }; })()`);
    await sleep(200);
    await b.send("Input.dispatchMouseEvent", { type: "mouseMoved", x: centre.x - 30, y: centre.y }); await b.send("Input.dispatchMouseEvent", { type: "mouseMoved", x: centre.x, y: centre.y });
    await b.waitFor(`document.querySelector('[data-chart-tooltip]')`, "tooltip");
    check("hovering a bar shows the day and its total", /\d{4}/.test(await b.eval(`document.querySelector('[data-chart-tooltip]').innerText`)) && (await b.eval(`document.querySelector('[data-chart-tooltip]').innerText`)).includes(Title));
    for (const [label, n] of [["7d", 7], ["30d", 30], ["90d", 90]]) {
      await b.goto(`/wellness/${kind}?range=${label}`); await b.waitFor(figure, label, 15000); await until(async () => (await points()) === n, `${label} points`);
      check(`${label}: ${n} bars, one per day`, (await points()) === n && (await tableRows()).length === n);
    }
    await b.goto(`/wellness/${kind}`); await b.clickText("a", "7d"); await b.waitFor(`location.search.includes('range=7d')`, "URL");
    check("the range links change the URL", true);

    begin(`${Title} page: logging, undo, delete, another day`);
    await b.goto(`/wellness/${kind}`); await b.waitFor(figure, "chart", 15000);
    const startTotal = (await dayApi())[totalKey];
    check("the header shows today's total and the goal", (await b.eval(`document.querySelector('main').innerText`)).includes(`Today: ${fmt(startTotal)} of ${fmt(goal)}`));
    const entryCount = async () => (await dayApi()).entries.length;
    const rowsOnPage = () => b.eval(`document.querySelectorAll('[data-entry-row]').length`);
    const n0 = await entryCount();
    check("today's entries are listed newest first, as many as the API has", (await rowsOnPage()) === n0 && n0 > 0);
    if (water) {
      await b.clickText("button", "+500 ml"); await until(async () => (await dayApi()).totalMl === startTotal + 500, "+500");
      await b.waitFor(`document.querySelector('[data-undo]')`, "undo");
      check("+500 ml added one entry and an Undo appears", (await entryCount()) === n0 + 1 && await b.has(hasText("Added 500 ml")));
      await b.clickText("button", "Undo"); await until(async () => (await dayApi()).totalMl === startTotal, "undone");
      check("Undo removed it", (await entryCount()) === n0);
      await b.clickText("button", "+250 ml"); await until(async () => (await dayApi()).totalMl === startTotal + 250, "+250");
      for (const bad of ["5", "5001", "abc", ""]) {
        await setValue(input("amountMl"), bad); await b.eval(`document.querySelector('form[aria-label="Custom amount"]').requestSubmit()`); await sleep(300);
        check(`custom amount "${bad}" is refused with a message and nothing is added`, await b.has(hasText("10 to 5000 ml")) && (await dayApi()).totalMl === startTotal + 250, bad);
      }
      await setValue(input("amountMl"), "330"); await b.click(submitOf("Custom amount"), "Add 330");
      await until(async () => (await dayApi()).totalMl === startTotal + 580, "330 added");
      check("a custom 330 ml was added", (await dayApi()).entries.some((e) => e.amountMl === 330));
    } else {
      const sug = (await api.get("/api/v1/protein/suggestions")).body;
      const chips = await b.eval(`[...document.querySelectorAll('[data-protein-chips] button')].map((x) => x.innerText.replace(/\\s+/g, ' ').trim())`);
      check("the chips are the person's own labels from the API, most used first, with their last grams", J(chips) === J(sug.map((s) => `${s.label} ${s.grams} g`)) && chips.length === 3, J(chips));
      await b.click(`document.querySelector('[data-protein-chips] button')`, "chip"); await until(async () => (await dayApi()).totalG === startTotal + sug[0].grams, "chip added");
      check("one tap on a chip added that label with those grams", (await dayApi()).entries[0].label === sug[0].label && (await dayApi()).entries[0].grams === sug[0].grams);
      await b.waitFor(`document.querySelector('[data-undo]')`, "undo"); await b.clickText("button", "Undo"); await until(async () => (await dayApi()).totalG === startTotal, "undone");
      check("Undo removed it", (await entryCount()) === n0);
      for (const bad of ["0", "501", "x", ""]) {
        await setValue(input("grams"), bad); await b.eval(`document.querySelector('form[aria-label="Protein entry"]').requestSubmit()`); await sleep(300);
        check(`grams "${bad}" is refused with a message and nothing is added`, await b.has(hasText("1 to 500 g")) && (await dayApi()).totalG === startTotal, bad);
      }
      await setValue(input("grams"), "42"); await setValue(input("label"), "Protein bar"); await b.click(submitOf("Protein entry"), "Add protein");
      await until(async () => (await dayApi()).totalG === startTotal + 42, "42 added");
      check("42 g labelled 'Protein bar' was added and shows in the list", (await dayApi()).entries.some((e) => e.grams === 42 && e.label === "Protein bar") && await b.has(hasText("Protein bar")));
    }
    // delete
    await until(async () => (await rowsOnPage()) === (await entryCount()), "list refreshed");
    const before = await entryCount();
    const delRow = `document.querySelector('[data-entry-row]')`;
    const delBtn = () => b.click(`[...${delRow}.querySelectorAll('button')].at(-1)`, "Delete entry");
    await delBtn();
    check("one tap on delete only arms it", (await entryCount()) === before);
    await delBtn(); await until(async () => (await entryCount()) === before - 1, "deleted");
    await until(async () => (await rowsOnPage()) === before - 1, "row gone");
    check("deleted: gone from the API and the list, and the total follows", (await dayApi())[totalKey] === (await dayApi()).entries.reduce((s, e) => s + (water ? e.amountMl : e.grams), 0));
    // another day
    const other = dayAgo(6);   // a day with no seed entries (water and protein skip i=6 / not i=6 for protein: use the seed's own value)
    const otherTotal = total(6) ?? 0;
    await setValue(input("day"), other); await b.waitFor(`location.search.includes('date=${other}')`, "URL has the date");
    await b.waitFor(hasText(`of ${fmt(goal)}`), "other day header");
    const od = await dayApi(other);
    check("choosing another day shows that day's entries and total (from the API), and keeps the range in the URL", od[totalKey] === otherTotal && (await b.eval(`document.querySelector('main').innerText`)).includes(`${fmt(od[totalKey])} of ${fmt(goal)}`) && (await b.url()).includes("range=") && (await rowsOnPage()) === od.entries.length, J([od[totalKey], otherTotal]));
    const todayBefore = (await dayApi())[totalKey];
    if (water) await b.clickText("button", "+250 ml"); else { await setValue(input("grams"), "18"); await b.click(submitOf("Protein entry"), "Add for the other day"); }
    await until(async () => (await dayApi(other))[totalKey] === otherTotal + (water ? 250 : 18), "added to the other day");
    check("logging while viewing another day adds to that day and not to today", (await dayApi())[totalKey] === todayBefore && await b.has(hasText(`Log ${kind} for`)));
    await setValue(input("day"), today); await b.waitFor(`!location.search.includes('date=')`, "back to today");
    check("choosing today again drops the date from the URL", true);
    await b.shot(`${kind}-page-after-1280`);
  }

  // ------------------------------------------------------------------------------------------------
  begin("Hidden metrics: their pages go back to Today, and the dashboard card follows");
  await api.put("/api/v1/wellness/preferences", { sleepEnabled: true, waterEnabled: false, proteinEnabled: true, waterGoalMl: 2500, proteinGoalG: 140, sleepGoalMinutes: 480 });
  await b.goto("/wellness/water"); await b.waitFor(`document.querySelector('[data-metric=sleep]')`, "redirect target");
  check("a hidden metric's page redirects to Today, which no longer lists it", (await b.url()) === "/wellness" && !(await b.has(`document.querySelector('[data-metric=water]')`)));
  await b.goto("/dashboard"); await b.waitFor(`document.querySelector('[data-wellness-card]')`, "card");
  check("the dashboard card lists only the visible metrics", J(await b.eval(`[...document.querySelectorAll('[data-wellness-card] [data-wellness-row]')].map((r) => r.dataset.wellnessRow)`)) === J(["sleep", "protein"]));
  await api.put("/api/v1/wellness/preferences", { sleepEnabled: false, waterEnabled: false, proteinEnabled: false });
  await b.goto("/dashboard"); await b.waitFor(hasText("Today's workout"), "dashboard"); await sleep(600);
  check("with every metric hidden there is no wellness card, and the other three cards remain", !(await b.has(`document.querySelector('[data-wellness-card]')`)) && (await b.eval(`document.querySelector('[data-wellness-card]') === null && document.querySelectorAll('main section:first-of-type [data-slot=card]').length`)) === 3);
  for (const k of ["sleep", "protein"]) { await b.goto(`/wellness/${k}`); await b.waitFor(hasText("Everything is hidden"), "all hidden"); check(`${k} page with everything hidden goes back to Today`, (await b.url()) === "/wellness"); }
  await api.put("/api/v1/wellness/preferences", { sleepEnabled: true, waterEnabled: true, proteinEnabled: true, waterGoalMl: 2500, proteinGoalG: 140, sleepGoalMinutes: 480 });

  // ------------------------------------------------------------------------------------------------
  begin("Responsive: overflow and touch targets at 390, 768 and 1280 px");
  for (const w of [390, 768, 1280]) {
    await b.viewport(w, 900);
    for (const [label, path, ready] of [["sleep", "/wellness/sleep", input("bedtime")], ["water", "/wellness/water", input("amountMl")], ["protein", "/wellness/protein", input("grams")], ["dashboard", "/dashboard", `document.querySelector('[data-wellness-card]')`]]) {
      await b.goto(path); await b.waitFor(ready, `${label} ready`, 15000);
      if (label !== "dashboard") await b.waitFor(figure, `${label} chart`, 15000);
      await sleep(500);
      const o = await b.overflow(); check(`${w}px ${label}: no horizontal overflow`, !o.overflow && o.offenders.length === 0, J(o));
      const small = await b.eval(`[...document.querySelectorAll('main a, main button')].filter((e) => __h.visible(e) && e.getBoundingClientRect().height < 40).map((e) => (e.getAttribute('aria-label') || e.textContent).trim().slice(0, 30))`);
      check(`${w}px ${label}: every button and link is at least 40px tall`, small.length === 0, J(small));
      if (label !== "dashboard") { const box = await b.eval(`(() => { const r = ${figure}.getBoundingClientRect(); return { l: r.left, r: r.right, w: innerWidth }; })()`); check(`${w}px ${label}: the chart fits inside the viewport`, box.l >= 0 && box.r <= box.w + 1, J(box)); }
      if (w === 390 || w === 768) await b.shot(`wellness-${label}-${w}`);
    }
  }

  // ------------------------------------------------------------------------------------------------
  begin("Loading state and backend unavailable on a metric page, then recovery");
  await b.viewport(1280, 900); await b.goto("/wellness"); await b.waitFor(`document.querySelector('[data-metric=water]')`, "Today");
  const pid = backendPid();
  execSync(`kill -STOP ${pid}`);
  try {
    await b.click(`document.querySelector('[data-metric=water] a')`, "water link");
    let sawLoading = false;
    for (let i = 0; i < 20 && !sawLoading; i++) { sawLoading = await b.has(`!!document.querySelector('[aria-busy="true"]')`); if (!sawLoading) await sleep(150); }
    check("the loading skeleton shows while the backend is slow", sawLoading);
    await b.waitFor(hasText("We can't reach the server"), "unavailable panel", 15000);
    check("a frozen backend shows the unavailable panel and the user stays on the water page", (await b.url()) === "/wellness/water");
    await b.shot("wellness-water-unavailable-1280");
  } finally { execSync(`kill -CONT ${pid}`); }
  await sleep(1500); await b.clickText("button", "Try again");
  await b.waitFor(figure, "recovered", 15000);
  check("Try again brings the page back with its chart", true);
} catch (e) { check("section completed without error", false, e.message); await b.shot("FAILED-run9-wellness-pages"); }
b.close();
summary();
