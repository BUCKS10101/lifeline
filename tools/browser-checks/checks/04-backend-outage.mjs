// Part 4: the backend really goes away mid-workout, then comes back.
import { Browser, begin, check, summary, registerUser, sleep, stopBackend, startBackend, backendHealthy as healthy } from "../lib.mjs";
const PW = "a long enough password";
const J = JSON.stringify;
const email = `down${Date.now()}@example.com`;
const api = await registerUser(email, PW, "Asha Nair");
const bench = (await api.get("/api/v1/exercises?q=barbell%20bench&size=5")).body.items[0].id;
const w = (await api.post("/api/v1/workouts", {})).body;
await api.post(`/api/v1/workouts/${w.id}/exercises`, { exerciseId: bench });
const hasText = (t) => `document.body.innerText.includes(${J(t)})`;

const b = new Browser();
await b.launch();
await b.viewport(390);
begin("Backend goes away mid-workout");
try {
  await b.goto("/login");
  await b.type(`document.querySelector('input[name=email]')`, email, "email");
  await b.type(`document.querySelector('input[name=password]')`, PW, "password");
  await b.clickText("button", "Log in");
  await b.waitFor(`location.pathname === '/dashboard'`, "dashboard");
  await b.goto(`/fitness/workouts/${w.id}`);
  await b.waitFor(hasText("Finish workout"), "logger");
  const card = `__h.section('Barbell Bench Press')`;
  await b.type(`(${card}).querySelector('input[id^="draft-"][id$="-weight"]')`, "60", "weight");
  await b.type(`(${card}).querySelector('input[id^="draft-"][id$="-reps"]')`, "8", "reps");

  await stopBackend();
  check("backend is down (health check fails)", !(await healthy()));
  await b.click(`__h.find('button','Log set', ${card})`, "Log set while backend is down");
  await b.waitFor(`!!document.querySelector('[role=alert]')`, "error notice", 15000);
  const alertText = await b.eval(`document.querySelector('[role=alert]').innerText`);
  check("logging while the backend is down shows an error (not silence)", alertText.length > 0, alertText);
  check("no set row was added optimistically", !(await b.has(`__h.hasSet("Barbell Bench Press", 60, 8)`)));
  check("the typed weight and reps are kept so nothing must be re-entered",
    (await b.eval(`(${card}).querySelector('input[id^="draft-"][id$="-weight"]').value`)) === "60" && (await b.eval(`(${card}).querySelector('input[id^="draft-"][id$="-reps"]').value`)) === "8");
  check("the Log set button is usable again for a retry", !(await b.eval(`__h.find('button','Log set', ${card}).disabled`)));
  await b.shot("backend-down-logger-390");

  // Bring it back and retry on the SAME page: the retry must not create a duplicate.
  const up = await startBackend();
  check("backend restarted and healthy", up);
  await b.click(`__h.find('button','Log set', ${card})`, "Log set retry");
  await b.waitFor(`__h.hasSet("Barbell Bench Press", 60, 8)`, "set appears after retry", 15000);
  check("the retry succeeds after the backend returns", true);
  await sleep(500);
  const sets = (await api.get(`/api/v1/workouts/${w.id}`)).body.exercises[0].sets;
  check("API: exactly one set exists (no duplicate from the failed + retried attempts)", sets.length === 1 && sets[0].weightKg === 60 && sets[0].reps === 8, J(sets));
  check("the error notice is cleared after a successful retry", !(await b.has(`document.querySelector('[role=alert]')`)));

  begin("Pages while the backend is down, then recovery");
  await stopBackend();
  for (const path of ["/fitness", "/fitness/workouts", "/fitness/exercises", "/fitness/templates", "/dashboard", `/fitness/workouts/${w.id}`]) {
    await b.goto(path);
    await b.waitFor(hasText("We can't reach the server"), `unavailable on ${path}`, 10000).catch(() => {});
    const t = await b.text();
    check(`${path}: shows the unavailable panel while the backend is down`, t.includes("We can't reach the server"), t.slice(0, 60));
    check(`${path}: stays on the page (no redirect to login)`, (await b.url()) === path, await b.url());
  }
  await b.shot("backend-down-hub-390");
  const up2 = await startBackend();
  check("backend restarted again", up2);
  await sleep(1500);
  await b.click(`__h.find('button','Try again')`, "Try again");
  await b.waitFor(hasText("Finish workout"), "workout page recovered", 15000);
  check("'Try again' recovers the workout page and the logged set is still there", await b.has(`__h.hasSet("Barbell Bench Press", 60, 8)`));
  check("session survived the restarts (still logged in)", (await b.url()).includes("/fitness/workouts/"));
} catch (e) {
  check("section completed without error", false, e.message);
  await b.shot("FAILED-run4");
}
if (!(await healthy())) await startBackend();
check("no JavaScript exceptions", b.consoleErrors.length === 0, b.consoleErrors.slice(0, 3).join(" | "));
b.close();
process.exit(summary() ? 1 : 0);
