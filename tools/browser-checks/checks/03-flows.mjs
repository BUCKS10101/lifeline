// Part 3: start flows, resume, conflicts, discard, templates, exercises, history, delete, desktop.
import { Browser, begin, check, summary, registerUser, sleep } from "../lib.mjs";
const PW = "a long enough password";
const J = JSON.stringify;
const email = `flow${Date.now()}@example.com`;
const api = await registerUser(email, PW, "Asha Nair");
const b = new Browser();
await b.launch();

const hasText = (t) => `document.body.innerText.includes(${J(t)})`;
const dialog = `document.querySelector('[role=dialog]')`;
const titles = () => b.eval(`__h.headings()`);
const current = async () => (await api.get("/api/v1/workouts/current"));
const exId = async (q) => (await api.get(`/api/v1/exercises?q=${encodeURIComponent(q)}&size=100`)).body.items.find((e) => e.name.toLowerCase() === q.toLowerCase())?.id;
const cardBySpan = (name) => `__h.row(${J(name)})`;
const inTemplateCardContains = (name, sel, text) => `__h.findContains(${J(sel)}, ${J(text)}, ${cardBySpan(name)})`;
const workoutUrl = `/^\\/fitness\\/workouts\\/[0-9a-f-]{36}$/.test(location.pathname)`;

async function section(name, fn) { begin(name); try { await fn(); } catch (e) { check("section completed without error", false, e.message); await b.shot("FAILED-" + name.replace(/\W+/g, "_").slice(0, 30)); } }
async function pickExercise(name, query) {
  await b.click(`__h.find('button', 'Add exercise')`, "Add exercise");
  await b.waitFor(`!!${dialog}`, "picker opens");
  await b.type(`document.querySelector('[role=dialog] input[type=search]')`, query, "picker search");
  const btn = `[...document.querySelectorAll('[role=dialog] button')].find((x) => x.querySelector('span > span')?.textContent.trim() === ${J(name)})`;
  await b.waitFor(`document.querySelector('[role=dialog] ul[aria-busy="false"]') && ${btn}`, `picker result ${name}`);
  await b.click(btn, `pick ${name}`);
  await b.waitFor(`!${dialog}`, "picker closes");
}
async function uiLogin() {
  await b.goto("/login");
  await b.type(`document.querySelector('input[name=email]')`, email, "email");
  await b.type(`document.querySelector('input[name=password]')`, PW, "password");
  await b.clickText("button", "Log in");
  await b.waitFor(`location.pathname === '/dashboard'`, "dashboard");
}
async function completedViaApi(name, exerciseName, pairs) {
  const ex = await exId(exerciseName);
  const w = (await api.post("/api/v1/workouts", { name })).body;
  const we = (await api.post(`/api/v1/workouts/${w.id}/exercises`, { exerciseId: ex })).body;
  for (const [weight, reps] of pairs) await api.post(`/api/v1/workouts/${w.id}/exercises/${we.id}/sets`, { weightKg: weight, reps });
  return (await api.post(`/api/v1/workouts/${w.id}/finish`)).body;
}

await b.viewport(390);
await uiLogin();

// ---------------------------------------------------------------------------------------------
await section("Start Push / Pull / Legs / empty from the start page", async () => {
  for (const [tpl, count, first] of [["Push", 6, "Barbell Bench Press"], ["Pull", 7, "Pull-Up"], ["Legs", 6, "Barbell Back Squat"]]) {
    await b.goto("/fitness/start");
    await b.waitFor(hasText("Empty workout"), "start page");
    await b.click(`__h.find('button', 'Start', ${cardBySpan(tpl)})`, `Start ${tpl}`);
    await b.waitFor(workoutUrl + ` && document.body.innerText.includes('Finish workout')`, `${tpl} workout opens`);
    const t = await titles();
    check(`${tpl}: opens a workout with ${count} exercises, first is ${first}`, t.length === count && t[0] === first, J(t));
    const cur = (await current()).body;
    check(`${tpl}: API workout is named '${tpl}', IN_PROGRESS, no sets yet`, cur.name === tpl && cur.status === "IN_PROGRESS" && cur.totals.sets === 0);
    await api.del(`/api/v1/workouts/${cur.id}`);   // discard so the next template can be started from the start page
  }
});

await section("Resume, and the 409 'already active' handling", async () => {
  const pushTemplate = (await api.get("/api/v1/workout-templates?size=100")).body.items.find((t) => t.name === "Push" && t.builtIn).id;
  await api.post("/api/v1/workouts", { templateId: pushTemplate });   // an active Push workout to resume
  const active = (await current()).body;
  await b.goto("/fitness");
  await b.waitFor(hasText("Workout in progress") + " && " + hasText("Resume workout"), "resume card");
  check("hub names the active workout (Push) in the resume panel", (await b.text()).includes("Push"));
  check("hub shows the active workout with a Resume button", (await b.text()).includes("Resume workout"));
  await b.clickText("a", "Resume workout");
  await b.waitFor(workoutUrl, "resume navigates");
  check("Resume on the hub returns to the same workout", (await b.url()).endsWith(active.id));
  await b.waitFor(hasText("Finish workout"), "logger loaded");
  check("resumed workout still shows all 6 exercises", (await titles()).length === 6);

  await b.goto("/dashboard");
  await b.waitFor(hasText("Resume workout"), "dashboard resume");
  check("dashboard 'Today's workout' card offers Resume for the active workout", (await b.text()).includes("Push"));
  await b.clickText("a", "Resume workout");
  await b.waitFor(workoutUrl, "dashboard resume navigates");
  check("Resume on the dashboard opens the same workout", (await b.url()).endsWith(active.id));

  await b.goto("/fitness/start");
  await b.waitFor(hasText("You already have a workout in progress"), "start page conflict");
  check("start page explains one workout is already active (no templates offered)", !(await b.has(`__h.find('button','Empty workout')`)));
  await b.clickText("a", "Resume workout");
  await b.waitFor(workoutUrl, "start page resume");
  check("start page Resume link opens the active workout", (await b.url()).endsWith(active.id));

  // Starting from the templates page while one is active: server answers 409, UI explains and offers Resume.
  await b.goto("/fitness/templates");
  await b.waitFor(hasText("Built-in templates"), "templates page");
  await b.click(`__h.find('button', 'Start', ${cardBySpan("Legs")})`, "Start Legs (templates page)");
  await b.waitFor(hasText("already have a workout in progress"), "409 message on templates page");
  check("409 from the templates page shows a clear message", true);
  await b.clickText("a", "Resume it");
  await b.waitFor(workoutUrl, "resume it");
  check("'Resume it' link opens the workout that is in the way", (await b.url()).endsWith(active.id));
  check("no second workout was created by the rejected start", (await api.get("/api/v1/workouts?status=IN_PROGRESS")).body.totalItems === 1);
});

await section("Discard an active workout (confirmation, then gone)", async () => {
  const active = (await current()).body;
  const weId = active.exercises[0].id;
  await api.post(`/api/v1/workouts/${active.id}/exercises/${weId}/sets`, { weightKg: 60, reps: 8 });
  await b.goto(`/fitness/workouts/${active.id}`);
  await b.waitFor(hasText("Discard workout"), "logger");
  await b.clickText("button", "Discard workout");
  await b.waitFor(`!!${dialog}`, "discard dialog");
  check("discard asks for confirmation and warns it cannot be undone", (await b.eval(`${dialog}.innerText`)).includes("cannot be undone"));
  await b.shot("discard-dialog-390");
  await b.click(`__h.find('button','Keep it', ${dialog})`, "Keep it");
  await b.waitFor(`!${dialog}`, "dialog closed");
  check("'Keep it' leaves the workout untouched", (await current()).body?.id === active.id);
  await b.clickText("button", "Discard workout");
  await b.waitFor(`!!${dialog}`, "discard dialog again");
  await b.click(`__h.find('button','Discard workout', ${dialog})`, "confirm discard");
  await b.waitFor(`location.pathname === '/fitness'`, "back on hub after discard");
  check("discarding returns to the Fitness hub", true);
  check("API: no active workout after discard (204)", (await current()).status === 204);
  check("API: the discarded workout and its sets are gone (404)", (await api.get(`/api/v1/workouts/${active.id}`)).status === 404);
  await b.waitFor(hasText("Ready to train?"), "hub back to start state");
  check("hub goes back to 'Ready to train?'", true);
});

await section("409 when a workout is started elsewhere while the start page is open", async () => {
  await b.goto("/fitness/start");
  await b.waitFor(hasText("Empty workout"), "start page");
  const elsewhere = (await api.post("/api/v1/workouts", { name: "Started on my other device" })).body;
  await b.clickText("button", "Empty workout");
  await b.waitFor(hasText("already have a workout in progress"), "409 notice");
  check("start page shows the 409 message instead of failing silently", true);
  check("start page stays put (no navigation on failure)", (await b.url()) === "/fitness/start");
  await b.clickText("a", "Resume it");
  await b.waitFor(workoutUrl, "resume it");
  check("'Resume it' finds and opens the workout started elsewhere", (await b.url()).endsWith(elsewhere.id));
  await api.del(`/api/v1/workouts/${elsewhere.id}`);
});

// ---------------------------------------------------------------------------------------------
await section("Templates: create, validate, edit, duplicate, start, delete", async () => {
  await b.goto("/fitness/templates");
  await b.waitFor(hasText("No templates of your own yet"), "templates page");
  await b.clickText("a", "New template");
  await b.waitFor(`location.pathname === '/fitness/templates/new'`, "new template page");
  await b.type(`document.querySelector('input[value=""]') || document.querySelector('form input')`, "UI Template", "template name");
  await b.clickText("button", "Create template");
  await sleep(300);
  check("creating with no exercises is rejected", (await b.text()).includes("Add at least one exercise"));
  await pickExercise("Overhead Press", "overhead press");
  await pickExercise("Lateral Raise", "lateral");
  check("both exercises are listed in the editor, in order", J(await b.eval(`__h.editorNames()`)) === J(["Overhead Press", "Lateral Raise"]), J(await b.eval(`__h.editorNames()`)));
  await b.type(`document.querySelector('input[aria-label="Target sets for Overhead Press"]')`, "25", "target sets");
  await b.clickText("button", "Create template");
  await sleep(300);
  check("target sets above 20 are rejected with a message", (await b.text()).includes("must be a whole number from 1 to 20"));
  await b.type(`document.querySelector('input[aria-label="Target sets for Overhead Press"]')`, "4", "target sets");
  await b.click(`__h.find('button','Move Lateral Raise up')`, "move up");
  check("moving reorders the rows in the editor", J(await b.eval(`__h.editorNames()`)) === J(["Lateral Raise", "Overhead Press"]), J(await b.eval(`__h.editorNames()`)));
  await b.shot("template-editor-filled-390");
  await b.clickText("button", "Create template");
  await b.waitFor(`location.pathname === '/fitness/templates' && document.body.innerText.includes('UI Template')`, "created and listed");
  check("template created and listed under 'My templates'", true);
  let t = (await api.get("/api/v1/workout-templates?size=100")).body.items.find((x) => x.name === "UI Template");
  const detail = (await api.get(`/api/v1/workout-templates/${t.id}`)).body;
  check("API: order is [Lateral Raise, Overhead Press] with 4 target sets on Overhead Press",
    J(detail.exercises.map((e) => e.exercise.name)) === J(["Lateral Raise", "Overhead Press"]) && detail.exercises[1].targetSets === 4, J(detail.exercises.map((e) => [e.exercise.name, e.targetSets])));

  // Edit
  await b.clickText("a", "UI Template");
  await b.waitFor(hasText("Edit template"), "editor");
  await b.type(`document.querySelector('form input')`, "UI Template 2", "rename");
  await b.click(`__h.find('button','Remove Lateral Raise')`, "remove row");
  await b.clickText("button", "Save template");
  await b.waitFor(`location.pathname === '/fitness/templates' && document.body.innerText.includes('UI Template 2')`, "edited and listed");
  const edited = (await api.get(`/api/v1/workout-templates/${t.id}`)).body;
  check("API: renamed and reduced to one exercise", edited.name === "UI Template 2" && edited.exercises.length === 1 && edited.exercises[0].exercise.name === "Overhead Press", J(edited));

  // Duplicate a built-in and an own template
  await b.click(`__h.find('button', 'Duplicate', ${cardBySpan("Push")})`, "Duplicate Push");
  await b.waitFor(hasText("Edit template"), "editor for the copy");
  check("duplicating a built-in opens an editable copy", await b.eval(`document.querySelector('form input').value`) === "Push (copy)");
  const pushCopy = (await api.get("/api/v1/workout-templates?size=100")).body.items.find((x) => x.name === "Push (copy)");
  check("API: the copy is user-owned with all 6 exercises", pushCopy && !pushCopy.builtIn && pushCopy.exerciseCount === 6);
  await b.goto("/fitness/templates");
  await b.waitFor(hasText("Push (copy)"), "list with copy");
  await b.click(`__h.find('button', 'Duplicate', ${cardBySpan("UI Template 2")})`, "Duplicate own");
  await b.waitFor(hasText("Edit template"), "editor for own copy");
  check("duplicating your own template works", (await api.get("/api/v1/workout-templates?size=100")).body.items.some((x) => x.name === "UI Template 2 (copy)"));

  // Built-ins cannot be edited or deleted from the UI.
  await b.goto("/fitness/templates");
  await b.waitFor(hasText("Built-in templates"), "list");
  check("built-in template cards have no Edit or Delete", !(await b.has(inTemplateCardContains("Push", "button,a", "Edit"))) && !(await b.has(inTemplateCardContains("Push", "button", "Delete"))));
  await b.goto(`/fitness/templates/${(await api.get("/api/v1/workout-templates?size=100")).body.items.find((x) => x.name === "Push" && x.builtIn).id}`);
  await b.waitFor(hasText("Built-in"), "built-in view");
  check("built-in template opens read-only (no editor form)", !(await b.has(`document.querySelector('form')`)) && (await b.eval(`document.querySelectorAll('main ol > li').length`)) === 6 && (await b.text()).includes("Barbell Bench Press"));

  // Start from a custom template via the UI
  await b.goto("/fitness/templates");
  await b.waitFor(hasText("UI Template 2"), "list");
  await b.click(`__h.find('button', 'Start', ${cardBySpan("UI Template 2")})`, "Start custom template");
  await b.waitFor(workoutUrl + ` && document.body.innerText.includes('Finish workout')`, "custom template workout");
  check("a custom template starts a workout with its exercises", J(await titles()) === J(["Overhead Press"]));
  await api.del(`/api/v1/workouts/${(await current()).body.id}`);

  // Delete (two-tap)
  await b.goto("/fitness/templates");
  await b.waitFor(hasText("UI Template 2 (copy)"), "list");
  for (const name of ["UI Template 2 (copy)", "Push (copy)", "UI Template 2"]) {
    const del = inTemplateCardContains(name, "button", "Delete template");
    await b.click(del, `delete ${name} tap 1`);
    check(`delete '${name}': first tap only arms it`, (await api.get("/api/v1/workout-templates?size=100")).body.items.some((x) => x.name === name));
    await b.click(inTemplateCardContains(name, "button", "Delete template"), `delete ${name} tap 2`);
    await b.waitFor(`!(${cardBySpan(name)})`, `${name} removed from list`);
    check(`delete '${name}': second tap removes it`, !(await api.get("/api/v1/workout-templates?size=100")).body.items.some((x) => x.name === name));
  }
  await b.waitFor(hasText("No templates of your own yet"), "empty again");
  check("after deleting all custom templates the empty state returns", true);
  check("built-in Push, Pull and Legs are untouched", (await api.get("/api/v1/workout-templates?size=100")).body.items.filter((x) => x.builtIn).length === 3);
});

// ---------------------------------------------------------------------------------------------
await section("Exercises: create, validate, edit, history, archive, delete", async () => {
  await b.goto("/fitness/exercises");
  await b.waitFor(hasText("New custom exercise"), "exercises page");
  await b.clickText("button", "New custom exercise");
  await b.waitFor(hasText("Create exercise"), "form open");
  await b.type(`document.querySelector('input[name=name]')`, "deadlift", "duplicate name");
  await b.clickText("button", "Create exercise");
  await b.waitFor(hasText("already exists"), "duplicate error");
  check("a name that clashes with a built-in is rejected with a clear message", true);
  await b.type(`document.querySelector('input[name=name]')`, "UI Squat Variant", "name");
  await b.select(`document.querySelector('select[name=primaryMuscleGroup]')`, "QUADS", "muscle group");
  await b.type(`document.querySelector('input[name=equipment]')`, "Barbell", "equipment");
  await b.clickText("button", "Create exercise");
  await b.waitFor(`/^\\/fitness\\/exercises\\/[0-9a-f-]{36}$/.test(location.pathname) && document.body.innerText.includes('UI Squat Variant')`, "detail page");
  const id = (await b.url()).split("/").pop();
  check("creating a custom exercise opens its page, marked Custom", (await b.text()).includes("Custom") && (await b.text()).includes("Quads"));
  check("new exercise has no records or history yet", (await b.text()).includes("No records yet") && (await b.text()).includes("Not done yet"));
  const ex = (await api.get(`/api/v1/exercises/${id}/records`)).body.exercise;
  check("API: stored as QUADS / Barbell / not built-in", ex.primaryMuscleGroup === "QUADS" && ex.equipment === "Barbell" && !ex.builtIn);

  await b.type(`document.querySelector('input[name=name]')`, "UI Squat Variant 2", "rename");
  await b.type(`document.querySelector('input[name=equipment]')`, "", "clear equipment");
  await b.clickText("button", "Save changes");
  await b.waitFor(hasText("Saved."), "saved");
  await sleep(700);
  const ex2 = (await api.get(`/api/v1/exercises/${id}/records`)).body.exercise;
  check("API: renamed and equipment cleared", ex2.name === "UI Squat Variant 2" && ex2.equipment === null, J(ex2));
  check("page heading follows the rename", await b.has(`document.querySelector('h1').textContent.includes('UI Squat Variant 2')`));

  await b.goto("/fitness/exercises?q=variant");
  await b.waitFor(hasText("UI Squat Variant 2"), "search result");
  check("search finds the custom exercise and labels it Custom", (await b.text()).includes("Custom"));
  await b.goto("/fitness/exercises?muscleGroup=QUADS&q=variant");
  check("muscle-group filter works together with search", (await b.text()).includes("UI Squat Variant 2"));
  await b.goto("/fitness/exercises?muscleGroup=CHEST&q=variant");
  await b.waitFor(hasText("No exercises found"), "filtered out");
  check("a different muscle group filters it out", true);

  // Use it in a finished workout, then look at its page again.
  const done = await completedViaApi("Squat day", "UI Squat Variant 2", [[80, 8], [90, 5]]);
  await b.goto(`/fitness/exercises/${id}`);
  await b.waitFor(hasText("Personal bests"), "records");
  await sleep(300);
  let t = await b.text();
  check("exercise page shows heaviest set 90 kg x 5 and a history entry", t.includes("90 kg × 5") && t.includes("80×8"), t.slice(0, 300));
  await b.click(`__h.findContains('a', '80×8')`, "history entry link");
  await b.waitFor(workoutUrl, "history link opens the workout");
  check("a history entry links to that workout", (await b.url()).endsWith(done.id));

  // Archive (used exercise): dialog then confirm
  await b.goto(`/fitness/exercises/${id}`);
  await b.waitFor(hasText("Remove exercise"), "manage card");
  await b.clickText("button", "Remove exercise");
  await b.waitFor(`!!${dialog}`, "remove dialog");
  check("remove dialog explains archive-vs-delete", (await b.eval(`${dialog}.innerText`)).includes("hidden from the catalogue"));
  await b.click(`__h.find('button','Keep it', ${dialog})`, "Keep it");
  await b.waitFor(`!${dialog}`, "closed");
  check("'Keep it' leaves the exercise untouched", (await api.get(`/api/v1/exercises?q=squat%20variant&size=10`)).body.items.length === 1);
  await b.clickText("button", "Remove exercise");
  await b.waitFor(`!!${dialog}`, "dialog");
  await b.click(`__h.find('button','Remove exercise', ${dialog})`, "confirm remove");
  await b.waitFor(`location.pathname === '/fitness/exercises'`, "back on list");
  const hidden = (await api.get(`/api/v1/exercises?q=squat%20variant&size=10`)).body.items.length;
  const withArchived = (await api.get(`/api/v1/exercises?q=squat%20variant&size=10&includeArchived=true`)).body.items;
  check("used exercise is archived, not deleted: hidden by default, present with includeArchived", hidden === 0 && withArchived.length === 1 && withArchived[0].archived === true, J(withArchived));
  check("its finished workout still shows the exercise (history intact)", J((await api.get(`/api/v1/workouts/${done.id}`)).body.exercises.map((e) => e.exercise.name)) === J(["UI Squat Variant 2"]));
  await b.goto(`/fitness/exercises/${id}`);
  await b.waitFor(hasText("Removed"), "archived page");
  check("archived exercise page still opens, marked Removed, without the edit card", !(await b.text()).includes("Your exercise") && (await b.text()).includes("90 kg × 5"));

  // Delete (unused)
  await b.goto("/fitness/exercises");
  await b.clickText("button", "New custom exercise");
  await b.type(`document.querySelector('input[name=name]')`, "UI Unused Exercise", "name");
  await b.clickText("button", "Create exercise");
  await b.waitFor(`/^\\/fitness\\/exercises\\/[0-9a-f-]{36}$/.test(location.pathname) && document.body.innerText.includes('UI Unused Exercise')`, "detail");
  const unusedId = (await b.url()).split("/").pop();
  await b.clickText("button", "Remove exercise");
  await b.waitFor(`!!${dialog}`, "dialog");
  await b.click(`__h.find('button','Remove exercise', ${dialog})`, "confirm");
  await b.waitFor(`location.pathname === '/fitness/exercises'`, "back on list");
  check("an unused exercise is deleted outright (404 afterwards)", (await api.get(`/api/v1/exercises/${unusedId}/records`)).status === 404);
});

// ---------------------------------------------------------------------------------------------
await section("History list, pagination, detail, and deleting a completed workout", async () => {
  for (let i = 1; i <= 22; i++) await completedViaApi(`Bulk ${String(i).padStart(2, "0")}`, "Deadlift", [[100, 5]]);
  await b.goto("/fitness/workouts");
  await b.waitFor(hasText("Page 1 of"), "history page 1");
  const total = (await api.get("/api/v1/workouts?size=1")).body.totalItems;
  const pages = Math.ceil(total / 20);
  check(`history is paged (${total} workouts -> ${pages} pages, 20 per page)`, (await b.text()).includes(`Page 1 of ${pages}`));
  const firstPageCards = (await b.eval(`document.querySelectorAll('a[href^="/fitness/workouts/"]').length`));
  check("first page lists 20 workouts", firstPageCards === 20, String(firstPageCards));
  await b.clickText("a", "Next");
  await b.waitFor(`location.search.includes('page=1')`, "next page");
  check("Next moves to page 2", (await b.text()).includes(`Page 2 of ${pages}`));
  await b.clickText("a", "Previous");
  await b.waitFor(`!location.search.includes('page=1')`, "previous");
  check("Previous returns to page 1", (await b.text()).includes(`Page 1 of ${pages}`));
  await b.goto("/fitness/workouts?page=99");
  await b.waitFor(hasText("No workouts on this page"), "out-of-range page");
  check("an out-of-range page shows a helpful empty state", (await b.text()).includes("Back to the first page"));

  await b.goto("/fitness/workouts");
  await b.waitFor(hasText("Bulk 22"), "list");
  await b.click(`__h.findContains('a', 'Bulk 22')`, "open a workout from the list");
  await b.waitFor(workoutUrl + ` && document.body.innerText.includes('Completed')`, "detail");
  check("a history card opens the workout summary", (await b.text()).includes("Bulk 22"));
  const id = (await b.url()).split("/").pop();
  await b.clickText("button", "Delete workout");
  await b.waitFor(`!!${dialog}`, "delete dialog");
  check("delete asks for confirmation and mentions recalculated records", (await b.eval(`${dialog}.innerText`)).includes("recalculated"));
  await b.click(`__h.find('button','Keep it', ${dialog})`, "Keep it");
  await b.waitFor(`!${dialog}`, "closed");
  check("'Keep it' leaves the workout in place", (await api.get(`/api/v1/workouts/${id}`)).status === 200);
  await b.clickText("button", "Delete workout");
  await b.waitFor(`!!${dialog}`, "dialog");
  await b.click(`__h.find('button','Delete workout', ${dialog})`, "confirm delete");
  await b.waitFor(`location.pathname === '/fitness/workouts'`, "back to history");
  check("deleting returns to history", true);
  check("API: the deleted workout is gone (404)", (await api.get(`/api/v1/workouts/${id}`)).status === 404);
  await b.waitFor(`!document.body.innerText.includes('Bulk 22')`, "removed from list");
  check("it no longer appears in the history list", true);
});

// ---------------------------------------------------------------------------------------------
await section("Desktop (1280px) full flow and tablet (768px) picker", async () => {
  await b.viewport(1280);
  await b.goto("/fitness/templates");
  await b.waitFor(hasText("Built-in templates"), "templates");
  await b.click(`__h.find('button', 'Start', ${cardBySpan("Pull")})`, "Start Pull");
  await b.waitFor(workoutUrl + ` && document.body.innerText.includes('Finish workout')`, "pull workout");
  check("desktop: Pull starts with 7 exercises", (await titles()).length === 7);
  check("desktop: sidebar and top bar are visible alongside the logger", await b.has(`__h.visible(document.querySelector('aside')) && __h.visible(document.querySelector('header'))`));
  const layout = await b.eval(`(() => { const main = document.querySelector('main').getBoundingClientRect(); const c = __h.section('Pull-Up').getBoundingClientRect();
    const bar = __h.find('button','Finish workout').closest('.sticky').getBoundingClientRect(); return { mainL: main.left, mainR: main.right, cardL: c.left, cardR: c.right, cardW: c.width, barL: bar.left, barR: bar.right, barBottom: bar.bottom, ih: innerHeight }; })()`);
  check("desktop: the logging column is a readable width (<=700px) and centred in the content area", layout.cardW <= 700 && Math.abs((layout.cardL - layout.mainL) - (layout.mainR - layout.cardR)) <= 2, J(layout));
  check("desktop: the Finish bar lines up with the cards and is docked at the bottom", Math.abs(layout.barL - layout.cardL) <= 2 && Math.abs(layout.barR - layout.cardR) <= 2 && Math.abs(layout.barBottom - layout.ih) <= 1, J(layout));
  const firstCard = "Pull-Up";
  const c = `__h.section(${J(firstCard)})`;
  await b.type(`${c}.querySelector('input[id^="draft-"][id$="-weight"]')`, "0", "weight");
  await b.type(`${c}.querySelector('input[id^="draft-"][id$="-reps"]')`, "8", "reps");
  await b.click(`__h.find('button','Log set', ${c})`, "Log set");
  await b.waitFor(`__h.hasSet(${J(firstCard)}, 0, 8)`, "bodyweight set logged");
  check("desktop: a 0 kg bodyweight set (Pull-Up) can be logged", (await current()).body.exercises[0].sets[0].weightKg === 0);
  await b.shot("desktop-logger-1280");
  const o = await b.overflow();
  check("desktop: no horizontal overflow while logging", !o.overflow && o.offenders.length === 0, J(o));
  await b.clickText("button", "Finish workout");
  await b.waitFor(`!!${dialog}`, "dialog");
  await b.click(`__h.find('button','Finish workout', ${dialog})`, "confirm");
  await b.waitFor(hasText("Completed") + ` && !document.body.innerText.includes('Log set')`, "summary", 15000);
  check("desktop: finishing shows the read-only summary with only the exercise that had sets (empty ones dropped)", (await b.text()).includes("Pull-Up") && !(await b.text()).includes("Barbell Row") && !(await b.text()).includes("Lat Pulldown"));
  await b.shot("desktop-summary-1280");

  // Tablet: the picker is a centred sheet, not full width.
  await b.viewport(768);
  await b.goto("/fitness/start");
  await b.waitFor(hasText("Empty workout"), "start");
  await b.clickText("button", "Empty workout");
  await b.waitFor(workoutUrl, "workout");
  await b.clickText("button", "Add exercise");
  await b.waitFor(`!!${dialog}`, "sheet");
  await sleep(500);
  const g = await b.eval(`(() => { const r = document.querySelector('[role=dialog]').getBoundingClientRect(); return { l: r.left, w: r.width, iw: innerWidth }; })()`);
  check("tablet: the picker sheet is centred and narrower than the screen", g.w < g.iw && Math.abs(g.l - (g.iw - g.w - g.l)) <= 2, J(g));
  await b.key("Escape", "Escape", 27);
  await api.del(`/api/v1/workouts/${(await current()).body.id}`);
});

check("no JavaScript exceptions during all flows", b.consoleErrors.length === 0, b.consoleErrors.slice(0, 3).join(" | "));
b.close();
process.exit(summary() ? 1 : 0);
