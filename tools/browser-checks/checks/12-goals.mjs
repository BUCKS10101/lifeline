// Part 12: the Goals list, the goal detail page (progress, linked tasks, linked habits, status transitions, delete),
// linking a goal from the task and habit edit/create forms, and the dashboard's Goals card, driven like a user and
// confirmed against the API. Progress must come from linked tasks only: habits never move it.
import { execSync } from "node:child_process";
import { Browser, begin, check, summary, registerUser, sleep, backendPid } from "../lib.mjs";
const J = JSON.stringify; const PW = "a long enough password"; const TZ = "Asia/Kolkata";
const email = `goals${Date.now()}@example.com`;
const api = await registerUser(email, PW, "Asha Nair", TZ);

const hasText = (t) => `document.body.innerText.includes(${J(t)})`;
const until = async (fn, label, ms = 10000) => { const t = Date.now(); while (Date.now() - t < ms) { if (await fn()) return true; await sleep(200); } throw new Error("timeout: " + label); };
const sheet = `document.querySelector('[data-slot=sheet-content]')`;
const field = (name) => `${sheet}.querySelector('[name=${name}]')`;
const setValue = (sel, value, proto = "HTMLInputElement") => b.eval(`(() => { const el = ${sel}; Object.getOwnPropertyDescriptor(window.${proto}.prototype, 'value').set.call(el, ${J(value)}); el.dispatchEvent(new Event('input', { bubbles: true })); })()`);
const apiList = async (status = "active") => (await api.get(`/api/v1/goals?status=${status}`)).body;
const apiOne = async (id) => (await api.get(`/api/v1/goals/${id}`)).body;
const goalRowByTitle = (title) => `[...document.querySelectorAll('[data-goal-row]')].find((r) => r.innerText.includes(${J(title)}))`;
const goalTaskRowByTitle = (title) => `[...document.querySelectorAll('[data-goal-task-row]')].find((r) => r.innerText.includes(${J(title)}))`;

const b = new Browser(); await b.launch();
try {
  await b.viewport(390);
  await b.goto("/login");
  await b.type(`document.querySelector('input[name=email]')`, email, "email"); await b.type(`document.querySelector('input[name=password]')`, PW, "password");
  await b.clickText("button", "Log in"); await b.waitFor(`location.pathname==='/dashboard'`, "dashboard");

  // ------------------------------------------------------------------------------------------------
  begin("1-3. Empty /goals, empty dashboard card, and navigation (a brand-new user)");
  await b.goto("/goals"); await b.waitFor(hasText("No goals yet"), "empty goals");
  check("no goal rows and an Add goal button", (await b.eval(`document.querySelectorAll('[data-goal-row]').length`)) === 0 && await b.has(`[...document.querySelectorAll('button')].some((x) => x.textContent.trim() === 'Add goal')`));
  let o0 = await b.overflow(); check("no horizontal overflow (empty goals, 390px)", !o0.overflow && o0.offenders.length === 0, J(o0));
  await b.shot("goals-empty-390");
  await b.goto("/dashboard"); await b.waitFor(`document.querySelector('[data-goals-card]')`, "goals card");
  check("the dashboard card with no goals says so, and the placeholder 'Soon' badge is gone", await b.has(`document.querySelector('[data-no-goals]')?.innerText.includes('No goals yet')`) && !(await b.has(`[...document.querySelectorAll('main [data-slot=card]')].some((c) => c.innerText.includes('Goals') && c.innerText.includes('Soon'))`)));
  check("the only remaining dashboard placeholders are Upcoming events and DSA progress", (await b.eval(`[...document.querySelectorAll('main [data-slot=card]')].filter((c) => c.innerText.includes('Soon')).map((c) => c.innerText.split('\\n')[0].trim())`)).sort().join() === ["Upcoming events", "DSA progress"].sort().join());
  await b.viewport(1280, 900); await b.goto("/dashboard"); await b.waitFor(`document.querySelector('a[href="/goals"]')`, "nav");
  check("Goals is a live link in the sidebar", true);
  check("Calendar and DSA are still not links", !(await b.has(`document.querySelector('a[href="/calendar"], a[href="/dsa"]')`)));
  await b.click(`document.querySelector('a[href="/goals"]')`, "sidebar Goals"); await b.waitFor(`location.pathname === '/goals'`, "goals page");
  check("the Goals link is marked current and the page title is set", await b.has(`document.querySelector('a[href="/goals"]').getAttribute('aria-current') === 'page'`) && (await b.eval("document.title")).includes("Goals"));

  // ------------------------------------------------------------------------------------------------
  begin("4-5. Creating a goal via the sheet, and it lists with no tasks linked yet");
  await b.viewport(390); await b.goto("/goals"); await b.waitFor(hasText("No goals yet"), "empty");
  await b.clickText("button", "Add goal"); await b.waitFor(sheet, "sheet"); await sleep(300);
  await b.type(field("title"), "Run a marathon", "title");
  await b.type(field("description"), "Sub-4 hours", "description");
  await setValue(field("targetDate"), "2027-04-01");
  await b.click(`__h.find('button', 'Add goal', ${sheet})`, "submit Add goal"); await b.waitFor(`!${sheet}`, "sheet closed");
  await until(async () => (await apiList()).some((g) => g.title === "Run a marathon"), "created");
  const marathon = (await apiList()).find((g) => g.title === "Run a marathon");
  check("the goal was created active with the typed fields and null progress (no tasks yet)", marathon.status === "ACTIVE" && marathon.description === "Sub-4 hours" && marathon.targetDate === "2027-04-01" && marathon.progressPercent === null, J(marathon));
  await b.waitFor(`document.querySelector('[data-goal-row]')`, "row appears");
  check("the list shows the goal with 'No tasks linked yet' and the target date", (await b.eval(`${goalRowByTitle("Run a marathon")}.innerText`)).includes("No tasks linked yet") && (await b.eval(`${goalRowByTitle("Run a marathon")}.innerText`)).includes("2027"));
  await b.shot("goals-list-390");

  // ------------------------------------------------------------------------------------------------
  begin("6-7. The goal detail page, and adding a task directly from it");
  await b.click(`${goalRowByTitle("Run a marathon")}.querySelector('a')`, "open goal"); await b.waitFor(`location.pathname === '/goals/${marathon.id}'`, "detail page");
  await b.waitFor(hasText("Manage this goal"), "detail content loaded");
  check("the header shows the title and description, and the target date is shown", await b.has(hasText("Run a marathon")) && await b.has(hasText("Sub-4 hours")) && await b.has(hasText("2027")));
  check("with no linked tasks, the tasks section says so", await b.has(hasText("No tasks linked yet")));
  check("with no linked habits, the habits section says so", await b.has(hasText("No habits linked yet")));
  await b.type(`document.querySelector('input[name=title]')`, "Book a 20k race", "quick add");
  await b.clickText("button", "Add"); await sleep(200);
  await until(async () => (await apiOne(marathon.id)).tasks.length === 1, "task added");
  let detail = await apiOne(marathon.id);
  check("the task was created already linked to this goal", detail.tasks[0].title === "Book a 20k race" && detail.goal.taskCount === 1 && detail.goal.doneCount === 0 && detail.goal.progressPercent === 0, J(detail.goal));
  await b.waitFor(`document.querySelector('[data-goal-task-row]')`, "task row appears");
  check("the task appears in the open list on the page", await b.has(`${goalTaskRowByTitle("Book a 20k race")}`));
  check("the progress line now reads 0 of 1 task done", await b.has(hasText("0 of 1 task done")));

  // ------------------------------------------------------------------------------------------------
  begin("8-9. Completing the linked task moves progress, and habits never do");
  await b.click(`${goalTaskRowByTitle("Book a 20k race")}.querySelector('[role=checkbox]')`, "complete task");
  await until(async () => (await apiOne(marathon.id)).goal.doneCount === 1, "completed");
  detail = await apiOne(marathon.id);
  check("progress is now 1 of 1, 100%, purely from the task", detail.goal.progressPercent === 100 && detail.goal.taskCount === 1 && detail.goal.doneCount === 1, J(detail.goal));
  await b.waitFor(hasText("1 of 1 task done"), "progress line updates"); check("the progress line on the page updates to match", true);

  // Link a habit to this goal at creation time; ticking it must never move the goal's own progress percentage.
  await b.goto("/habits"); await b.waitFor(`document.querySelector('button')`, "habits page");
  await b.clickText("button", "Add habit"); await b.waitFor(sheet, "sheet"); await sleep(300);
  await b.type(field("name"), "Run daily", "name");
  await b.waitFor(`${sheet}.querySelector('select[name=goalId] option[value="${marathon.id}"]')`, "goal option loaded");
  await b.select(`${sheet}.querySelector('select[name=goalId]')`, marathon.id, "goal picker");
  await b.click(`__h.find('button', 'Add habit', ${sheet})`, "submit Add habit"); await b.waitFor(`!${sheet}`, "sheet closed");
  await until(async () => (await apiOne(marathon.id)).habits.length === 1, "habit linked");
  const habits = (await api.get("/api/v1/habits")).body;
  const runDaily = habits.find((h) => h.name === "Run daily");
  check("the habit was created already linked to the goal", runDaily.goal?.id === marathon.id);
  const beforeHabitTick = (await apiOne(marathon.id)).goal.progressPercent;
  const t2 = new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
  await api.put(`/api/v1/habits/${runDaily.id}/completions/${t2}`, {});
  detail = await apiOne(marathon.id);
  check("ticking the linked habit never changes the goal's progress percentage", detail.goal.progressPercent === beforeHabitTick && detail.goal.taskCount === 1, J(detail.goal));
  const runDailyNow = (await api.get("/api/v1/habits")).body.find((h) => h.id === runDaily.id);
  await b.goto(`/goals/${marathon.id}`); await b.waitFor(hasText("Supporting habits"), "detail page");
  check("the goal page lists the linked habit with its real streak from the API", await b.has(hasText("Run daily")) && await b.has(hasText(`${runDailyNow.currentStreak} day`)), J(runDailyNow));
  await b.shot("goal-detail-1280-linked");

  // ------------------------------------------------------------------------------------------------
  begin("10-11. Linking an existing task from the task edit sheet's goal picker, and unlinking it");
  await api.post("/api/v1/tasks", { title: "Buy running shoes" });
  await b.goto("/tasks?view=anytime"); await b.waitFor(`document.querySelector('[data-task-row]')`, "tasks list");
  await b.click(`[...document.querySelectorAll('[data-task-row]')].find((r) => r.innerText.includes('Buy running shoes')).querySelector('button[aria-label^="Edit task"]')`, "edit task");
  await b.waitFor(sheet, "sheet"); await sleep(300);
  check("the task edit sheet has a goal picker defaulting to No goal", (await b.eval(`${sheet}.querySelector('select[name=goalId]').value`)) === "");
  await b.waitFor(`${sheet}.querySelector('select[name=goalId] option[value="${marathon.id}"]')`, "goal option loaded");
  await b.select(`${sheet}.querySelector('select[name=goalId]')`, marathon.id, "link to goal");
  await b.clickText("button", "Save"); await b.waitFor(`!${sheet}`, "sheet closed");
  const shoesTask = (await api.get("/api/v1/tasks?view=anytime&size=100")).body.items.find((t) => t.title === "Buy running shoes");
  check("the task is now linked to the goal via the API", shoesTask.goal?.id === marathon.id);
  await until(async () => (await apiOne(marathon.id)).goal.taskCount === 2, "goal sees the newly linked task");
  detail = await apiOne(marathon.id);
  check("the goal's counts now include the newly linked task (2 tasks, 1 done, 50%)", detail.goal.taskCount === 2 && detail.goal.doneCount === 1 && detail.goal.progressPercent === 50, J(detail.goal));
  await b.waitFor(`document.querySelector('[data-task-row] [data-due], [data-task-row]')`, "row"); // settle
  check("the tasks page itself now shows the goal's name on the row", await b.has(`[...document.querySelectorAll('[data-task-row]')].find((r) => r.innerText.includes('Buy running shoes'))?.innerText.includes('Run a marathon')`));
  // Unlink it again via an explicit clear, and confirm the goal's count drops back.
  await b.click(`[...document.querySelectorAll('[data-task-row]')].find((r) => r.innerText.includes('Buy running shoes')).querySelector('button[aria-label^="Edit task"]')`, "edit task again");
  await b.waitFor(sheet, "sheet"); await sleep(300);
  await b.select(`${sheet}.querySelector('select[name=goalId]')`, "", "clear goal");
  await b.clickText("button", "Save"); await b.waitFor(`!${sheet}`, "sheet closed");
  await until(async () => (await apiOne(marathon.id)).goal.taskCount === 1, "unlinked");
  check("clearing the goal picker unlinks the task and the goal's count drops back to 1", true);

  // ------------------------------------------------------------------------------------------------
  begin("12-13. Editing the goal's own fields");
  await b.goto(`/goals/${marathon.id}`); await b.waitFor(hasText("Manage this goal"), "manage form");
  await setValue(`document.querySelector('form[aria-label="Edit goal"] input[name=title]')`, "Run a fast marathon");
  await b.clickText("button", "Save changes");
  await until(async () => (await apiOne(marathon.id)).goal.title === "Run a fast marathon", "renamed");
  check("the title was saved and other fields were left alone", (await apiOne(marathon.id)).goal.description === "Sub-4 hours");
  await b.waitFor(hasText("Saved."), "saved notice");
  check("a success notice is shown", true);

  // ------------------------------------------------------------------------------------------------
  begin("14-16. Status transitions: achieve, reopen, archive, restore");
  await b.clickText("button", "Mark achieved");
  await until(async () => (await apiOne(marathon.id)).goal.status === "ACHIEVED", "achieved");
  check("achieving records achievedAt and the badge shows Achieved", (await apiOne(marathon.id)).goal.achievedAt !== null && await b.has(hasText("Achieved")));
  // Achieving this goal empties the active list (it is the only goal created so far), so the empty state shows, not a row.
  await b.goto("/goals"); await b.waitFor(hasText("No goals yet"), "empty active list");
  check("an achieved goal leaves the default active list", !(await b.eval(`document.body.innerText.includes('Run a fast marathon')`)));
  await b.clickText("a", "Show achieved and archived goals"); await b.waitFor(`location.search.includes('done=1')`, "shown");
  check("it appears under Achieved once shown", await b.has(hasText("Achieved")) && await b.has(`${goalRowByTitle("Run a fast marathon")}`));
  await b.goto(`/goals/${marathon.id}`); await b.waitFor(hasText("Reopen goal"), "detail page");
  await b.clickText("button", "Reopen goal");
  await until(async () => (await apiOne(marathon.id)).goal.status === "ACTIVE", "reopened");
  check("reopening clears achievedAt and makes it active again", (await apiOne(marathon.id)).goal.achievedAt === null);
  await b.clickText("button", "Archive goal");
  await until(async () => (await apiOne(marathon.id)).goal.status === "ARCHIVED", "archived");
  check("archiving hides it from the active list but keeps it reachable", !(await apiList("active")).some((g) => g.id === marathon.id) && (await apiList("archived")).some((g) => g.id === marathon.id));
  await b.waitFor(hasText("Restore goal"), "button label flips");
  await b.clickText("button", "Restore goal");
  await until(async () => (await apiOne(marathon.id)).goal.status === "ACTIVE", "restored");
  check("restoring brings it back to active", (await apiList("active")).some((g) => g.id === marathon.id));

  // ------------------------------------------------------------------------------------------------
  begin("17-18. Delete unlinks its tasks and habits without deleting them, and requires confirmation");
  const remainingTask = (await apiOne(marathon.id)).tasks[0];
  const linkedHabitId = runDaily.id;
  await b.goto(`/goals/${marathon.id}`); await b.waitFor(hasText("Manage this goal"), "manage form");
  const delBtn = () => b.click(`[...document.querySelectorAll('button')].find((x) => x.textContent.trim() === 'Delete goal' || x.textContent.trim() === 'Delete')`, "Delete goal");
  await delBtn();
  check("the first tap only arms the confirmation (the goal still exists)", (await api.get(`/api/v1/goals/${marathon.id}`)).status === 200);
  await delBtn();
  await until(async () => (await api.get(`/api/v1/goals/${marathon.id}`)).status === 404, "deleted");
  await b.waitFor(`location.pathname === '/goals'`, "redirected");
  check("the second tap deletes it and returns to /goals", true);
  // "Book a 20k race" was completed earlier in this run, so it now lives in the done view, not anytime.
  const stillTask = (await api.get("/api/v1/tasks?view=done&size=100")).body.items.find((t) => t.id === remainingTask.id);
  check("its linked task still exists, just unlinked from the deleted goal", !!stillTask && stillTask.goal === null);
  const stillHabit = (await api.get("/api/v1/habits")).body.find((h) => h.id === linkedHabitId);
  check("its linked habit still exists, just unlinked from the deleted goal", !!stillHabit && stillHabit.goal === null);

  // ------------------------------------------------------------------------------------------------
  begin("19. A deleted/unknown goal is a clean not-found page");
  await b.goto(`/goals/${marathon.id}`); await b.waitFor(hasText("Not found"), "not found", 15000);
  check("a deleted goal's detail page shows Not found with a way back", await b.has(`document.querySelector('a[href="/goals"]')`));
  const oNF = await b.overflow(); check("no overflow on the not-found page", !oNF.overflow, J(oNF));
  await b.goto(`/goals/00000000-0000-0000-0000-000000000000`); await b.waitFor(hasText("Not found"), "unknown id");
  check("an unknown id is also Not found (never a crash)", true);

  // ------------------------------------------------------------------------------------------------
  begin("20-22. Responsive: overflow and touch targets at 390, 768 and 1280 px, on a fresh goal with linked work");
  const survivorId = (await api.post("/api/v1/goals", { title: "Read more" })).body.id;
  await api.post("/api/v1/tasks", { title: "Finish a book", goalId: survivorId });
  await api.post("/api/v1/habits", { name: "Read", daysOfWeek: [1, 2, 3, 4, 5, 6, 7], goalId: survivorId });
  for (const w of [390, 768, 1280]) {
    await b.viewport(w, 900);
    await b.goto("/goals"); await b.waitFor(`document.querySelector('[data-goal-row]')`, "list"); await sleep(300);
    let o = await b.overflow(); check(`${w}px goals list: no horizontal overflow`, !o.overflow && o.offenders.length === 0, J(o));
    let small = await b.eval(`[...document.querySelectorAll('main a, main button')].filter((e) => __h.visible(e) && e.getBoundingClientRect().height < 40).map((e) => (e.getAttribute('aria-label') || e.textContent).trim().slice(0, 30))`);
    check(`${w}px goals list: every control is at least 40px tall`, small.length === 0, J(small));
    await b.goto(`/goals/${survivorId}`); await b.waitFor(hasText("Manage this goal"), "detail"); await sleep(300);
    o = await b.overflow(); check(`${w}px goal detail: no horizontal overflow`, !o.overflow && o.offenders.length === 0, J(o));
    small = await b.eval(`[...document.querySelectorAll('main a, main button')].filter((e) => __h.visible(e) && e.getBoundingClientRect().height < 40).map((e) => (e.getAttribute('aria-label') || e.textContent).trim().slice(0, 30))`);
    check(`${w}px goal detail: every control is at least 40px tall`, small.length === 0, J(small));
    if (w !== 768) await b.shot(`goal-detail-${w}`);
  }

  // ------------------------------------------------------------------------------------------------
  begin("23. Keyboard focus is visible (real Tab-key navigation, not .focus())");
  await b.viewport(1280, 900); await b.goto(`/goals/${survivorId}`); await b.waitFor(hasText("Manage this goal"), "detail");
  await b.click(`document.querySelector('h1')`, "click somewhere neutral to seed a starting point");
  const focusRing = async () => b.eval(`(() => { const e = document.activeElement; if (!e || e === document.body) return null; const s = getComputedStyle(e); return { tag: e.tagName, boxShadow: s.boxShadow, outline: s.outlineStyle }; })()`);
  let visible = false; let last = null;
  for (let i = 0; i < 40 && !visible; i++) {
    await b.key("Tab", "Tab", 9);
    last = await focusRing();
    if (last && ((last.boxShadow && last.boxShadow !== "none") || last.outline !== "none")) visible = true;
  }
  check("real Tab-key navigation reaches a control with a visible focus ring somewhere on the goal detail page", visible, J(last));
  // Same check on the goal list page, where the goal title itself is the tabbable, clickable link.
  await b.goto("/goals"); await b.waitFor(`document.querySelector('[data-goal-row]')`, "list");
  await b.click(`document.querySelector('h1')`, "click somewhere neutral");
  visible = false; last = null;
  for (let i = 0; i < 40 && !visible; i++) {
    await b.key("Tab", "Tab", 9);
    last = await focusRing();
    if (last && ((last.boxShadow && last.boxShadow !== "none") || last.outline !== "none")) visible = true;
  }
  check("real Tab-key navigation also reaches a visible focus ring on the goals list page", visible, J(last));

  // ------------------------------------------------------------------------------------------------
  begin("24-25. Dashboard Goals card: renders, matches the API, and stays read-only");
  await b.goto("/dashboard"); await b.waitFor(`document.querySelector('[data-goals-card]')`, "card");
  const activeGoals = await apiList("active");
  const shown = activeGoals.slice(0, 3);
  const titlesOnCard = await b.eval(`[...document.querySelectorAll('[data-goals-card] [data-goal-title]')].map((e) => e.innerText.split('\\n')[0].trim())`);
  check("the card lists the first active goals by title, matching the API", J(titlesOnCard) === J(shown.map((g) => g.title)), J({ titlesOnCard, want: shown.map((g) => g.title) }));
  check("it is read-only: no buttons or inputs beyond the View goals link", (await b.eval(`document.querySelectorAll('[data-goals-card] input').length`)) === 0 && (await b.eval(`document.querySelectorAll('[data-goals-card] [role=checkbox]').length`)) === 0 && await b.has(`document.querySelector('[data-goals-card] a[href="/goals"]')`));
  await b.shot("dashboard-goals-1280");
  await b.click(`document.querySelector('[data-goals-card] a[href="/goals"]')`, "View goals"); await b.waitFor(`location.pathname === '/goals'`, "goals page");
  check("View goals opens /goals", true);

  // ------------------------------------------------------------------------------------------------
  begin("26. Loading skeleton, then the backend unavailable and recovering");
  await b.goto("/dashboard"); await b.waitFor(hasText("Today's workout"), "dashboard");
  const pid = backendPid();
  execSync(`kill -STOP ${pid}`);
  try {
    await b.click(`document.querySelector('a[href="/goals"]')`, "Goals nav");
    let sawLoading = false;
    for (let i = 0; i < 20 && !sawLoading; i++) { sawLoading = await b.has(`!!document.querySelector('[aria-busy="true"]')`); if (!sawLoading) await sleep(150); }
    check("the loading skeleton shows while the backend is slow", sawLoading);
    await b.waitFor(hasText("We can't reach the server"), "unavailable panel", 15000);
    check("a frozen backend shows the unavailable panel and the user stays on /goals", (await b.url()) === "/goals");
    await b.shot("goals-unavailable-1280");
  } finally { execSync(`kill -CONT ${pid}`); }
  await sleep(1500); await b.clickText("button", "Try again");
  await b.waitFor(`document.querySelector('[data-goal-row]')`, "recovered", 15000);
  check("Try again brings the page back with real data", true);
} catch (e) { check("section completed without error", false, e.message); await b.shot("FAILED-run12-goals"); }
b.close();
summary();
