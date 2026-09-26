// Part 8: the /wellness Today view (sleep, water, protein), driven like a user and confirmed against the API.
import { execSync } from "node:child_process";
import { Browser, begin, check, summary, registerUser, sleep, stopBackend, startBackend, backendHealthy, backendPid } from "../lib.mjs";
const J = JSON.stringify; const PW = "a long enough password"; const TZ = "Asia/Kolkata";
const email = `wellness${Date.now()}@example.com`;
const api = await registerUser(email, PW, "Asha Nair", TZ);

const today = new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const fmtMin = (m) => { const h = Math.floor(m / 60), r = m % 60; return h === 0 ? `${r} min` : r === 0 ? `${h} h` : `${h} h ${r} min`; };
const fmtMl = (ml) => (ml < 1000 ? `${ml} ml` : `${Number((ml / 1000).toFixed(2))} L`);
const hasText = (t) => `document.body.innerText.includes(${J(t)})`;
const line = (m) => `document.querySelector('[data-metric=${m}]')`;
const valueOf = (m) => b.eval(`${line(m)}?.querySelector('[data-value]')?.innerText.replace(/\\s+/g, ' ').trim() ?? null`);
const rowText = (m) => b.eval(`${line(m)}?.innerText.replace(/\\s+/g, ' ').trim() ?? null`);
const btn = (m, text) => `[...${line(m)}.querySelectorAll('button')].find((x) => x.innerText.trim() === ${J(text)} || (x.getAttribute('aria-label') || '').startsWith(${J(text)}))`;
const sheet = `document.querySelector('[data-slot=sheet-content]')`;
const sheetBtn = (text) => `[...${sheet}.querySelectorAll('button')].find((x) => x.innerText.replace(/\\s+/g, ' ').trim().startsWith(${J(text)}))`;
const until = async (fn, label, ms = 10000) => { const t = Date.now(); while (Date.now() - t < ms) { if (await fn()) return true; await sleep(200); } throw new Error("timeout: " + label); };
/** Headless Chrome cannot use the native time picker, so the value is set the way the browser does when the picker commits. */
const setTime = (name, value) => b.eval(`(() => { const el = document.querySelector('input[name=${name}]'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, ${J(value)}); el.dispatchEvent(new Event('input', { bubbles: true })); })()`);
const waterDay = async () => (await api.get("/api/v1/water-entries")).body;
const proteinDay = async () => (await api.get("/api/v1/protein-entries")).body;
const todayApi = async () => (await api.get("/api/v1/wellness/today")).body;
const prefsApi = async () => (await api.get("/api/v1/wellness/preferences")).body;

const b = new Browser(); await b.launch();
try {
  await b.viewport(390);
  await b.goto("/login");
  await b.type(`document.querySelector('input[name=email]')`, email, "email"); await b.type(`document.querySelector('input[name=password]')`, PW, "password");
  await b.clickText("button", "Log in"); await b.waitFor(`location.pathname==='/dashboard'`, "dashboard");

  // ------------------------------------------------------------------------------------------------
  begin("Today view, empty (a brand-new user), 390px");
  await b.goto("/wellness");
  await b.waitFor(line("water"), "lines");
  const order = await b.eval(`[...document.querySelectorAll('[data-metric]')].map((l) => l.dataset.metric)`);
  check("one line per metric, in the order sleep, water, protein", J(order) === J(["sleep", "water", "protein"]), J(order));
  check("nothing logged: 'Not logged', 0 ml and 0 g", (await valueOf("sleep")) === "Not logged" && (await valueOf("water")) === "0 ml" && (await valueOf("protein")) === "0 g", J([await valueOf("sleep"), await valueOf("water"), await valueOf("protein")]));
  check("the primary actions are Log, +250 and +20", await b.has(btn("sleep", "Log")) && await b.has(btn("water", "Add 250")) && await b.has(btn("protein", "Add 20")));
  check("each line has at most a primary button and one quiet 'more' button", (await b.eval(`[...document.querySelectorAll('[data-metric]')].map((l) => l.querySelectorAll('button').length)`)).every((n) => n <= 2));
  check("no charts, no history, no forms on Today", !(await b.has(`document.querySelector('figure, svg.recharts-surface, table, form')`)) && !(await b.has(`document.querySelector('main h2, main ol')`)));
  check("the lines are plain text: no links yet (their pages do not exist)", !(await b.has(`document.querySelector('[data-metric] a')`)));
  await b.viewport(1280, 900); await b.goto("/wellness"); await b.waitFor(line("water"), "lines");
  check("Wellness is a live nav link in the sidebar and is marked as the current page", await b.has(`document.querySelector('a[href="/wellness"]')?.getAttribute('aria-current') === 'page'`));
  check("the other 'Soon' items are still not links", !(await b.has(`document.querySelector('a[href="/tasks"], a[href="/habits"], a[href="/goals"], a[href="/calendar"], a[href="/dsa"]')`)));
  await b.viewport(390);
  check("the page title is set", (await b.eval("document.title")).includes("Wellness"));
  const today0 = await todayApi();
  check("the server's 'today' is the user's local date", today0.date === today, today0.date);
  const o0 = await b.overflow(); check("no horizontal overflow", !o0.overflow && o0.offenders.length === 0, J(o0));
  await b.shot("wellness-empty-390");

  // ------------------------------------------------------------------------------------------------
  begin("Water: quick add, undo, the undo going away by itself");
  await b.click(btn("water", "Add 250"), "+250");
  await until(async () => (await waterDay()).totalMl === 250, "250 saved");
  await until(async () => (await valueOf("water")) === "250 ml", "line shows 250 ml");
  check("one tap added exactly one 250 ml entry", (await waterDay()).entries.length === 1 && (await waterDay()).entries[0].amountMl === 250);
  await b.waitFor(`${line("water")}.innerText.includes('Added 250 ml')`, "undo note");
  check("an Undo appears on that line", await b.has(btn("water", "Undo")));
  await b.click(btn("water", "Undo"), "Undo");
  await until(async () => (await waterDay()).totalMl === 0, "undone");
  await until(async () => (await valueOf("water")) === "0 ml", "line back to 0 ml");
  check("Undo removed the entry (API) and the line returned to 0 ml", (await waterDay()).entries.length === 0 && !(await b.has(btn("water", "Undo"))));
  await b.click(btn("water", "Add 250"), "+250 again");
  await until(async () => (await valueOf("water")) === "250 ml", "250 ml");
  await sleep(9500);
  check("the Undo goes away by itself after a few seconds", !(await b.has(btn("water", "Undo"))) && !(await b.has(`${line("water")}.innerText.includes('Added')`)));

  begin("Water sheet: +500, custom amount, validation");
  await b.click(btn("water", "More ways to log water"), "more");
  await b.waitFor(sheet, "sheet");
  check("the sheet offers +500 and a custom amount only (+250 is the button on the line, not repeated here)", !(await b.has(sheetBtn("+250"))) && await b.has(sheetBtn("+500")) && (await b.eval(`${sheet}.querySelectorAll('input').length`)) === 1);
  await b.shot("wellness-water-sheet-390");
  await b.click(sheetBtn("+500"), "+500");
  await until(async () => (await waterDay()).totalMl === 750, "750");
  await b.waitFor(`!${sheet}`, "sheet closed");
  await until(async () => (await valueOf("water")) === "750 ml", "750 ml shown");
  check("+500 in the sheet added 500 ml, the sheet closed and Undo is offered", await b.has(`${line("water")}.innerText.includes('Added 500 ml')`));
  await b.click(btn("water", "More ways to log water"), "more"); await b.waitFor(sheet, "sheet");
  for (const bad of ["5", "5001", "abc", ""]) {
    await b.type(`${sheet}.querySelector('input')`, bad, "custom"); await b.eval(`${sheet}.querySelector('form').requestSubmit()`);
    await sleep(300);
    check(`custom amount "${bad}" is refused with a message and nothing is added`, await b.has(`${sheet}.innerText.includes('10 to 5000 ml')`) && (await waterDay()).totalMl === 750, bad);
  }
  await b.type(`${sheet}.querySelector('input')`, "330", "custom");
  await b.click(`${sheet}.querySelector('form button[type=submit]')`, "Add custom");
  await until(async () => (await waterDay()).totalMl === 1080, "1080");
  await until(async () => (await valueOf("water")) === "1.08 L", "1.08 L shown");
  check("a custom 330 ml was added; the total shows as 1.08 L", (await waterDay()).entries.some((e) => e.amountMl === 330));

  // ------------------------------------------------------------------------------------------------
  begin("Protein: quick add, custom with a label, personal chips, undo");
  await b.click(btn("protein", "Add 20"), "+20");
  await until(async () => (await proteinDay()).totalG === 20, "20 g");
  await until(async () => (await valueOf("protein")) === "20 g", "20 g shown");
  check("+20 added one unlabelled 20 g entry", (await proteinDay()).entries[0].grams === 20 && (await proteinDay()).entries[0].label === null);
  await b.click(btn("protein", "More ways to log protein"), "more"); await b.waitFor(sheet, "sheet");
  check("a new user has no chips (there is no food database)", !(await b.has(`${sheet}.querySelector('[data-protein-chips]')`)));
  check("the sheet has grams and an optional label, and nothing else", (await b.eval(`${sheet}.querySelectorAll('input').length`)) === 2);
  for (const bad of ["0", "501", "x", ""]) {
    await b.type(`${sheet}.querySelector('input[name=grams]')`, bad, "grams"); await b.eval(`${sheet}.querySelector('form').requestSubmit()`); await sleep(300);
    check(`grams "${bad}" is refused with a message and nothing is added`, await b.has(`${sheet}.innerText.includes('1 to 500 g')`) && (await proteinDay()).totalG === 20, bad);
  }
  await b.type(`${sheet}.querySelector('input[name=grams]')`, "25", "grams"); await b.type(`${sheet}.querySelector('input[name=label]')`, "Whey shake", "label");
  await b.click(`${sheet}.querySelector('form button[type=submit]')`, "Add protein");
  await until(async () => (await proteinDay()).totalG === 45, "45 g");
  check("25 g labelled 'Whey shake' was added", (await proteinDay()).entries.some((e) => e.grams === 25 && e.label === "Whey shake"));
  await b.waitFor(`${line("protein")}.innerText.includes('Added 25 g')`, "note");
  await b.click(btn("protein", "More ways to log protein"), "more"); await b.waitFor(sheet, "sheet");
  await b.type(`${sheet}.querySelector('input[name=grams]')`, "30", "grams"); await b.type(`${sheet}.querySelector('input[name=label]')`, "whey shake", "label");
  await b.click(`${sheet}.querySelector('form button[type=submit]')`, "Add protein");
  await until(async () => (await proteinDay()).totalG === 75, "75 g");
  await sleep(1200);
  await b.click(btn("protein", "More ways to log protein"), "more"); await b.waitFor(sheet, "sheet");
  const suggestions = (await api.get("/api/v1/protein/suggestions")).body;
  const chips = await b.eval(`[...${sheet}.querySelectorAll('[data-protein-chips] button')].map((x) => x.innerText.replace(/\\s+/g, ' ').trim())`);
  check("one chip from the user's own history, as last typed, with the grams last used", J(chips) === J(suggestions.map((s) => `${s.label} ${s.grams} g`)) && chips.length === 1 && chips[0] === "whey shake 30 g", J(chips));
  await b.shot("wellness-protein-sheet-390");
  await b.click(`${sheet}.querySelector('[data-protein-chips] button')`, "chip");
  await until(async () => (await proteinDay()).totalG === 105, "105 g");
  check("one tap on the chip added 30 g with that label", (await proteinDay()).entries[0].grams === 30 && (await proteinDay()).entries[0].label === "whey shake");
  await until(async () => (await valueOf("protein")) === "105 g", "105 g shown");
  await b.click(btn("protein", "Undo"), "Undo chip");
  await until(async () => (await proteinDay()).totalG === 75, "undone to 75");
  await until(async () => (await valueOf("protein")) === "75 g", "75 g shown");
  check("Undo removed the chip's entry and the line followed", (await proteinDay()).entries.length === 3);
  const other = await registerUser(`wellness-other${Date.now()}@example.com`, PW, "Other", TZ);
  await other.post("/api/v1/protein-entries", { grams: 40, label: "Secret smoothie" });
  await b.goto("/wellness"); await b.waitFor(line("protein"), "lines");
  await b.click(btn("protein", "More ways to log protein"), "more"); await b.waitFor(sheet, "sheet");
  check("another person's labels never appear as chips", !(await b.has(`${sheet}.innerText.includes('Secret')`)));
  await b.key("Escape", "Escape", 27); await sleep(400);

  // ------------------------------------------------------------------------------------------------
  begin("Sleep: the sheet, the live preview, and agreement with the server");
  await b.goto("/wellness"); await b.waitFor(line("sleep"), "lines");
  await b.click(btn("sleep", "Log"), "Log");
  await b.waitFor(sheet, "sheet");
  check("the sheet has exactly two fields (went to sleep, woke up)", (await b.eval(`${sheet}.querySelectorAll('input').length`)) === 2 && (await b.eval(`${sheet}.innerText`)).includes("Went to sleep") && (await b.eval(`${sheet}.innerText`)).includes("Woke up"));
  check("before any time is entered the preview says so", (await b.eval(`document.querySelector('[data-sleep-preview]').innerText`)).includes("Duration appears here"));
  await b.shot("wellness-sleep-sheet-390");
  const cases = [["23:30", "07:15"], ["01:00", "08:00"], ["14:00", "08:00"]];
  for (const [bed, wake] of cases) {
    await setTime("bedtime", bed); await setTime("wakeTime", wake);
    const preview = await b.eval(`document.querySelector('[data-sleep-preview]').innerText.trim()`);
    await b.click(`${sheet}.querySelector('form button[type=submit]')`, "Save sleep");
    await b.waitFor(`!${sheet}`, "sheet closed");
    const server = (await api.get("/api/v1/sleep-entries?size=1")).body.items[0];
    await until(async () => (await valueOf("sleep")) === fmtMin(server.durationMinutes), "line shows the server duration");
    check(`${bed} to ${wake}: the preview (${preview}) equals the saved duration (${fmtMin(server.durationMinutes)}) shown on the line`, preview === fmtMin(server.durationMinutes) && server.bedtime === bed && server.wakeTime === wake && server.date === today, J(server));
    check("the button is now 'Edit'", await b.has(btn("sleep", "Edit")));
    await b.click(btn("sleep", "Edit"), "Edit"); await b.waitFor(sheet, "sheet");
    check("editing shows the saved times", (await b.eval(`${sheet}.querySelector('input[name=bedtime]').value`)) === bed && (await b.eval(`${sheet}.querySelector('input[name=wakeTime]').value`)) === wake);
  }
  check("one entry per morning: saving again replaced it", (await api.get("/api/v1/sleep-entries")).body.totalItems === 1);
  for (const [bed, wake, expected] of [["11:59", "08:00", "longer than 20 hours"], ["07:46", "08:00", "shorter than 15 minutes"], ["07:00", "07:00", "same"]]) {
    await setTime("bedtime", bed); await setTime("wakeTime", wake);
    const preview = await b.eval(`document.querySelector('[data-sleep-preview]').innerText.trim()`);
    check(`${bed} to ${wake}: the preview explains the problem`, preview.includes(expected), preview);
    await b.click(`${sheet}.querySelector('form button[type=submit]')`, "Save invalid"); await sleep(600);
    const err = await b.eval(`${sheet}.querySelector('[role=alert]')?.innerText ?? ''`);
    check(`the server refuses it with the same wording, the sheet stays open and nothing changes`, err.includes(expected) && (await api.get("/api/v1/sleep-entries?size=1")).body.items[0].durationMinutes === 18 * 60, err);
  }
  await b.key("Escape", "Escape", 27); await sleep(400);

  // Daylight saving and other zones: the preview function against the server, for real DST nights that have passed.
  let previewSleep;
  try { ({ previewSleep } = await import("../../../lib/sleep-preview.ts")); } catch (e) { check("the preview function can be loaded (Node 22.18+ or 24)", false, e.message); }
  if (previewSleep) {
    const dst = [["Europe/London", "2026-03-29", "23:00", "07:00"], ["Europe/London", "2025-10-26", "23:00", "07:00"], ["Europe/London", "2025-10-26", "01:30", "08:00"],
      ["Europe/London", "2025-10-26", "22:00", "01:30"], ["Europe/London", "2026-03-29", "01:30", "08:00"], ["America/New_York", "2026-03-08", "23:00", "07:00"],
      ["America/New_York", "2025-11-02", "23:00", "07:00"], ["Asia/Kolkata", "2026-05-10", "23:30", "07:15"], ["Australia/Sydney", "2026-04-05", "23:00", "07:00"], ["Australia/Sydney", "2025-10-05", "23:00", "07:00"]];
    const users = {};
    let agree = 0; const misses = [];
    for (const [zone, date, bed, wake] of dst) {
      users[zone] ??= await registerUser(`wellness-tz${Date.now()}${Object.keys(users).length}@example.com`, PW, "Tz", zone);
      const server = await users[zone].put(`/api/v1/sleep-entries/${date}`, { bedtime: bed, wakeTime: wake });
      const p = previewSleep(date, bed, wake, zone);
      if (server.status < 300 && p.ok && p.minutes === server.body.durationMinutes) agree++; else misses.push(J([zone, date, bed, wake, server.body?.durationMinutes ?? server.body?.message, p]));
    }
    check(`the preview equals the server on ${dst.length} nights across daylight-saving changes and other zones`, agree === dst.length, misses.join(" | "));
  }

  // ------------------------------------------------------------------------------------------------
  begin("Goals and preferences: set, progress, clear, hide and show");
  await b.goto("/wellness"); await b.waitFor(line("water"), "lines");
  check("without goals there is no progress line and no 'of' text", !(await b.has(`document.querySelector('[data-metric] [role=progressbar]')`)) && !(await rowText("water")).includes(" of "));
  await b.clickText("button", "Customize"); await b.waitFor(sheet, "customize sheet");
  const cf = await b.eval(`[...${sheet}.querySelectorAll('input')].map((i) => i.name)`);
  check("Customize has three switches (all on by default) and three empty goal fields", J(cf) === J(["show-sleep", "show-water", "show-protein", "sleepGoal", "waterGoal", "proteinGoal"]) && (await b.eval(`[...${sheet}.querySelectorAll('input[type=checkbox]')].every((i) => i.checked)`)) && (await b.eval(`[...${sheet}.querySelectorAll('input:not([type=checkbox])')].every((i) => i.value === '' && i.placeholder === 'No goal')`)), J(cf));
  await b.shot("wellness-customize-390");
  for (const [name, bad, msg] of [["waterGoal", "100", "250 to 10000 ml"], ["proteinGoal", "5", "10 to 500 g"], ["sleepGoal", "abc", "4 to 16 hours"], ["sleepGoal", "2", "4 to 16 hours"]]) {
    await b.type(`${sheet}.querySelector('input[name=${name}]')`, bad, name); await b.eval(`${sheet}.querySelector('form').requestSubmit()`); await sleep(300);
    check(`${name} "${bad}" is refused with '${msg}' and nothing is saved`, await b.has(`${sheet}.innerText.includes(${J(msg)})`) && (await prefsApi()).waterGoalMl === null, name);
    await b.type(`${sheet}.querySelector('input[name=${name}]')`, "", name);
    await b.eval(`(() => { const el = ${sheet}.querySelector('input[name=${name}]'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, ''); el.dispatchEvent(new Event('input', { bubbles: true })); })()`);
  }
  await b.type(`${sheet}.querySelector('input[name=waterGoal]')`, "2000", "water goal"); await b.type(`${sheet}.querySelector('input[name=proteinGoal]')`, "140", "protein goal"); await b.type(`${sheet}.querySelector('input[name=sleepGoal]')`, "8", "sleep goal");
  await b.click(`${sheet}.querySelector('form button[type=submit]')`, "Save prefs");
  await b.waitFor(`!${sheet}`, "sheet closed");
  const p1 = await prefsApi();
  check("goals were saved as typed (sleep hours became minutes)", p1.waterGoalMl === 2000 && p1.proteinGoalG === 140 && p1.sleepGoalMinutes === 480, J(p1));
  await until(async () => (await rowText("water")).includes("of 2 L"), "water goal shown");
  const t1 = await todayApi();
  check("water: 'of 2 L' and the progress bar equals the API's percent", (await b.eval(`${line("water")}.querySelector('[role=progressbar]').getAttribute('aria-valuenow')`)) === String(t1.water.progressPercent) && t1.water.progressPercent === 54, J(t1.water));
  check("protein: 'of 140 g' and the progress bar equals the API's percent", (await rowText("protein")).includes("of 140 g") && (await b.eval(`${line("protein")}.querySelector('[role=progressbar]').getAttribute('aria-valuenow')`)) === String(t1.protein.progressPercent), J(t1.protein));
  check("sleep: 'of 8 h' with a progress bar (18 h is above the goal, so it is full and reached)", (await rowText("sleep")).includes("of 8 h") && t1.sleep.progressPercent === 100 && (await rowText("sleep")).includes("Goal reached"), await rowText("sleep"));
  await b.click(btn("water", "Add 250"), "+250 to pass the goal"); await sleep(700);
  for (let i = 0; i < 4; i++) { await until(async () => !(await b.has(`${line("water")}.querySelector('button[disabled]')`)), "enabled"); await b.click(btn("water", "Add 250"), "+250"); await sleep(600); }
  await until(async () => (await waterDay()).totalMl >= 2000, "goal passed");
  await until(async () => (await rowText("water")).includes("Goal reached"), "reached shown");
  const t2 = await todayApi();
  check("passing the goal shows 'Goal reached' and stops the bar at 100% (the real total is still shown)", t2.water.goalReached && t2.water.progressPercent === 100 && (await valueOf("water")).startsWith(fmtMl(t2.water.totalMl)), J(t2.water));

  await b.clickText("button", "Customize"); await b.waitFor(sheet, "sheet");
  await b.click(`${sheet}.querySelector('input[name=show-water]')`, "hide water");
  await b.eval(`(() => { const el = ${sheet}.querySelector('input[name=waterGoal]'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, ''); el.dispatchEvent(new Event('input', { bubbles: true })); })()`);
  await b.click(`${sheet}.querySelector('form button[type=submit]')`, "Save");
  await b.waitFor(`!${sheet}`, "closed");
  await until(async () => !(await b.has(line("water"))), "water line hidden");
  const p2 = await prefsApi(); const t3 = await todayApi();
  check("hiding water removes its line, and the API agrees (preference off, section absent, goal cleared)", !p2.waterEnabled && t3.water === null && p2.waterGoalMl === null, J(p2));
  check("sleep and protein are still there", await b.has(line("sleep")) && await b.has(line("protein")));
  await b.goto("/wellness"); await b.waitFor(line("sleep"), "reload");
  check("the choice survives a reload", !(await b.has(line("water"))));
  await b.clickText("button", "Customize"); await b.waitFor(sheet, "sheet");
  await b.click(`${sheet}.querySelector('input[name=show-water]')`, "show water");
  await b.click(`${sheet}.querySelector('form button[type=submit]')`, "Save"); await b.waitFor(`!${sheet}`, "closed");
  await until(async () => b.has(line("water")), "water back");
  check("showing it again brings the line back with the data it kept", (await valueOf("water")).startsWith(fmtMl((await waterDay()).totalMl)), await valueOf("water"));
  // Hide everything.
  await b.clickText("button", "Customize"); await b.waitFor(sheet, "sheet");
  for (const n of ["sleep", "water", "protein"]) await b.click(`${sheet}.querySelector('input[name=show-${n}]')`, "hide " + n);
  await b.click(`${sheet}.querySelector('form button[type=submit]')`, "Save"); await b.waitFor(`!${sheet}`, "closed");
  await until(async () => b.has(`document.querySelector('[data-all-hidden]')`), "all hidden message");
  check("everything hidden: a short message, no lines, and Customize is still there", !(await b.has(`document.querySelector('[data-metric]')`)) && await b.has(`[...document.querySelectorAll('button')].some((x) => x.innerText.trim() === 'Customize')`));
  await b.shot("wellness-all-hidden-390");
  await b.clickText("button", "Customize"); await b.waitFor(sheet, "sheet");
  for (const n of ["sleep", "water", "protein"]) await b.click(`${sheet}.querySelector('input[name=show-${n}]')`, "show " + n);
  await b.click(`${sheet}.querySelector('form button[type=submit]')`, "Save"); await b.waitFor(`!${sheet}`, "closed");
  await until(async () => b.has(line("protein")), "all back");
  check("all three come back, nothing was lost", (await b.eval(`document.querySelectorAll('[data-metric]').length`)) === 3);

  // ------------------------------------------------------------------------------------------------
  begin("Responsive: overflow and touch targets at 390, 768 and 1280 px (lines, and each sheet open)");
  for (const w of [390, 768, 1280]) {
    await b.viewport(w, 900); await b.goto("/wellness"); await b.waitFor(line("protein"), "lines"); await sleep(400);
    const o = await b.overflow(); check(`${w}px: no horizontal overflow`, !o.overflow && o.offenders.length === 0, J(o));
    const small = await b.eval(`[...document.querySelectorAll('main button, main a')].filter((e) => __h.visible(e) && e.getBoundingClientRect().height < 40).map((e) => (e.getAttribute('aria-label') || e.textContent).trim().slice(0, 30))`);
    check(`${w}px: every button is at least 40px tall`, small.length === 0, J(small));
    check(`${w}px: the view fits without scrolling`, await b.has(`document.documentElement.scrollHeight <= innerHeight + 1`));
    for (const [what, open] of [["sleep sheet", btn("sleep", "Edit")], ["water sheet", btn("water", "More ways to log water")], ["protein sheet", btn("protein", "More ways to log protein")]]) {
      await b.click(open, what); await b.waitFor(sheet, what); await sleep(300);
      const os = await b.overflow(); const box = await b.eval(`(() => { const r = ${sheet}.getBoundingClientRect(); return { l: r.left, r: r.right, w: innerWidth }; })()`);
      const sm = await b.eval(`[...${sheet}.querySelectorAll('button')].filter((e) => __h.visible(e) && e.getBoundingClientRect().height < 40).map((e) => (e.getAttribute('aria-label') || e.textContent).trim().slice(0, 30))`);
      check(`${w}px ${what}: no overflow, inside the viewport, touch targets >= 40px`, !os.overflow && box.l >= 0 && box.r <= box.w + 1 && sm.length === 0, J({ os: os.offenders, box, sm }));
      await b.key("Escape", "Escape", 27); await sleep(350);
    }
    if (w === 768 || w === 1280) await b.shot(`wellness-${w}`);
  }

  // ------------------------------------------------------------------------------------------------
  begin("Loading state, then the backend going away and coming back");
  await b.viewport(1280, 900); await b.goto("/dashboard"); await b.waitFor(hasText("Today's workout"), "dashboard");
  const pid = backendPid();
  execSync(`kill -STOP ${pid}`);
  try {
    await b.click(`document.querySelector('a[href="/wellness"]')`, "Wellness nav");
    let sawLoading = false;
    for (let i = 0; i < 20 && !sawLoading; i++) { sawLoading = await b.has(`!!document.querySelector('[aria-busy="true"]')`); if (!sawLoading) await sleep(150); }
    check("the loading skeleton shows while the backend is slow", sawLoading);
    await b.waitFor(hasText("We can't reach the server"), "unavailable panel", 15000);
    check("a frozen backend shows the unavailable panel, and the user stays on /wellness", (await b.url()) === "/wellness");
  } finally { execSync(`kill -CONT ${pid}`); }
  await sleep(1500); await b.clickText("button", "Try again");
  await b.waitFor(line("water"), "recovered", 15000);
  check("Try again brings the page back", true);
  await b.viewport(390);
  await b.goto("/wellness"); await b.waitFor(line("water"), "lines");
  const before = (await waterDay()).totalMl;
  await stopBackend();
  check("backend is down", !(await backendHealthy()));
  await b.click(btn("water", "Add 250"), "+250 while down");
  await b.waitFor(`${line("water")}.querySelector('[role=alert]')`, "inline error", 15000);
  check("adding water while the backend is down shows an inline error, no Undo, and the value is unchanged", !(await b.has(btn("water", "Undo"))) && (await valueOf("water")) === fmtMl(before), await rowText("water"));
  await b.shot("wellness-error-390");
  check("backend restarted", await startBackend());
  await b.eval("location.reload()"); await b.waitFor(line("water"), "reloaded", 20000);
  await sleep(800); await b.eval("window.scrollTo(0, 0)");   // let the reloaded page settle, and start from the top, before tapping
  check("after the backend returns the page works and nothing was counted for the failed tap", (await waterDay()).totalMl === before);
  await b.click(btn("water", "Add 250"), "+250 after recovery");
  await until(async () => (await waterDay()).totalMl === before + 250, "added once");
  check("a fresh tap after recovery adds exactly once", (await waterDay()).totalMl === before + 250);
} catch (e) { check("section completed without error", false, e.message); await b.shot("FAILED-run8-wellness"); }
b.close();
summary();
