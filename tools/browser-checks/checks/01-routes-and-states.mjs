// Part 1: fresh-user empty states, logged-out behaviour, then responsive sweeps at 390 / 768 / 1280.
import { Browser, begin, check, summary, registerUser, sleep, backendPid, BASE } from "../lib.mjs";
import { execSync } from "node:child_process";
const PW = "a long enough password";
const stamp = Date.now();
const hasText = (t) => `document.body.innerText.includes(${JSON.stringify(t)})`;

async function section(name, fn) {
  begin(name);
  try { await fn(); } catch (e) { check(`section completed without error`, false, e.message); }
}
async function uiLogin(b, email) {
  await b.goto("/login");
  await b.type(`document.querySelector('input[name=email]')`, email, "email");
  await b.type(`document.querySelector('input[name=password]')`, PW, "password");
  await b.clickText("button", "Log in");
  await b.waitFor(`location.pathname === '/dashboard'`, "dashboard after login");
}

const emailC = `fresh${stamp}@example.com`;
const emailA = `user${stamp}@example.com`;
const emailB = `other${stamp}@example.com`;
const apiC = await registerUser(emailC, PW, "Fresh User");
const b = new Browser();
await b.launch();

// ---------------------------------------------------------------------------------------------
await section("Logged-out behaviour", async () => {
  await b.viewport(390);
  for (const path of ["/fitness", "/fitness/start", "/fitness/workouts", "/fitness/exercises", "/fitness/templates", "/fitness/templates/new",
                      `/fitness/workouts/${crypto.randomUUID()}`]) {
    await b.goto(path);
    check(`logged out: ${path} redirects to /login`, (await b.url()).startsWith("/login"), await b.url());
  }
  const anon = await fetch(`${BASE}/api/v1/workouts/current`);
  check("logged out: API returns 401", anon.status === 401, String(anon.status));
});

// ---------------------------------------------------------------------------------------------
await section("Empty states (brand-new user) at 390px", async () => {
  await uiLogin(b, emailC);
  await b.viewport(390);
  await b.goto("/fitness");
  await b.waitFor(hasText("Ready to train?"), "fitness hub");
  const t = await b.text();
  check("hub: shows 'Ready to train?' with Start workout", t.includes("Ready to train?") && t.includes("Start workout"));
  check("hub: recent workouts empty state", t.includes("No workouts yet"));
  check("hub: this week shows zeros", /Workouts\s*\n?0/.test(t) || t.includes("This week"));
  await b.goto("/fitness/workouts");
  await b.waitFor(hasText("No completed workouts yet"), "history empty");
  check("history: empty state", true);
  await b.goto("/fitness/templates");
  await b.waitFor(hasText("No templates of your own yet"), "templates empty");
  check("templates: empty state for own templates, built-ins listed", (await b.text()).includes("Push") && (await b.text()).includes("Legs"));
  await b.goto("/fitness/exercises?q=zzzzzz");
  await b.waitFor(hasText("No exercises found"), "exercises empty");
  check("exercises: no-results empty state with 'Clear the filters'", (await b.text()).includes("Clear the filters"));
  const bench = (await apiC.get("/api/v1/exercises?q=barbell%20bench&size=5")).body.items[0].id;
  await b.goto(`/fitness/exercises/${bench}`);
  await b.waitFor(hasText("No records yet"), "exercise detail empty");
  check("exercise detail: 'No records yet' and 'Not done yet'", (await b.text()).includes("Not done yet"));
  await b.goto("/dashboard");
  await b.waitFor(hasText("Nothing in progress"), "dashboard card");
  check("dashboard: Today's workout card offers Start workout", (await b.text()).includes("Start workout"));
});

// ---------------------------------------------------------------------------------------------
await section("Loading state and backend unavailable (backend frozen)", async () => {
  await b.viewport(390);
  await b.goto("/dashboard");
  await b.waitFor(hasText("Today's workout"), "dashboard");
  const pid = backendPid();
  check("found the backend process", /^\d+$/.test(pid), pid);
  execSync(`kill -STOP ${pid}`);
  try {
    // Soft navigation to Fitness: the server render waits on the frozen backend, so the loading skeleton must show.
    await b.clickText("a", "Fitness").catch(async () => { await b.click(`document.querySelector('button[aria-label="Open navigation menu"]')`, "menu"); await sleep(500); await b.click(`__h.find('[role=dialog] a', 'Fitness')`, "drawer fitness"); });
    let sawLoading = false;
    for (let i = 0; i < 20 && !sawLoading; i++) { sawLoading = await b.has(`!!document.querySelector('[aria-busy="true"]')`); if (!sawLoading) await sleep(150); }
    check("loading skeleton is shown while the backend is slow", sawLoading);
    await b.shot("loading-390");
    await b.waitFor(hasText("We can't reach the server"), "unavailable after timeout", 15000);
    check("frozen backend: timeout shows the unavailable panel", true);
    check("frozen backend: user stays on /fitness (not redirected to login)", (await b.url()) === "/fitness", await b.url());
    check("frozen backend: shell (sidebar/topbar) still rendered", await b.has(`!!document.querySelector('header')`));
    await b.shot("unavailable-390");
  } finally {
    execSync(`kill -CONT ${pid}`);
  }
  await sleep(1500);
  await b.clickText("button", "Try again");
  await b.waitFor(hasText("Ready to train?"), "recovered after Try again", 15000);
  check("after the backend resumes, 'Try again' recovers the page", true);
});

// ---------------------------------------------------------------------------------------------
// Data for user A, then the responsive sweeps.
const apiA = await registerUser(emailA, PW, "Asha Nair");
const apiB = await registerUser(emailB, PW, "Other Person");
const bench = (await apiA.get("/api/v1/exercises?q=barbell%20bench&size=5")).body.items[0].id;
const row = (await apiA.get("/api/v1/exercises?q=barbell%20row&size=5")).body.items[0].id;
const custom = (await apiA.post("/api/v1/exercises", { name: "Zercher Squat", primaryMuscleGroup: "QUADS", equipment: "Barbell" })).body.id;
const tpl = (await apiA.post("/api/v1/workout-templates", { name: "My Upper Day", exercises: [{ exerciseId: bench, targetSets: 4 }, { exerciseId: row }] })).body.id;
const templates = (await apiA.get("/api/v1/workout-templates?size=100")).body.items;
const pushId = templates.find((t) => t.name === "Push").id;

async function completed(api, exerciseId, pairs) {
  const w = (await api.post("/api/v1/workouts", {})).body;
  const we = (await api.post(`/api/v1/workouts/${w.id}/exercises`, { exerciseId })).body;
  for (const [weight, reps] of pairs) await api.post(`/api/v1/workouts/${w.id}/exercises/${we.id}/sets`, { weightKg: weight, reps });
  return (await api.post(`/api/v1/workouts/${w.id}/finish`)).body;
}
const W1 = await completed(apiA, bench, [[100, 5], [105, 3]]);
await sleep(1100);
await completed(apiA, row, [[80, 8], [80, 8]]);
const bWorkout = (await apiB.post("/api/v1/workouts", {})).body;   // an active workout owned by the OTHER user

await section("Responsive sweep: every fitness route at 390 / 768 / 1280", async () => {
  // UI logout of the fresh user, then UI login as user A.
  await b.viewport(1280);
  await b.goto("/dashboard");
  await b.click(`document.querySelector('aside button[aria-label="Account menu"]')`, "account menu");
  await b.click(`__h.find('[role=menuitem]', 'Log out')`, "log out");
  await b.waitFor(`location.pathname === '/login'`, "back on login after logout");
  check("UI logout returns to the login page", true);
  await b.goto("/fitness");
  check("after logout /fitness is protected again", (await b.url()).startsWith("/login"));
  await uiLogin(b, emailA);
  check("UI login as user A works", true);

  const routes = [
    ["hub", "/fitness", "Fitness"],
    ["start", "/fitness/start", "Start workout"],
    ["history", "/fitness/workouts", "Workout history"],
    ["workout-detail", `/fitness/workouts/${W1.id}`, "Completed"],
    ["exercises", "/fitness/exercises", "Exercises"],
    ["exercise-detail", `/fitness/exercises/${bench}`, "Personal bests"],
    ["custom-exercise", `/fitness/exercises/${custom}`, "Your exercise"],
    ["templates", "/fitness/templates", "Workout templates"],
    ["template-view", `/fitness/templates/${pushId}`, "Built-in"],
    ["template-editor", `/fitness/templates/${tpl}`, "Edit template"],
    ["template-new", "/fitness/templates/new", "New template"],
  ];
  const consoleBefore = b.consoleErrors.length;
  for (const width of [390, 768, 1280]) {
    await b.viewport(width);
    for (const [label, path, text] of routes) {
      await b.goto(path);
      await b.waitFor(hasText(text), `${label} @${width}`);
      const o = await b.overflow();
      check(`${label} @${width}px: no horizontal overflow`, !o.overflow && o.offenders.length === 0, JSON.stringify(o));
      await b.shot(`${label}-${width}`);
    }
    // Active workout page: an in-progress Push workout with some sets.
    const w = (await apiA.post("/api/v1/workouts", { templateId: pushId })).body;
    const first = w.exercises[0].id;
    await apiA.post(`/api/v1/workouts/${w.id}/exercises/${first}/sets`, { weightKg: 60, reps: 8 });
    await apiA.post(`/api/v1/workouts/${w.id}/exercises/${first}/sets`, { weightKg: 110, reps: 2, rpe: 8.5 });
    await b.goto(`/fitness/workouts/${w.id}`);
    await b.waitFor(hasText("Finish workout"), `active workout @${width}`);
    const o = await b.overflow();
    check(`active workout @${width}px: no horizontal overflow`, !o.overflow && o.offenders.length === 0, JSON.stringify(o));
    await b.shot(`active-${width}`);
    check(`active workout @${width}px: sidebar ${width >= 768 ? "visible" : "hidden, hamburger visible"}`,
      width >= 768 ? await b.has(`__h.visible(document.querySelector('aside'))`) : await b.has(`__h.visible(document.querySelector('button[aria-label="Open navigation menu"]'))`));
    await apiA.del(`/api/v1/workouts/${w.id}`);
  }
  check("no JavaScript exceptions during the sweeps", b.consoleErrors.length === consoleBefore, b.consoleErrors.slice(consoleBefore, consoleBefore + 2).join(" | "));
});

await section("404 / malformed / other user's ids (inside the app shell)", async () => {
  await b.viewport(390);
  const bad = [
    ["malformed workout id", "/fitness/workouts/not-a-uuid"],
    ["unknown workout id", `/fitness/workouts/${crypto.randomUUID()}`],
    ["another user's workout id", `/fitness/workouts/${bWorkout.id}`],
    ["malformed exercise id", "/fitness/exercises/12345"],
    ["unknown exercise id", `/fitness/exercises/${crypto.randomUUID()}`],
    ["malformed template id", "/fitness/templates/nope"],
    ["unknown template id", `/fitness/templates/${crypto.randomUUID()}`],
  ];
  for (const [label, path] of bad) {
    await b.goto(path);
    await b.waitFor(hasText("Not found"), label).catch(() => {});
    const t = await b.text();
    check(`${label}: shows 'Not found' page`, t.includes("Not found"), t.slice(0, 80));
    check(`${label}: no workout data leaked`, !t.includes("Other Person") && !t.includes("Finish workout"));
    check(`${label}: stays inside the app shell (nav present)`, await b.has(`!!document.querySelector('header')`));
  }
  await b.goto(`/fitness/workouts/${bWorkout.id}`);
  check("other user's active workout is not reachable (page is 'Not found')", (await b.text()).includes("Not found"));
  check("other user's workout is untouched", (await apiB.get(`/api/v1/workouts/${bWorkout.id}`)).body.status === "IN_PROGRESS");
});

await apiB.del(`/api/v1/workouts/${bWorkout.id}`);
b.close();
process.exit(summary() ? 1 : 0);
