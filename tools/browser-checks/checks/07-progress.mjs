// Part 7: /fitness/progress, the exercise page's progression chart and record history, the hub link and the dashboard's
// body-weight card. Driven like a user and confirmed against the API and against numbers worked out from the seed.
import { Browser, begin, check, summary, registerUser, sleep, sql } from "../lib.mjs";
const J = JSON.stringify; const PW = "a long enough password"; const TZ = "Asia/Kolkata";
const email = `progress${Date.now()}@example.com`;
const api = await registerUser(email, PW, "Asha Nair", TZ);

const todayIn = () => new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const addDays = (iso, n) => { const [y, m, d] = iso.split("-").map(Number); return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10); };
const monday = (iso) => { const [y, m, d] = iso.split("-").map(Number); return addDays(iso, -((new Date(Date.UTC(y, m - 1, d)).getUTCDay() + 6) % 7)); };
const today = todayIn();
const num = (v) => String(Number(Number(v).toFixed(2)));

const hasText = (t) => `document.body.innerText.includes(${J(t)})`;
const figs = `[...document.querySelectorAll('figure[data-chart]')]`;
const fig = (i) => `${figs}[${i}]`;
const pointsOf = (i) => b.eval(`Number(${fig(i)}?.getAttribute('data-points') ?? -1)`);
const tableOf = (i) => b.eval(`[...${fig(i)}.querySelectorAll('table tbody tr')].map((r) => [...r.children].map((c) => c.textContent))`);
const headOf = (i) => b.eval(`[...${fig(i)}.querySelectorAll('table thead th')].map((c) => c.textContent)`);
const until = async (fn, label, ms = 10000) => { const t = Date.now(); while (Date.now() - t < ms) { if (await fn()) return true; await sleep(200); } throw new Error("timeout: " + label); };

async function exerciseId(name) { return (await api.get(`/api/v1/exercises?q=${encodeURIComponent(name)}&size=5`)).body.items[0].id; }
/** Finishes a workout through the API, then back-dates it (the API can only make today's) with SQL. */
async function finished(items, daysAgo) {
  const w = (await api.post("/api/v1/workouts", { name: "Seed" })).body;
  for (const [ex, sets] of items) {
    const we = (await api.post(`/api/v1/workouts/${w.id}/exercises`, { exerciseId: ex })).body;
    for (const [kg, reps, warmup] of sets) await api.post(`/api/v1/workouts/${w.id}/exercises/${we.id}/sets`, { weightKg: kg, reps, warmup: !!warmup });
  }
  const done = await api.post(`/api/v1/workouts/${w.id}/finish`);
  if (done.status !== 200) throw new Error("finish failed " + done.status);
  const day = addDays(today, -daysAgo);
  sql(`update workouts set performed_on='${day}', started_at='${day} 09:00+00', finished_at='${day} 10:00+00' where id='${w.id}';`);
  return { id: w.id, day };
}

const b = new Browser(); await b.launch();
try {
  await b.viewport(390);
  await b.goto("/login");
  await b.type(`document.querySelector('input[name=email]')`, email, "email"); await b.type(`document.querySelector('input[name=password]')`, PW, "password");
  await b.clickText("button", "Log in"); await b.waitFor(`location.pathname==='/dashboard'`, "dashboard");

  // ------------------------------------------------------------------------------------------------
  begin("Empty states: progress page, exercise page, dashboard card (390px)");
  await b.goto("/fitness/progress");
  await b.waitFor(hasText("No completed workouts in this range"), "empty volume");
  check("no workouts: an empty state instead of empty charts", !(await b.has(fig(0))) && await b.has(hasText("Finish a workout")));
  check("no records: an empty state for personal records", await b.has(hasText("No records yet")));
  const o0 = await b.overflow(); check("no horizontal overflow (empty)", !o0.overflow && o0.offenders.length === 0, J(o0));
  const bench = await exerciseId("Barbell Bench Press"), row = await exerciseId("Barbell Row"), squat = await exerciseId("Barbell Back Squat");
  await b.goto(`/fitness/exercises/${bench}`);
  await b.waitFor(hasText("No records yet"), "exercise empty");
  check("an exercise with no history shows no progress chart and no record history", !(await b.has(hasText("Record history"))) && !(await b.has(fig(0))));
  await b.goto("/dashboard");
  await b.waitFor(hasText("Body weight"), "dashboard");
  check("dashboard body-weight card with no data invites the first weight", await b.has(hasText("No weight logged yet")) && await b.has(`[...document.querySelectorAll('main a')].some((a) => a.textContent.trim() === 'Log weight' && a.getAttribute('href') === '/weight')`));

  // ------------------------------------------------------------------------------------------------
  // Seed: a bench that improves over four weeks, rows and squats in other weeks, and warm-ups that must not count.
  const seed = [
    { ago: 1,  items: [[bench, [[40, 10, true], [60, 8], [60, 8]]], [squat, [[100, 5]]]] },   // this week
    { ago: 9,  items: [[bench, [[60, 8], [62.5, 5]]], [row, [[70, 8]]]] },
    { ago: 10, items: [[squat, [[110, 5], [110, 5]]]] },                                       // same week as the line above
    { ago: 16, items: [[bench, [[60, 10]]], [row, [[75, 8, false]]]] },
    { ago: 30, items: [[bench, [[65, 5]]]] },
    { ago: 60, items: [[bench, [[70, 3]]], [squat, [[120, 3]]]] },
    { ago: 200, items: [[bench, [[55, 8]]]] },                                                  // outside 90 days, inside a year
  ];
  const expectedByWeek = {}; const group = { [bench]: "PUSH", [row]: "PULL", [squat]: "LEGS" };
  for (const w of seed) {
    await finished(w.items, w.ago);
    const wk = monday(addDays(today, -w.ago)); const e = (expectedByWeek[wk] ??= { workouts: 0, PUSH: 0, PULL: 0, LEGS: 0, CORE_FULL_BODY: 0 });
    e.workouts++;
    for (const [ex, sets] of w.items) for (const [kg, reps, warm] of sets) if (!warm) e[group[ex]] += kg * reps;
  }
  const weeksRange = (n) => Array.from({ length: n }, (_, i) => addDays(monday(today), -7 * (n - 1 - i)));

  begin("Progress page: volume by movement group and workouts per week (1280px)");
  await b.viewport(1280, 900);
  await b.goto("/fitness/progress");
  await b.waitFor(fig(0), "charts", 15000);
  await until(async () => (await pointsOf(0)) === 12, "12 weeks");
  const api12 = (await api.get(`/api/v1/fitness/analytics/volume?from=${weeksRange(12)[0]}&to=${today}&granularity=weekly`)).body;
  check("default range is 12 whole weeks, as the API returns", (await pointsOf(0)) === api12.points.length && api12.points.length === 12 && (await pointsOf(1)) === 12);
  check("the 12w link is marked current", await b.has(`document.querySelector('nav[aria-label="Range"] a[aria-current="true"]')?.textContent.trim() === '12w'`));
  check("two charts: volume by movement group and workouts per week", (await b.eval(`${figs}.length`)) === 2);
  const head = await headOf(0);
  check("the volume table names the four movement groups and a total", J(head) === J(["Week starting", "Push", "Pull", "Legs", "Core / full body", "Total"]), J(head));
  const rows = await tableOf(0);
  const weeks = weeksRange(12);
  const expectedRows = weeks.map((wk) => { const e = expectedByWeek[wk] ?? { PUSH: 0, PULL: 0, LEGS: 0, CORE_FULL_BODY: 0 }; return [`${num(e.PUSH)} kg`, `${num(e.PULL)} kg`, `${num(e.LEGS)} kg`, `${num(e.CORE_FULL_BODY)} kg`, `${num(e.PUSH + e.PULL + e.LEGS + e.CORE_FULL_BODY)} kg`]; });
  check("every week's volume per group and total equals the sets worked out from the seed (warm-ups excluded, empty weeks zero)", J(rows.map((r) => r.slice(1))) === J(expectedRows), J(rows.map((r) => r.slice(1))).slice(0, 300));
  const wrows = await tableOf(1);
  check("workouts per week equal the seeded workouts", J(wrows.map((r) => r[1])) === J(weeks.map((wk) => String(expectedByWeek[wk]?.workouts ?? 0))), J(wrows.map((r) => r[1])));
  check("the charts draw bars", (await b.eval(`${fig(0)}.querySelectorAll('.recharts-bar-rectangle').length`)) > 0 && (await b.eval(`${fig(1)}.querySelectorAll('.recharts-bar-rectangle').length`)) > 0);
  check("the pictures are hidden from assistive technology", await b.has(`${fig(0)}.querySelector('div[aria-hidden=true] .recharts-surface')`));
  check("the legend lists the four groups", await b.has(`${fig(0)}.querySelector('ul[aria-hidden=true]').innerText.replace(/\\s+/g,' ').includes('Push Pull Legs Core / full body')`));
  await b.shot("progress-12w-1280");

  begin("Progress page: ranges, tooltips (mouse and touch)");
  await b.clickText("a", "8w"); await b.waitFor(`location.search.includes('range=8w')`, "URL");
  await until(async () => (await pointsOf(0)) === 8, "8 weeks");
  check("8w: URL is ?range=8w and 8 bars per chart", (await pointsOf(0)) === 8 && (await pointsOf(1)) === 8);
  await b.goto("/fitness/progress?range=6m"); await b.waitFor(fig(0), "6m", 15000);
  check("6m (shared link): 26 weekly bars", (await pointsOf(0)) === 26);
  await b.goto("/fitness/progress?range=1y"); await b.waitFor(fig(0), "1y", 15000);
  const months = await tableOf(0);
  check("1y: 12 monthly bars, headed 'Month', with the month totals", (await pointsOf(0)) === 12 && (await headOf(0))[0] === "Month" && /\d{4}$/.test(months[0][0]), J(months[0]));
  check("the monthly totals add up to the whole seed's volume", months.reduce((sum, r) => sum + parseFloat(r.at(-1)), 0) === Object.values(expectedByWeek).reduce((s, e) => s + e.PUSH + e.PULL + e.LEGS, 0), J(months.map((r) => r.at(-1))));
  await b.goto("/fitness/progress?range=nonsense"); await b.waitFor(fig(0), "fallback", 15000);
  check("an unknown ?range= falls back to 12w", await b.has(`document.querySelector('nav[aria-label="Range"] a[aria-current="true"]')?.textContent.trim() === '12w'`));
  await b.goto("/fitness/progress"); await b.waitFor(fig(0), "charts", 15000); await sleep(400);
  const bar = await b.eval(`(() => { const r = [...${fig(0)}.querySelectorAll('.recharts-bar-rectangle')].map((e) => e.getBoundingClientRect()).filter((r) => r.height > 4)[0]; ${fig(0)}.scrollIntoView({block:'center'}); const q = [...${fig(0)}.querySelectorAll('.recharts-bar-rectangle')].map((e) => e.getBoundingClientRect()).filter((r) => r.height > 4)[0]; return { x: q.left + q.width / 2, y: q.top + q.height / 2 }; })()`);
  await b.send("Input.dispatchMouseEvent", { type: "mouseMoved", x: bar.x - 5, y: bar.y }); await b.send("Input.dispatchMouseEvent", { type: "mouseMoved", x: bar.x, y: bar.y });
  await b.waitFor(`document.querySelector('[data-chart-tooltip]')`, "tooltip on hover");
  const tip = await b.eval(`document.querySelector('[data-chart-tooltip]').innerText`);
  check("hovering a bar shows the week, each group and the total", tip.includes("Week of") && tip.includes("Push") && tip.includes("Total") && tip.includes("kg"), tip);
  await b.shot("progress-tooltip-hover-1280");
  await b.viewport(390); await b.goto("/fitness/progress"); await b.waitFor(fig(0), "charts", 15000); await sleep(500);
  const bar2 = await b.eval(`(() => { ${fig(0)}.scrollIntoView({block:'center'}); const q = [...${fig(0)}.querySelectorAll('.recharts-bar-rectangle')].map((e) => e.getBoundingClientRect()).filter((r) => r.height > 4 && r.left > 0 && r.right < innerWidth)[0]; return { x: q.left + q.width / 2, y: q.top + q.height / 2 }; })()`);
  await sleep(200);
  await b.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: bar2.x, y: bar2.y }] }); await sleep(80); await b.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  await until(() => b.has(`document.querySelector('[data-chart-tooltip]')`), "tooltip on tap", 4000).catch(() => {});
  check("tapping a bar shows the tooltip", ((await b.eval(`document.querySelector('[data-chart-tooltip]')?.innerText ?? ''`))).includes("Total"));
  await b.shot("progress-tooltip-tap-390");

  begin("Recent personal records on the progress page");
  await b.goto("/fitness/progress"); await b.waitFor(hasText("Recent personal records"), "prs");
  const prs = (await api.get("/api/v1/fitness/personal-records?limit=10")).body;
  const distinctSets = [...new Set(prs.map((p) => p.record.setId))];
  const prRows = await b.eval(`[...document.querySelectorAll('[data-pr-row]')].map((r) => ({ text: r.innerText.replace(/\\s+/g, ' '), href: r.querySelector('a')?.getAttribute('href'), badges: [...r.querySelectorAll('.text-record')].length }))`);
  check(`one row per record set (${distinctSets.length}), newest first, linking to the exercise`, prRows.length === distinctSets.length && prRows[0].text.includes(prs[0].exercise.name) && prRows[0].href === `/fitness/exercises/${prs[0].exercise.id}`, J(prRows[0]));
  check("each row shows its set, the estimated 1RM and amber record badges", prRows.every((r) => r.text.includes("kg ×") && r.text.includes("est. 1RM") && r.badges >= 1), J(prRows));
  const sameSet = prs.filter((p) => p.record.setId === prs[0].record.setId).length;
  check("a set that is several records shows all its badges once", prRows[0].badges === sameSet, `${prRows[0].badges} vs ${sameSet}`);
  await b.click(`document.querySelector('[data-pr-row] a')`, "PR exercise link"); await b.waitFor(`location.pathname === '/fitness/exercises/${prs[0].exercise.id}'`, "exercise page");
  check("the exercise link opens that exercise", true);

  // ------------------------------------------------------------------------------------------------
  // A dedicated exercise with 12 sessions, each a new weight record, so record history needs more than one page.
  const press = (await api.post("/api/v1/exercises", { name: "Progressive Press", primaryMuscleGroup: "SHOULDERS" })).body.id;
  for (let i = 0; i < 12; i++) await finished([[press, [[40 + i * 2.5, 5]]]], 120 - i * 9);
  const single = (await api.post("/api/v1/exercises", { name: "Single Session Curl", primaryMuscleGroup: "BICEPS" })).body.id;
  await finished([[single, [[20, 10]]]], 3);

  begin("Exercise page: progression chart and record history (390px, then 1280px)");
  await b.viewport(390);
  await b.goto(`/fitness/exercises/${bench}`);
  await b.waitFor(fig(0), "progression chart", 15000);
  const win = (from) => `from=${from}&to=${today}`;
  const [yy, mm, dd] = today.split("-").map(Number); const from1y = addDays(new Date(Date.UTC(yy - 1, mm - 1, dd)).toISOString().slice(0, 10), 1);
  const prog = (await api.get(`/api/v1/exercises/${bench}/progression?${win(from1y)}`)).body;
  await until(async () => (await pointsOf(0)) === prog.points.length, "progression points");
  check(`default range is 1 year: ${prog.points.length} sessions, as the API returns`, (await pointsOf(0)) === prog.points.length && prog.points.length === 6);
  check("the 1y link is marked current", await b.has(`document.querySelector('nav[aria-label="Range"] a[aria-current="true"]')?.textContent.trim() === '1y'`));
  const prows = await tableOf(0);
  check("the table's columns are Date, Top set and Estimated 1RM", J(await headOf(0)) === J(["Date", "Top set", "Estimated 1RM"]));
  check("each session's top set and 1RM equal the API (the 40 kg warm-up is not a top set)", J(prows.map((r) => [r[1], r[2]])) === J(prog.points.map((p) => [`${num(p.topSet.weightKg)} kg`, `${num(p.bestEstimated1rmKg)} kg`])), J(prows));
  check("top set values follow the seed, oldest first", J(prows.map((r) => r[1])) === J(["55 kg", "70 kg", "65 kg", "60 kg", "62.5 kg", "60 kg"]), J(prows.map((r) => r[1])));
  await b.click(`[...document.querySelectorAll('nav[aria-label="Range"] a')].find((a) => a.textContent.trim() === '90d')`, "90d");
  await b.waitFor(`location.search.includes('range=90d')`, "URL");
  const p90 = (await api.get(`/api/v1/exercises/${bench}/progression?from=${addDays(today, -89)}&to=${today}`)).body;
  await until(async () => (await pointsOf(0)) === p90.points.length, "90d points");
  check(`90d: URL changes and the chart has ${p90.points.length} sessions`, (await pointsOf(0)) === p90.points.length && p90.points.length === 5 && p90.points.length < prog.points.length);
  const events = (await api.get(`/api/v1/exercises/${bench}/personal-records?size=100`)).body;
  const distinct = [...new Set(events.items.map((e) => e.setId))];
  await b.waitFor(hasText("Record history"), "record history");
  const evRows = await b.eval(`document.querySelectorAll('[data-pr-row]').length`);
  check(`record history: one row per record set (${distinct.length}), same as the API`, evRows === distinct.length && events.totalItems >= distinct.length, `${evRows} vs ${distinct.length}`);
  const o1 = await b.overflow(); check("no horizontal overflow on the exercise page", !o1.overflow && o1.offenders.length === 0, J(o1));
  await b.shot("exercise-progress-390");

  await b.goto(`/fitness/exercises/${press}`);
  await b.waitFor(hasText("Record history"), "press history");
  const pressEvents = (await api.get(`/api/v1/exercises/${press}/personal-records?page=0&size=10`)).body;
  const pages = pressEvents.totalPages;
  check(`record history is paged (${pressEvents.totalItems} events, ${pages} pages)`, pages >= 2 && await b.has(hasText(`Page 1 of ${pages}`)));
  const page0 = await b.eval(`[...document.querySelectorAll('[data-pr-row]')].map((r) => r.innerText.replace(/\\s+/g, ' ').slice(0, 60))`);
  await b.click(`document.querySelector('nav[aria-label="Record history pages"] a[href*="prpage=1"]')`, "Next record page");
  await b.waitFor(`location.search.includes('prpage=1')`, "prpage=1");
  const page1 = await b.eval(`[...document.querySelectorAll('[data-pr-row]')].map((r) => r.innerText.replace(/\\s+/g, ' ').slice(0, 60))`);
  const apiPage1 = (await api.get(`/api/v1/exercises/${press}/personal-records?page=1&size=10`)).body.items;
  check("page 2 of the record history shows different, older records than page 1, in the API's order", page1.length > 0 && page1.every((t) => !page0.includes(t)) && J(page1.map((t) => t.split(" ")[0])) !== J(page0.map((t) => t.split(" ")[0])), J({ page0: page0.slice(0, 2), page1: page1.slice(0, 2) }));
  check("the range and the two lists keep their own page parameters", (await b.url()).includes("prpage=1") && await b.has(`document.querySelector('nav[aria-label="Range"] a').getAttribute('href').includes('prpage=1')`) && apiPage1.length > 0);
  await b.goto(`/fitness/exercises/${single}`);
  await b.waitFor(fig(0), "single-session chart", 15000);
  check("one session: one point and a note that the lines need another session", (await pointsOf(0)) === 1 && await b.has(hasText("One session so far")));

  // ------------------------------------------------------------------------------------------------
  begin("Fitness hub link and dashboard body-weight card");
  await b.goto("/fitness"); await b.waitFor(hasText("This week"), "hub");
  check("the hub has a Progress link, the four links fit at 390px", await b.has(`document.querySelector('nav[aria-label="Fitness sections"] a[href="/fitness/progress"]')`) && !(await b.overflow()).overflow);
  await b.shot("hub-390");
  await b.click(`document.querySelector('nav[aria-label="Fitness sections"] a[href="/fitness/progress"]')`, "Progress link");
  await b.waitFor(`location.pathname === '/fitness/progress'`, "progress page");
  await b.waitFor(hasText("Recent personal records"), "progress page content");
  check("the Progress link opens the progress page", true);

  await api.put(`/api/v1/weight-entries/${addDays(today, -8)}`, { weightKg: 84.0 }); await api.put(`/api/v1/weight-entries/${today}`, { weightKg: 82.4 });
  await api.put("/api/v1/weight/target", { targetWeightKg: 80 });
  await b.goto("/dashboard"); await b.waitFor(`document.querySelector('[data-body-weight]')`, "weight card");
  const sum = (await api.get("/api/v1/weight/summary")).body;
  const cardText = await b.eval(`document.querySelector('[data-body-weight]').closest('[data-slot=card]').innerText.replace(/\\s+/g, ' ')`);
  check("the card shows the current weight from the API", cardText.includes(`${num(sum.current.weightKg)} kg`), cardText);
  check("the card shows the 7-day change with a real minus sign", cardText.includes(`−${num(Math.abs(sum.change.last7Days.changeKg))} kg in 7 days`), cardText);
  check("the card shows the progress bar and what is left to the target", (await b.eval(`document.querySelector('[data-body-weight-progress] [role=progressbar]').getAttribute('aria-valuenow')`)) === String(sum.progress.percent) && cardText.includes(`${num(sum.progress.remainingKg)} kg to 80 kg`), cardText);
  await b.click(`[...document.querySelectorAll('main a')].find((a) => a.textContent.trim() === 'View weight')`, "View weight");
  await b.waitFor(`location.pathname === '/weight'`, "weight page");
  check("View weight opens /weight", true);
  await b.goto("/dashboard"); await b.waitFor(hasText("Body weight"), "dashboard");
  const soon = await b.eval(`[...document.querySelectorAll('main [data-slot=card]')].filter((c) => c.innerText.includes('Soon')).map((c) => c.innerText.split('\\n')[0].trim())`);
  check("the other dashboard cards are still 'Soon'", J(soon.sort()) === J(["Goals", "Upcoming events", "DSA progress", "Habits today", "Tasks due"].sort()), J(soon));

  // ------------------------------------------------------------------------------------------------
  begin("No horizontal overflow and touch targets at 390, 768 and 1280 px");
  const pagesToCheck = [["progress page", "/fitness/progress", () => fig(0)], ["exercise page", `/fitness/exercises/${bench}`, () => fig(0)], ["dashboard", "/dashboard", () => `document.querySelector('[data-body-weight]')`], ["fitness hub", "/fitness", () => `document.body`]];
  for (const w of [390, 768, 1280]) {
    await b.viewport(w, 900);
    for (const [label, path, ready] of pagesToCheck) {
      await b.goto(path); await b.waitFor(ready(), `${label} ready`, 15000); await sleep(500);
      const o = await b.overflow();
      check(`${w}px ${label}: no overflow`, !o.overflow && o.offenders.length === 0, J(o));
      const small = await b.eval(`[...document.querySelectorAll('main a, main button')].filter((e) => __h.visible(e) && e.getBoundingClientRect().height < 40).map((e) => (e.getAttribute('aria-label') || e.textContent).trim().slice(0, 30))`);
      check(`${w}px ${label}: touch targets are at least 40px tall`, small.length === 0, J(small));
      if (label === "progress page") { await b.shot(`progress-full-${w}`); }
    }
  }
} catch (e) { check("section completed without error", false, e.message); await b.shot("FAILED-run7-progress"); }
b.close();
summary();
