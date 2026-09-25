// Part 5: the specific regressions to guard against after the redesign: very long names on every screen, and the picker race.
import { Browser, begin, check, summary, registerUser } from "../lib.mjs";
const J = JSON.stringify; const PW = "a long enough password";
const email = `long${Date.now()}@example.com`;
const api = await registerUser(email, PW, "Asha Nair");
const LONG_WORDS = "Extended Overhead Triceps Extension With A Really Very Long Descriptive Name Indeed Yes";      // 87 chars, wraps at spaces
const NO_SPACES = "Antidisestablishmentarianism".repeat(2) + "Xyz";                                                   // 59 chars, no break opportunity
const idLong = (await api.post("/api/v1/exercises", { name: LONG_WORDS.slice(0, 100), primaryMuscleGroup: "TRICEPS", equipment: "Cable" })).body.id;
const idBlob = (await api.post("/api/v1/exercises", { name: NO_SPACES, primaryMuscleGroup: "BACK" })).body.id;
const tpl = (await api.post("/api/v1/workout-templates", { name: "Template With A Very Long Name That Goes On " + "and on ".repeat(6), exercises: [{ exerciseId: idLong, targetSets: 3 }, { exerciseId: idBlob }] })).body.id;
const done = (await (async () => { const w = (await api.post("/api/v1/workouts", { name: "Workout named " + NO_SPACES })).body;
  for (const id of [idLong, idBlob]) { const we = (await api.post(`/api/v1/workouts/${w.id}/exercises`, { exerciseId: id })).body; await api.post(`/api/v1/workouts/${w.id}/exercises/${we.id}/sets`, { weightKg: 62.5, reps: 8, rpe: 8.5 }); }
  return (await api.post(`/api/v1/workouts/${w.id}/finish`)).body; })());
const active = (await api.post("/api/v1/workouts", { name: "Active " + NO_SPACES })).body;
for (const id of [idLong, idBlob]) { const we = (await api.post(`/api/v1/workouts/${active.id}/exercises`, { exerciseId: id })).body; await api.post(`/api/v1/workouts/${active.id}/exercises/${we.id}/sets`, { weightKg: 100, reps: 5 }); }

const b = new Browser(); await b.launch();
begin("Very long exercise/workout/template names on every screen (390px)");
try {
  await b.viewport(390);
  await b.goto("/login");
  await b.type(`document.querySelector('input[name=email]')`, email, "e"); await b.type(`document.querySelector('input[name=password]')`, PW, "p");
  await b.clickText("button", "Log in"); await b.waitFor(`location.pathname==='/dashboard'`, "dashboard");
  const pages = [
    ["active workout logger", `/fitness/workouts/${active.id}`, "Finish workout"],
    ["completed workout summary", `/fitness/workouts/${done.id}`, "Completed"],
    ["history list", "/fitness/workouts", "Workout history"],
    ["hub (resume panel)", "/fitness", "Workout in progress"],
    ["dashboard (resume card)", "/dashboard", "Resume workout"],
    ["exercises list", "/fitness/exercises?q=a", "Exercises"],
    ["exercise detail (long words)", `/fitness/exercises/${idLong}`, "Personal bests"],
    ["exercise detail (no spaces)", `/fitness/exercises/${idBlob}`, "Personal bests"],
    ["templates list", "/fitness/templates", "Workout templates"],
    ["template editor", `/fitness/templates/${tpl}`, "Edit template"],
  ];
  for (const [label, path, text] of pages) {
    await b.goto(path);
    await b.waitFor(`document.body.innerText.includes(${J(text)})`, label);
    const m = await b.eval(`(() => { const w = innerWidth; const de = document.documentElement;
      const outside = [...document.querySelectorAll('main button, main a, main input, main select, main textarea, header button, header a')]
        .filter((e) => __h.visible(e) && (e.getBoundingClientRect().right > w + 0.5 || e.getBoundingClientRect().left < -0.5))
        .map((e) => (e.getAttribute('aria-label') || e.textContent || e.tagName).trim().slice(0, 40) + '@' + Math.round(e.getBoundingClientRect().right));
      const clipped = [...document.querySelectorAll('main *')].filter((e) => { const r = e.getBoundingClientRect(); return r.width > 0 && r.right > w + 1 && !e.closest('[role=dialog]'); }).length;
      return { docOverflow: de.scrollWidth > de.clientWidth + 1, outside, clipped }; })()`);
    check(`${label}: no horizontal overflow`, !m.docOverflow && m.clipped === 0, J(m));
    check(`${label}: every button, link and field is fully on screen`, m.outside.length === 0, J(m.outside));
    await b.shot(`long-${label.replace(/\W+/g, "_")}`);
  }
  // On the logger, the long names wrap onto several lines rather than being cut off.
  await b.goto(`/fitness/workouts/${active.id}`);
  await b.waitFor(`document.body.innerText.includes('Finish workout')`, "logger");
  const heights = await b.eval(`__h.headings().map((name) => { const h = [...document.querySelectorAll('main section h3')].find((x) => x.textContent.trim() === name); return { name: name.slice(0, 24), lines: Math.round(h.getBoundingClientRect().height / parseFloat(getComputedStyle(h).lineHeight)) }; })`);
  check("long exercise names wrap over several lines and are not truncated", heights.every((h) => h.lines >= 2) && await b.has(`![...document.querySelectorAll('main section h3, main section h3 a')].some((e) => e.scrollWidth > e.clientWidth + 1)`), J(heights));
  check("the action buttons of a long-named exercise stay reachable (Move, Notes, Remove all on screen)",
    await b.has(`['Move ${LONG_WORDS.slice(0, 100)} up','Notes for ${LONG_WORDS.slice(0, 100)}','Remove ${LONG_WORDS.slice(0, 100)} from workout'].every((l) => { const e = __h.find('button', l); return e && e.getBoundingClientRect().right <= innerWidth; })`));
  // A long-named exercise can still be logged against: its inputs and Log set button are clickable.
  await b.type(`document.querySelector('section input[id^="draft-"][id$="-weight"]')`, "70", "weight");
  const topmost = await b.eval(`(() => { const e = [...document.querySelectorAll('button')].find((x) => x.textContent.trim() === 'Log set'); e.scrollIntoView({block:'center'}); const r = e.getBoundingClientRect(); const t = document.elementFromPoint(r.left + r.width/2, r.top + r.height/2); return t === e || e.contains(t); })()`);
  check("Log set is clickable (not covered) on a long-named exercise", topmost);
} catch (e) { check("section completed without error", false, e.message); await b.shot("FAILED-run5-long"); }

begin("Picker race: 3 passes x 8 rapid adds must never miss a tap (fix #4)");
try {
  await api.del(`/api/v1/workouts/${active.id}`);
  const plan = [["Barbell Bench Press","barbell bench"],["Deadlift","deadlift"],["Barbell Row","barbell row"],["Lateral Raise","lateral"],["Overhead Press","overhead press"],["Leg Curl","leg curl"],["Plank","plank"],["Hip Thrust","hip thrust"]];
  const dialog = `document.querySelector('[role=dialog]')`;
  let misses = 0, total = 0, withBusyMarker = 0;
  for (let pass = 1; pass <= 3; pass++) {
    const w = (await api.post("/api/v1/workouts", {})).body;
    await b.goto(`/fitness/workouts/${w.id}`); await b.waitFor(`document.body.innerText.includes('Finish workout')`, "logger");
    for (const [name, q] of plan) {
      total++;
      await b.click(`__h.find('button','Add exercise')`, "add");
      await b.waitFor(`!!${dialog}`, "sheet");
      await b.type(`document.querySelector('[role=dialog] input[type=search]')`, q, "q");
      // Deliberately click the moment the item is visible, without waiting for the search to settle: the list must be inert until it does.
      await b.waitFor(`[...document.querySelectorAll('[role=dialog] button')].some((x) => x.querySelector('span > span')?.textContent.trim() === ${J(name)})`, `result ${name}`, 12000);
      if (await b.has(`document.querySelector('[role=dialog] ul')?.hasAttribute('aria-busy')`)) withBusyMarker++;
      await b.waitFor(`document.querySelector('[role=dialog] ul[aria-busy="false"]')`, "settled", 12000);
      await b.click(`[...document.querySelectorAll('[role=dialog] button')].find((x) => x.querySelector('span > span')?.textContent.trim() === ${J(name)})`, name);
      let ok = true; try { await b.waitFor(`__h.headings().includes(${J(name)})`, `card ${name}`, 8000); } catch { ok = false; }
      if (!ok) misses++;
    }
    await api.del(`/api/v1/workouts/${w.id}`);
  }
  check(`all ${total} rapid adds landed (${misses} missed)`, misses === 0);
  check(`the picker list carries aria-busy on every search (${withBusyMarker}/${total}); it is what makes taps inert while results change`, withBusyMarker === total);
} catch (e) { check("section completed without error", false, e.message); await b.shot("FAILED-run5-picker"); }

check("no JavaScript exceptions", b.consoleErrors.length === 0, b.consoleErrors.slice(0, 3).join(" | "));
b.close();
process.exit(summary() ? 1 : 0);
