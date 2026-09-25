// Part 2: the gym flow, end to end in the browser at 390px, verified against the API.
import { Browser, begin, check, summary, registerUser, sleep } from "../lib.mjs";
const PW = "a long enough password";
// A fresh user, so earlier runs cannot change which sets count as personal records.
const st = { emailA: `walk${Date.now()}@example.com` };
const apiA = await registerUser(st.emailA, PW, "Asha Nair");
{
  const bench = (await apiA.get("/api/v1/exercises?q=barbell%20bench&size=5")).body.items[0].id;
  const w = (await apiA.post("/api/v1/workouts", {})).body;
  const we = (await apiA.post(`/api/v1/workouts/${w.id}/exercises`, { exerciseId: bench })).body;
  for (const [weight, reps] of [[100, 5], [105, 3]]) await apiA.post(`/api/v1/workouts/${w.id}/exercises/${we.id}/sets`, { weightKg: weight, reps });
  await apiA.post(`/api/v1/workouts/${w.id}/finish`);
  await sleep(1100);
}
const b = new Browser();
await b.launch();
await b.viewport(390);

const J = JSON.stringify;
const hasText = (t) => `document.body.innerText.includes(${J(t)})`;
const card = (name) => `__h.section(${J(name)})`;
const inCard = (name, sel, text) => `__h.find(${J(sel)}, ${J(text)}, ${card(name)})`;
const cardText = (name) => b.eval(`(${card(name)})?.innerText ?? ''`);
const dialog = `document.querySelector('[role=dialog]')`;
const current = async () => (await apiA.get("/api/v1/workouts/current")).body;
const titles = () => b.eval(`__h.headings()`);

async function section(name, fn) { begin(name); try { await fn(); } catch (e) { check("section completed without error", false, e.message); await b.shot("FAILED-" + name.replace(/\W+/g, "_").slice(0, 30)); } }

async function pickExercise(name, query) {
  await b.click(`__h.find('button', 'Add exercise')`, "Add exercise");
  await b.waitFor(`!!${dialog}`, "picker sheet opens");
  await b.type(`document.querySelector('[role=dialog] input[type=search]')`, query, "picker search");
  const btn = `[...document.querySelectorAll('[role=dialog] button')].find((x) => x.querySelector('span > span')?.textContent.trim() === ${J(name)})`;
  await b.waitFor(`document.querySelector('[role=dialog] ul[aria-busy="false"]') && ${btn}`, `picker result ${name}`);
  await b.click(btn, `pick ${name}`);
  await b.waitFor(`!${dialog}`, "picker closes");
  await b.waitFor(`(${card(name)})`, `card for ${name}`);
}

async function logSet(name, weight, reps, { rpe, warmup } = {}) {
  const c = card(name);
  await b.type(`${c}.querySelector('input[id^="draft-"][id$="-weight"]')`, weight, "weight");
  await b.type(`${c}.querySelector('input[id^="draft-"][id$="-reps"]')`, reps, "reps");
  if (rpe) await b.select(`${c}.querySelector('select[id^="draft-"]')`, rpe, "rpe");
  if (warmup) await b.click(`[...${c}.querySelectorAll('label')].find((l) => l.querySelector('input[type=checkbox]'))`, "warm-up toggle");
  const before = (await cardText(name)).match(/Set (\d+)/g)?.length ?? 0;
  await b.click(inCard(name, "button", "Log set"), "Log set");
  await b.waitFor(`__h.hasSet(${J(name)}, ${Number(weight)}, ${Number(reps)})`, `set ${weight}×${reps} listed`);
  return before;
}

// ---------------------------------------------------------------------------------------------
await section("Login through the UI", async () => {
  await b.goto("/login");
  await b.type(`document.querySelector('input[name=email]')`, st.emailA, "email");
  await b.type(`document.querySelector('input[name=password]')`, PW, "password");
  await b.clickText("button", "Log in");
  await b.waitFor(`location.pathname === '/dashboard'`, "dashboard");
  check("logged in as the test user", true);
});

let workoutId;
await section("Start an empty workout and add exercises (bottom-sheet picker)", async () => {
  await b.goto("/fitness/start");
  await b.waitFor(hasText("Empty workout"), "start page");
  await b.clickText("button", "Empty workout");
  await b.waitFor(`/^\\/fitness\\/workouts\\/[0-9a-f-]{36}$/.test(location.pathname)`, "navigated to the workout");
  workoutId = (await b.url()).split("/").pop();
  check("empty workout started and opened on its own page", true);
  await b.waitFor(hasText("No exercises yet"), "empty state");
  check("new workout shows the 'No exercises yet' empty state", true);
  const cur = await current();
  check("API agrees: exactly this workout is IN_PROGRESS", cur?.id === workoutId && cur.status === "IN_PROGRESS");
  check("Finish workout bar is visible on a fresh workout", await b.has(`!!${"__h.find('button','Finish workout')"}`));

  // Bottom sheet geometry on mobile.
  await b.click(`__h.find('button', 'Add exercise')`, "Add exercise");
  await b.waitFor(`!!${dialog}`, "sheet");
  await sleep(500);
  const g = await b.eval(`(() => { const r = document.querySelector('[role=dialog]').getBoundingClientRect(); return { l: r.left, w: r.width, bottom: r.bottom, h: r.height, iw: innerWidth, ih: innerHeight }; })()`);
  check("picker is a bottom sheet: full width and docked to the bottom edge", Math.abs(g.w - g.iw) <= 1 && Math.abs(g.bottom - g.ih) <= 2, J(g));
  check("picker leaves room above it (max ~85% of the screen)", g.h <= g.ih * 0.86, J(g));
  await b.shot("picker-open-390");
  await b.type(`document.querySelector('[role=dialog] input[type=search]')`, "bench", "search");
  await sleep(700);
  const results = await b.eval(`[...document.querySelectorAll('[role=dialog] button')].map((x)=>x.innerText.split('\\n')[0]).filter((n)=>n && n !== 'Close')`);
  check("picker search filters the catalogue ('bench' -> only bench exercises)", results.length > 0 && results.every((n) => /bench/i.test(n)), J(results));
  check("picker results scroll inside the sheet without page overflow", !(await b.overflow()).overflow);
  await b.key("Escape", "Escape", 27);
  await b.waitFor(`!${dialog}`, "sheet closes on Escape");
  check("Escape closes the picker", true);
  await pickExercise("Barbell Bench Press", "barbell bench");
  check("selecting an exercise adds its card", (await titles()).includes("Barbell Bench Press"));
  check("first card shows the last-session hint from the earlier workout", (await cardText("Barbell Bench Press")).includes("Last time"));
  // An exercise already in the workout is shown as added and cannot be picked twice.
  await b.click(`__h.find('button', 'Add exercise')`, "Add exercise again");
  await b.waitFor(`!!${dialog}`, "sheet");
  await b.type(`document.querySelector('[role=dialog] input[type=search]')`, "barbell bench", "search");
  await sleep(700);
  check("an exercise already added is marked 'Added' and disabled",
    await b.has(`[...document.querySelectorAll('[role=dialog] button')].some((x) => x.innerText.includes('Barbell Bench Press') && x.innerText.includes('Added') && x.disabled)`));
  await b.key("Escape", "Escape", 27);
});

await section("Log sets: validation, PR badges, RPE, warm-up", async () => {
  // Client-side validation
  const c = card("Barbell Bench Press");
  await b.type(`${c}.querySelector('input[id^="draft-"][id$="-weight"]')`, "", "clear weight");
  await b.type(`${c}.querySelector('input[id^="draft-"][id$="-reps"]')`, "", "clear reps");
  await b.click(inCard("Barbell Bench Press", "button", "Log set"), "Log set (empty)");
  await sleep(200);
  let t = await cardText("Barbell Bench Press");
  check("empty weight/reps are rejected with field messages", t.includes("0 to 1000 kg") && t.includes("1 to 100"), t.slice(-160));
  await b.type(`${c}.querySelector('input[id^="draft-"][id$="-weight"]')`, "-5", "weight");
  await b.type(`${c}.querySelector('input[id^="draft-"][id$="-reps"]')`, "0", "reps");
  await b.click(inCard("Barbell Bench Press", "button", "Log set"), "Log set (invalid)");
  await sleep(200);
  t = await cardText("Barbell Bench Press");
  check("negative weight and zero reps are rejected", t.includes("0 to 1000 kg") && t.includes("1 to 100"));
  check("nothing was saved by the invalid attempts", (await current()).exercises[0].sets.length === 0);

  await logSet("Barbell Bench Press", "100", "5");
  t = await cardText("Barbell Bench Press");
  check("set 1 (100 kg x 5) is listed", await b.has(`__h.hasSet("Barbell Bench Press", 100, 5)`));
  check("a tie with the earlier 100 x 5 is not flagged as a record", !t.includes("PR"));
  check("the draft keeps weight and reps for the next set", await b.eval(`${card("Barbell Bench Press")}.querySelector('input[id^="draft-"][id$="-weight"]').value`) === "100");

  await logSet("Barbell Bench Press", "110", "2", { rpe: "8.5" });
  t = await cardText("Barbell Bench Press");
  check("set 2 shows RPE 8.5", await b.has(`__h.hasSet("Barbell Bench Press", 110, 2, {rpe: 8.5})`));
  check("set 2 is flagged Weight PR and 1RM PR (beats the earlier workout)", t.includes("Weight PR") && t.includes("1RM PR"), t);
  await b.shot("logger-with-sets-390");

  await logSet("Barbell Bench Press", "40", "10", { warmup: true });
  t = await cardText("Barbell Bench Press");
  check("set 3 is a warm-up and is not a record", await b.has(`__h.hasSet("Barbell Bench Press", 40, 10, {warmup: true})`));

  const cur = await current();
  const sets = cur.exercises[0].sets;
  check("API: 3 sets stored with the right values", sets.length === 3 && sets[1].weightKg === 110 && sets[1].rpe === 8.5 && sets[2].warmup === true, J(sets));
  check("API: totals exclude the warm-up (2 working sets, 720 kg)", cur.totals.workingSets === 2 && cur.totals.volumeKg === 720, J(cur.totals));
  { const t = await b.text();   // the redesign shows totals as label/value tiles: "Working sets 2", "Volume 720kg"
    check("header shows the updated totals (2 working sets, 720 kg)", /Working sets\s*\n?\s*2\b/.test(t) && /Volume\s*\n?\s*720\s*kg/.test(t), t.split("\n").slice(0, 14).join(" | ")); }
});

await section("Edit a set and clear its RPE; delete a set", async () => {
  await b.click(`__h.find('button', 'Edit set 2 of Barbell Bench Press')`, "Edit set 2");
  await b.waitFor(hasText("Edit set 2"), "edit form");
  await b.type(`document.querySelector('input[id^="edit-"][id$="-reps"]')`, "3", "edit reps");
  await b.select(`document.querySelector('select[id^="edit-"]')`, "", "clear rpe");
  await b.click(`__h.find('button', 'Save')`, "Save edit");
  await b.waitFor(`__h.hasSet("Barbell Bench Press", 110, 3)`, "edited set shown");
  const t = await cardText("Barbell Bench Press");
  check("edited set shows the new reps", await b.has(`__h.hasSet("Barbell Bench Press", 110, 3)`));
  check("RPE is gone after clearing it in the edit form", !t.includes("RPE 8.5"));
  const s2 = (await current()).exercises[0].sets[1];
  check("API: set 2 reps=3 and rpe=null", s2.reps === 3 && s2.rpe === null, J(s2));
  check("edited set still a record (110 kg beats 105 kg)", t.includes("Weight PR"));

  // Invalid edit is rejected client-side and keeps the form open.
  await b.click(`__h.find('button', 'Edit set 1 of Barbell Bench Press')`, "Edit set 1");
  await b.type(`document.querySelector('input[id^="edit-"][id$="-weight"]')`, "1001", "edit weight");
  await b.click(`__h.find('button', 'Save')`, "Save invalid");
  await sleep(200);
  check("editing a weight above 1000 kg is rejected with a message", (await b.text()).includes("0 to 1000 kg"));
  await b.click(`__h.find('button', 'Cancel')`, "Cancel edit");
  check("cancel closes the edit form without changes", (await current()).exercises[0].sets[0].weightKg === 100);

  // Two-tap delete of the warm-up (set 3).
  const label = "Delete set 3 of Barbell Bench Press";
  await b.click(`__h.findContains('button', ${J(label)})`, "delete tap 1");
  check("first tap only arms the delete (button turns into Delete confirmation)", (await current()).exercises[0].sets.length === 3 && await b.has(`__h.findContains('button', 'Delete: ${label}')`));
  await b.click(`__h.findContains('button', ${J(label)})`, "delete tap 2");
  await b.waitFor(`!__h.hasSet("Barbell Bench Press", 40, 10)`, "warm-up removed");
  check("second tap deletes the set", (await current()).exercises[0].sets.length === 2);
  check("remaining sets stay numbered 1..2", J((await current()).exercises[0].sets.map((s) => s.setNumber)) === "[1,2]");
});

await section("Add, reorder and remove exercises; exercise notes", async () => {
  await pickExercise("Deadlift", "deadlift");
  await pickExercise("Barbell Row", "barbell row");
  await pickExercise("Lateral Raise", "lateral");
  check("exercises appear in the order added", J(await titles()) === J(["Barbell Bench Press", "Deadlift", "Barbell Row", "Lateral Raise"]), J(await titles()));

  await b.click(`__h.find('button', 'Move Barbell Row up')`, "move row up");
  await sleep(500);
  check("moving Barbell Row up reorders the cards", J(await titles()) === J(["Barbell Bench Press", "Barbell Row", "Deadlift", "Lateral Raise"]), J(await titles()));
  check("API: the new order is stored", J((await current()).exercises.map((e) => e.exercise.name)) === J(["Barbell Bench Press", "Barbell Row", "Deadlift", "Lateral Raise"]));
  check("the first card's Move-up is disabled and the last card's Move-down is disabled",
    await b.has(`__h.find('button','Move Barbell Bench Press up').disabled`) && await b.has(`__h.find('button','Move Lateral Raise down').disabled`));

  const removeLabel = "Remove Deadlift from workout";
  await b.click(`__h.findContains('button', ${J(removeLabel)})`, "remove tap 1");
  check("first remove tap only arms it", (await titles()).includes("Deadlift"));
  await b.click(`__h.findContains('button', ${J(removeLabel)})`, "remove tap 2");
  await b.waitFor(`!__h.headings().includes('Deadlift')`, "deadlift gone");
  check("second tap removes the exercise", J(await titles()) === J(["Barbell Bench Press", "Barbell Row", "Lateral Raise"]));
  check("API: removal stored and positions are 1..3", J((await current()).exercises.map((e) => e.position)) === "[1,2,3]");

  await b.click(`__h.find('button', 'Notes for Barbell Row')`, "notes toggle");
  await b.waitFor(`!!(${card("Barbell Row")}).querySelector('textarea')`, "notes textarea");
  await b.type(`${card("Barbell Row")}.querySelector('textarea')`, "felt strong today", "notes");
  await b.eval(`${card("Barbell Row")}.querySelector('textarea').blur()`);
  await sleep(700);
  check("API: exercise notes saved on blur", (await current()).exercises[1].notes === "felt strong today", J((await current()).exercises[1].notes));

  await logSet("Barbell Row", "80", "8");
  await logSet("Barbell Row", "80", "8");
  check("logging on a second exercise works", (await current()).exercises[1].sets.length === 2);
});

await section("Mobile ergonomics: touch targets, sticky bar, keyboard reveal", async () => {
  await b.eval("window.scrollTo(0, 0)");
  const sizes = await b.eval(`(() => { const m = (e) => { const r = e.getBoundingClientRect(); return { w: Math.round(r.width), h: Math.round(r.height) }; };
    const c = ${card("Barbell Row")};
    return { weight: m(c.querySelector('input[id^="draft-"][id$="-weight"]')), reps: m(c.querySelector('input[id^="draft-"][id$="-reps"]')),
      rpe: m(c.querySelector('select')), logSet: m([...c.querySelectorAll('button')].find((x)=>x.textContent.trim()==='Log set')),
      finish: m(__h.find('button','Finish workout')), up: m(__h.find('button','Move Barbell Row up')),
      edit: m(__h.find('button','Edit set 1 of Barbell Row')), warm: m([...c.querySelectorAll('label')].find((l)=>l.querySelector('input[type=checkbox]'))) }; })()`);
  for (const [k, v] of Object.entries(sizes)) check(`touch target '${k}' is at least 44px tall (is ${v.h}px)`, v.h >= 44, J(v));
  check("weight and reps inputs are wide enough for their values (>=80px, 24px text)", sizes.weight.w >= 80 && sizes.reps.w >= 80, J(sizes));
  const fontSizes = await b.eval(`[...document.querySelectorAll('input[inputmode], textarea')].filter((e)=>__h.visible(e)).map((e)=>parseFloat(getComputedStyle(e).fontSize))`);
  check("inputs use >=16px text so iOS does not zoom on focus", fontSizes.length > 0 && fontSizes.every((f) => f >= 16), J(fontSizes));
  check("decimal/numeric keyboards requested for weight and reps",
    await b.has(`document.querySelector('input[inputmode=decimal]') && document.querySelector('input[inputmode=numeric]')`));

  const bar = await b.eval(`(() => { const btn = __h.find('button','Finish workout'); const bar = btn.closest('.sticky'); const r = bar.getBoundingClientRect(); return { bottom: r.bottom, ih: innerHeight, h: r.height, w: r.width, iw: innerWidth }; })()`);
  check("sticky finish bar is docked to the bottom of the viewport at the top of the page", Math.abs(bar.bottom - bar.ih) <= 1, J(bar));
  check("sticky finish bar spans the full width", Math.abs(bar.w - bar.iw) <= 1, J(bar));
  await b.eval("window.scrollTo(0, document.documentElement.scrollHeight)");
  await sleep(300);
  const bar2 = await b.eval(`(() => { const btn = __h.find('button','Finish workout'); const bar = btn.closest('.sticky'); const r = bar.getBoundingClientRect(); return { bottom: r.bottom, ih: innerHeight }; })()`);
  check("sticky finish bar is still docked at the end of the page", Math.abs(bar2.bottom - bar2.ih) <= 1, J(bar2));
  for (const label of ["Add exercise", "Discard workout"]) {
    const ok = await b.eval(`(() => { const el = __h.find('button', ${J(label)}); const r = el.getBoundingClientRect(); const top = document.elementFromPoint(r.left + r.width/2, r.top + r.height/2); return top === el || el.contains(top); })()`);
    check(`at the end of the page '${label}' is not covered by the sticky bar`, ok);
  }
  await b.shot("logger-bottom-390");

  // Worst case for a sticky bar: focusing an input that sits just under it. The browser scrolls it into view,
  // but only clear of the bar if the page reserves scroll padding for it.
  const revealed = await b.eval(`(() => { const c = ${card("Barbell Bench Press")}; const input = c.querySelector('input[id^="draft-"][id$="-weight"]');
    window.scrollTo(0, 0); const r0 = input.getBoundingClientRect(); window.scrollTo(0, window.scrollY + r0.top - (innerHeight - 30)); // input now peeks out at the very bottom
    input.focus(); const r = input.getBoundingClientRect(); const top = document.elementFromPoint(r.left + r.width/2, r.top + r.height/2);
    return { covered: !(top === input), bottom: r.bottom, ih: innerHeight, top: top ? top.tagName + '.' + String(top.className).slice(0, 40) : null }; })()`);
  await sleep(300);
  check("a focused input is revealed clear of the sticky finish bar", !revealed.covered, J(revealed));
});

await section("Finish: confirmation, then the workout becomes read-only", async () => {
  await b.click(`__h.find('button', 'Finish workout')`, "Finish (bar)");
  await b.waitFor(`!!${dialog}`, "finish dialog");
  const dtext = await b.eval(`${dialog}.innerText`);
  check("confirmation dialog explains what will happen", dtext.includes("Finish this workout?") && dtext.includes("Exercises without any sets are removed"), dtext.slice(0, 200));
  await b.shot("finish-dialog-390");
  await b.click(`__h.find('button', 'Keep training', ${dialog})`, "Keep training");
  await b.waitFor(`!${dialog}`, "dialog closes");
  check("'Keep training' closes the dialog and leaves the workout active", (await current())?.id === workoutId);
  await b.click(`__h.find('button', 'Finish workout')`, "Finish (bar) again");
  await b.waitFor(`!!${dialog}`, "finish dialog");
  await b.click(`__h.find('button', 'Finish workout', ${dialog})`, "Finish (confirm)");
  await b.waitFor(hasText("Completed") + ` && !${"__h.find('button','Log set')"}`, "summary view", 15000);
  check("workout page switched to the completed summary", true);
  check("no active workout remains (API 204)", (await apiA.get("/api/v1/workouts/current")).status === 204);
  const done = (await apiA.get(`/api/v1/workouts/${workoutId}`)).body;
  check("API: status COMPLETED with a finish time", done.status === "COMPLETED" && !!done.finishedAt);
  check("API: exercise with no sets (Lateral Raise) was dropped on finish", J(done.exercises.map((e) => e.exercise.name)) === J(["Barbell Bench Press", "Barbell Row"]), J(done.exercises.map((e) => e.exercise.name)));
  const t = await b.text();
  check("summary lists the two remaining exercises", t.includes("Barbell Bench Press") && t.includes("Barbell Row") && !t.includes("Lateral Raise"));
  check("summary shows a personal record for the 110 kg set", t.includes("personal record") && t.includes("Weight PR"));
  await b.shot("summary-390");

  // Read-only: no logging controls remain.
  check("read-only: no 'Log set' buttons", !(await b.has(`__h.find('button','Log set')`)));
  check("read-only: no 'Add exercise' button", !(await b.has(`__h.find('button','Add exercise')`)));
  check("read-only: no 'Finish workout' bar", !(await b.has(`__h.find('button','Finish workout')`)));
  check("read-only: no set Edit or Delete buttons", !(await b.has(`__h.findContains('button','Edit set')`)) && !(await b.has(`__h.findContains('button','Delete set')`)));
  check("read-only: no move/remove/notes exercise controls", !(await b.has(`__h.findContains('button','Move ')`)) && !(await b.has(`__h.findContains('button','Remove ')`)));
  check("read-only: no numeric input fields for sets", !(await b.has(`document.querySelector('input[inputmode]')`)));
  // ...but the API refuses changes too.
  const we = done.exercises[0].id;
  const r = await apiA.post(`/api/v1/workouts/${workoutId}/exercises/${we}/sets`, { weightKg: 50, reps: 5 });
  check("API refuses a new set on the completed workout (409)", r.status === 409 && r.body.code === "WORKOUT_NOT_IN_PROGRESS", J(r));

  // Editable: name and notes.
  check("editable: Name and notes form is present", (await b.text()).includes("Name and notes"));
  await b.type(`document.querySelector('input[name=name]')`, "Chest and Back", "workout name");
  await b.type(`document.querySelector('textarea[name=notes]')`, "Solid session, PRs on bench.", "notes");
  await b.click(`__h.find('button', 'Save changes')`, "Save name/notes");
  await b.waitFor(hasText("Saved."), "saved notice");
  await sleep(600);
  const renamed = (await apiA.get(`/api/v1/workouts/${workoutId}`)).body;
  check("API: name and notes were saved", renamed.name === "Chest and Back" && renamed.notes === "Solid session, PRs on bench.", J([renamed.name, renamed.notes]));
  check("page title reflects the new name", await b.has(`document.querySelector('h1').textContent.includes('Chest and Back')`));
  check("renamed workout is still read-only for sets", (await apiA.get(`/api/v1/workouts/${workoutId}`)).body.exercises[0].sets.length === 2);
  check("editable: Delete workout control is present", await b.has(`__h.find('button','Delete workout')`));
});

check("no JavaScript exceptions during the whole walkthrough", b.consoleErrors.length === 0, b.consoleErrors.slice(0, 3).join(" | "));
b.close();
process.exit(summary() ? 1 : 0);
