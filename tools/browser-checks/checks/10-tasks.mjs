// Part 10: the Tasks page and the dashboard's Tasks-due card, driven like a user and confirmed against the API and against
// numbers worked out from a seed.
import { execSync } from "node:child_process";
import { Browser, begin, check, summary, registerUser, sleep, stopBackend, startBackend, backendHealthy, backendPid } from "../lib.mjs";
const J = JSON.stringify; const PW = "a long enough password"; const TZ = "Asia/Kolkata";
const email = `tasks${Date.now()}@example.com`;
const api = await registerUser(email, PW, "Asha Nair", TZ);

const addDays = (iso, n) => { const [y, m, d] = iso.split("-").map(Number); return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10); };
const today = new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const hasText = (t) => `document.body.innerText.includes(${J(t)})`;
const rows = () => b.eval(`[...document.querySelectorAll('[data-task-row]')].map((r) => ({ id: r.dataset.id, title: r.querySelector('button[aria-label^="Edit task"] > span').textContent.trim(), due: r.querySelector('[data-due]')?.innerText.replace(/\\s+/g, ' ').trim() ?? null, high: !!r.querySelector('[data-priority=high]'), overdue: r.dataset.overdue === 'true', done: r.querySelector('[role=checkbox]').getAttribute('aria-checked') === 'true' }))`);
const titlesNow = async () => (await rows()).map((r) => r.title);
const apiView = async (v, extra = "") => (await api.get(`/api/v1/tasks?view=${v}&size=100${extra}`)).body;
const apiTitles = async (v) => (await apiView(v)).items.map((t) => t.title);
const until = async (fn, label, ms = 10000) => { const t = Date.now(); while (Date.now() - t < ms) { if (await fn()) return true; await sleep(200); } throw new Error("timeout: " + label); };
const sheet = `document.querySelector('[data-slot=sheet-content]')`;
const field = (name) => `${sheet}.querySelector('[name=${name}]')`;
/** Headless Chrome cannot use the native date picker (and React reads inputs through the value setter), so values are set the way the browser does when a picker commits. */
const setValue = (sel, value, proto = "HTMLInputElement") => b.eval(`(() => { const el = ${sel}; Object.getOwnPropertyDescriptor(window.${proto}.prototype, 'value').set.call(el, ${J(value)}); el.dispatchEvent(new Event('input', { bubbles: true })); })()`);
const checkboxOf = (title) => `[...document.querySelectorAll('[data-task-row]')].find((r) => r.querySelector('button[aria-label^="Edit task"] > span').textContent.trim() === ${J(title)}).querySelector('[role=checkbox]')`;
const editOf = (title) => `[...document.querySelectorAll('[data-task-row]')].map((r) => r.querySelector('button[aria-label^="Edit task"]')).find((x) => x.querySelector('span').textContent.trim() === ${J(title)})`;
/** Waits until React has attached to the add form: before that, Enter would be a plain browser form submission that reloads the page. */
const waitHydrated = () => b.waitFor(`(() => { const f = document.querySelector('form[aria-label="Add a task"]'); return f && Object.keys(f).some((k) => k.startsWith('__reactProps')); })()`, "hydrated", 15000);
const openView = async (v) => { await b.goto(`/tasks?view=${v}`); await b.waitFor(`document.querySelector('form[aria-label="Add a task"]')`, "tasks page"); await waitHydrated(); await sleep(200); };
/** A real Enter key press: with text "\r", so Chrome submits the form the way it does for a person (the harness's key() sends no text). */
const pressEnter = async () => { await b.send("Input.dispatchKeyEvent", { type: "keyDown", key: "Enter", code: "Enter", windowsVirtualKeyCode: 13, text: "\r" }); await b.send("Input.dispatchKeyEvent", { type: "keyUp", key: "Enter", code: "Enter", windowsVirtualKeyCode: 13 }); await sleep(250); };
const addTask = async (title) => { await b.type(`document.querySelector('input[name=title]')`, title, "add"); await pressEnter(); };

const b = new Browser(); await b.launch();
try {
  await b.viewport(390);
  await b.goto("/login");
  await b.type(`document.querySelector('input[name=email]')`, email, "email"); await b.type(`document.querySelector('input[name=password]')`, PW, "password");
  await b.clickText("button", "Log in"); await b.waitFor(`location.pathname==='/dashboard'`, "dashboard");

  // ------------------------------------------------------------------------------------------------
  begin("Empty states: all four views and the dashboard card (a brand-new user, 390px)");
  for (const [v, msg] of [["today", "Nothing due today"], ["upcoming", "Nothing upcoming"], ["anytime", "No undated tasks"], ["done", "Nothing completed yet"]]) {
    await openView(v);
    check(`${v}: empty state "${msg}" and no rows`, await b.has(hasText(msg)) && (await b.eval(`document.querySelectorAll('[data-task-row]').length`)) === 0);
  }
  await b.goto("/dashboard"); await b.waitFor(`document.querySelector('[data-tasks-card]')`, "tasks card");
  check("the dashboard card with no tasks says so", await b.has(`document.querySelector('[data-nothing-due]')?.innerText.includes('Nothing due today. No open tasks.')`));
  check("the placeholder 'Soon' card is gone and the other dashboard placeholders are untouched", !(await b.has(`[...document.querySelectorAll('main [data-slot=card]')].some((c) => c.innerText.includes('Tasks due') && c.innerText.includes('Soon'))`)) && (await b.eval(`[...document.querySelectorAll('main [data-slot=card]')].filter((c) => c.innerText.includes('Soon')).map((c) => c.innerText.split('\\n')[0].trim())`)).sort().join() === ["Upcoming events", "DSA progress"].sort().join());

  // ------------------------------------------------------------------------------------------------
  begin("Navigation");
  await b.viewport(1280, 900); await b.goto("/dashboard"); await b.waitFor(`document.querySelector('a[href="/tasks"]')`, "nav");
  check("Tasks is a live link in the sidebar", true);
  check("Calendar and DSA are still not links (Goals is real as of Phase 6 checkpoint 5)", !(await b.has(`document.querySelector('a[href="/calendar"], a[href="/dsa"]')`)));
  await b.click(`document.querySelector('a[href="/tasks"]')`, "sidebar Tasks"); await b.waitFor(`location.pathname === '/tasks'`, "tasks page");
  check("the Tasks link is marked current on /tasks and the page title is set", await b.has(`document.querySelector('a[href="/tasks"]').getAttribute('aria-current') === 'page'`) && (await b.eval("document.title")).includes("Tasks"));
  await b.waitFor(`document.querySelector('nav[aria-label="Task views"]')`, "view links");
  check("the four view links are Today, Upcoming, Anytime and Done, and Today is current by default", J(await b.eval(`[...document.querySelectorAll('nav[aria-label="Task views"] a')].map((a) => a.textContent.trim())`)) === J(["Today", "Upcoming", "Anytime", "Done"]) && await b.has(`document.querySelector('nav[aria-label="Task views"] a[aria-current="true"]')?.textContent.trim() === 'Today'`));

  // ------------------------------------------------------------------------------------------------
  // Seed (today is the person's local date): three overdue (two on the 23rd), one due today, later, undated.
  const seed = [
    ["Overdue 22nd", addDays(today, -2), "NORMAL"], ["Overdue 23rd high", addDays(today, -1), "HIGH"], ["Overdue 23rd low", addDays(today, -1), "LOW"],
    ["Overdue 23rd normal", addDays(today, -1), "NORMAL"], ["Due today", today, "NORMAL"], ["Tomorrow", addDays(today, 1), "NORMAL"],
    ["Next week high", addDays(today, 7), "HIGH"], ["Next week", addDays(today, 7), "NORMAL"], ["No date", null, "NORMAL"], ["No date high", null, "HIGH"],
  ];
  for (const [title, dueDate, priority] of seed) await api.post("/api/v1/tasks", { title, ...(dueDate ? { dueDate } : {}), priority });
  await sleep(200);

  begin("The four views: contents and order equal the API and the seed (1280px)");
  await openView("today");
  const todayRows = await rows();
  check("Today holds the overdue tasks and the one due today, ordered by date, then priority", J(todayRows.map((r) => r.title)) === J(["Overdue 22nd", "Overdue 23rd high", "Overdue 23rd normal", "Overdue 23rd low", "Due today"]), J(todayRows.map((r) => r.title)));
  check("the order equals the API's", J(await titlesNow()) === J(await apiTitles("today")));
  check("overdue tasks say 'Overdue', the task due today says 'Today' and is not overdue", todayRows.slice(0, 4).every((r) => r.overdue && r.due.startsWith("Overdue")) && todayRows[4].due === "Today" && !todayRows[4].overdue, J(todayRows.map((r) => r.due)));
  check("only the High task carries a priority mark", J(todayRows.map((r) => r.high)) === J([false, true, false, false, false]));
  check("the overdue flag on each row is the API's (not worked out in the page)", J(todayRows.map((r) => r.overdue)) === J((await apiView("today")).items.map((t) => t.overdue)));
  await b.clickText("a", "Upcoming"); await b.waitFor(`location.search.includes('view=upcoming')`, "URL"); await sleep(400);
  check("Upcoming holds only later dates, by date and priority, and equals the API", J(await titlesNow()) === J(["Tomorrow", "Next week high", "Next week"]) && J(await titlesNow()) === J(await apiTitles("upcoming")));
  check("the current view link follows the URL", await b.has(`document.querySelector('nav[aria-label="Task views"] a[aria-current="true"]')?.textContent.trim() === 'Upcoming'`));
  await b.clickText("a", "Anytime"); await b.waitFor(`location.search.includes('view=anytime')`, "URL"); await sleep(400);
  check("Anytime holds the undated tasks, high priority first, and equals the API", J(await titlesNow()) === J(["No date high", "No date"]) && J(await titlesNow()) === J(await apiTitles("anytime")));
  await b.goto("/tasks?view=nonsense"); await b.waitFor(`document.querySelector('form[aria-label="Add a task"]')`, "page");
  check("an unknown ?view= falls back to Today", await b.has(`document.querySelector('nav[aria-label="Task views"] a[aria-current="true"]')?.textContent.trim() === 'Today'`));
  await b.shot("tasks-today-1280");

  // ------------------------------------------------------------------------------------------------
  begin("Adding a task with one line, and undoing it (390px)");
  await b.viewport(390, 900); await openView("anytime");
  await b.type(`document.querySelector('input[name=title]')`, "   ", "blank"); await pressEnter(); await sleep(300);
  check("a blank line is refused with a message and nothing is sent", await b.has(hasText("Type a task first")) && (await api.get("/api/v1/tasks/summary")).body.open === 10);
  await addTask("  Buy oat milk  ");
  await until(async () => (await apiTitles("anytime")).includes("Buy oat milk"), "task created");
  const created = (await apiView("anytime")).items.find((t) => t.title === "Buy oat milk");
  check("Enter adds one task: trimmed title, no date, normal priority, open", created.dueDate === null && created.priority === "NORMAL" && created.completedAt === null && created.title === "Buy oat milk");
  await b.waitFor(hasText("Added “Buy oat milk” to Anytime"), "note");
  check("the page says where it went, the field is cleared and the task is in the list", (await b.eval(`document.querySelector('input[name=title]').value`)) === "" && (await titlesNow()).includes("Buy oat milk"));
  await b.clickText("button", "Undo"); await until(async () => !(await apiTitles("anytime")).includes("Buy oat milk"), "undone");
  await until(async () => !(await titlesNow()).includes("Buy oat milk"), "row gone");
  check("Undo deleted the task it had just added", true);
  await addTask("Water the plants"); await until(async () => (await apiTitles("anytime")).includes("Water the plants"), "kept task");

  // ------------------------------------------------------------------------------------------------
  begin("Completing, reopening and Undo");
  await b.waitFor(`document.querySelector('[data-task-row]')`, "rows");
  await b.click(checkboxOf("Water the plants"), "complete");
  await until(async () => (await apiTitles("done")).includes("Water the plants"), "completed");
  await until(async () => !(await titlesNow()).includes("Water the plants"), "row left Anytime");
  const doneTask = (await apiView("done")).items.find((t) => t.title === "Water the plants");
  check("one tap completed it (API has a completion moment) and the row left the view", !!doneTask.completedAt);
  await b.waitFor(hasText("Completed “Water the plants”"), "undo note");
  await b.clickText("button", "Undo"); await until(async () => (await apiTitles("anytime")).includes("Water the plants"), "reopened");
  await until(async () => (await titlesNow()).includes("Water the plants"), "row back");
  check("Undo reopened it: the API says open again and the row is back in Anytime", (await apiView("anytime")).items.find((t) => t.title === "Water the plants").completedAt === null);
  await b.click(checkboxOf("Water the plants"), "complete again"); await until(async () => (await apiTitles("done")).includes("Water the plants"), "done");
  await sleep(9500);
  check("the Undo note goes away by itself after a few seconds", !(await b.has(`document.querySelector('[data-undo]')`)));
  await openView("done");
  const doneRows = await rows();
  check("Done lists it with a filled check and a struck-through title, equal to the API", doneRows.length === 1 && doneRows[0].title === "Water the plants" && doneRows[0].done && J(await titlesNow()) === J(await apiTitles("done")));
  check("the check's accessible name says Reopen", await b.has(`document.querySelector('[data-task-row] [role=checkbox]').getAttribute('aria-label') === 'Reopen: Water the plants'`));
  await b.click(`document.querySelector('[data-task-row] [role=checkbox]')`, "reopen");
  await until(async () => (await apiTitles("anytime")).includes("Water the plants"), "reopened from Done");
  await until(async () => (await titlesNow()).length === 0, "left Done");
  check("tapping the check in Done reopens the task and it leaves Done", (await apiTitles("done")).length === 0);
  await b.clickText("button", "Undo"); await until(async () => (await apiTitles("done")).includes("Water the plants"), "undo the reopen");
  check("and that can be undone too", true);
  await api.post(`/api/v1/tasks/${(await apiView("done")).items[0].id}/reopen`);

  // ------------------------------------------------------------------------------------------------
  begin("Editing: the sheet, PATCH semantics (left alone vs cleared), validation, delete");
  await openView("upcoming");
  await b.click(editOf("Next week"), "edit Next week"); await b.waitFor(sheet, "sheet"); await sleep(300);
  const target = (await apiView("upcoming")).items.find((t) => t.title === "Next week");
  check("the sheet has title, notes, due date, priority and a goal picker (defaulting to No goal)", J(await b.eval(`[...${sheet}.querySelectorAll('[name]')].map((e) => e.name)`)) === J(["title", "notes", "dueDate", "goalId"]) && await b.has(`${sheet}.querySelector('[role=radiogroup]')`) && (await b.eval(`${sheet}.querySelector('select[name=goalId]').value`)) === "");
  check("it is pre-filled with the task's own values", (await b.eval(`${field("title")}.value`)) === "Next week" && (await b.eval(`${field("dueDate")}.value`)) === target.dueDate && await b.has(`${sheet}.querySelector('[role=radio][aria-checked=true]').textContent.trim() === 'Normal'`));
  await b.shot("tasks-edit-sheet-390");
  await setValue(field("title"), "Next week (renamed)"); await b.clickText("button", "Save");
  await b.waitFor(`!${sheet}`, "sheet closed");
  let t = (await apiView("upcoming")).items.find((x) => x.id === target.id);
  check("changing only the title changed only the title (date, priority and notes were left alone)", t.title === "Next week (renamed)" && t.dueDate === target.dueDate && t.priority === "NORMAL" && t.notes === null, J(t));
  await until(async () => (await titlesNow()).includes("Next week (renamed)"), "list updated");
  await b.click(editOf("Next week (renamed)"), "edit again"); await b.waitFor(sheet, "sheet"); await sleep(300);
  await setValue(field("notes"), "  bring the receipt  ", "HTMLTextAreaElement");
  await b.click(`${sheet}.querySelector('[role=radio]:last-child')`, "High");
  await b.clickText("button", "Save"); await b.waitFor(`!${sheet}`, "closed");
  t = (await apiView("upcoming")).items.find((x) => x.id === target.id);
  check("notes (trimmed) and priority were saved, the title and date untouched", t.notes === "bring the receipt" && t.priority === "HIGH" && t.title === "Next week (renamed)" && t.dueDate === target.dueDate, J(t));
  await until(async () => (await rows()).find((r) => r.title === "Next week (renamed)")?.high === true, "high mark");
  await b.click(editOf("Next week (renamed)"), "edit"); await b.waitFor(sheet, "sheet"); await sleep(300);
  await b.clickText("button", "No date"); await b.clickText("button", "Save"); await b.waitFor(`!${sheet}`, "closed");
  t = (await apiView("anytime")).items.find((x) => x.id === target.id);
  check("clearing the date sent an explicit null: the task is undated and moved to Anytime, and its notes and priority survived", !!t && t.dueDate === null && t.notes === "bring the receipt" && t.priority === "HIGH", J(t));
  await openView("anytime");
  await b.click(editOf("Next week (renamed)"), "edit"); await b.waitFor(sheet, "sheet"); await sleep(300);
  await setValue(field("notes"), "", "HTMLTextAreaElement"); await setValue(field("dueDate"), addDays(today, 30)); await b.clickText("button", "Save"); await b.waitFor(`!${sheet}`, "closed");
  t = (await apiView("upcoming")).items.find((x) => x.id === target.id);
  check("clearing the notes sent null and setting a date moved it to Upcoming; the title and priority were untouched", t.notes === null && t.dueDate === addDays(today, 30) && t.priority === "HIGH" && t.title === "Next week (renamed)", J(t));
  await openView("today");
  await b.click(editOf("Due today"), "edit Due today"); await b.waitFor(sheet, "sheet"); await sleep(300);
  await setValue(field("title"), "   "); await b.clickText("button", "Save"); await sleep(400);
  check("a blank title is refused with a message and the sheet stays open", await b.has(`${sheet}.innerText.includes('A task needs a title')`) && (await apiTitles("today")).includes("Due today"));
  await setValue(field("title"), "x".repeat(200)); await setValue(field("dueDate"), "1999-01-01"); await b.clickText("button", "Save"); await sleep(600);
  check("the server's validation (a date before 2000) is shown in the sheet and nothing changes", (await b.eval(`${sheet}.innerText`)).includes("Dates before 2000-01-01") && (await apiTitles("today")).includes("Due today"));
  await b.key("Escape", "Escape", 27); await sleep(400);
  check("Escape closes the sheet without saving", !(await b.has(sheet)) && (await apiTitles("today")).includes("Due today"));
  await openView("upcoming");
  await b.click(editOf("Tomorrow"), "edit Tomorrow"); await b.waitFor(sheet, "sheet"); await sleep(300);
  const delBtn = () => b.click(`[...${sheet}.querySelectorAll('button')].find((x) => (x.getAttribute('aria-label') || '').includes('Delete task') || x.innerText.trim() === 'Delete')`, "Delete task");
  await delBtn();
  check("the first tap on Delete only arms it (the task still exists)", (await apiTitles("upcoming")).includes("Tomorrow"));
  await delBtn(); await until(async () => !(await apiTitles("upcoming")).includes("Tomorrow"), "deleted");
  await b.waitFor(`!${sheet}`, "sheet closed");
  check("the second tap deleted it (API) and closed the sheet", true);

  // ------------------------------------------------------------------------------------------------
  begin("Done view paging and the dashboard card against the API");
  for (let i = 1; i <= 22; i++) { const r = await api.post("/api/v1/tasks", { title: `bulk ${String(i).padStart(2, "0")}` }); await api.post(`/api/v1/tasks/${r.body.id}/complete`); }
  await openView("done");
  const doneApi = (await api.get("/api/v1/tasks?view=done&size=20")).body;
  check("Done shows 20 rows and page links (the API's total pages), newest first", (await rows()).length === 20 && await b.has(hasText(`Page 1 of ${doneApi.totalPages}`)) && J(await titlesNow()) === J(doneApi.items.map((x) => x.title)) && doneApi.totalItems === 22);
  await b.clickText("a", "Next"); await b.waitFor(`location.search.includes('page=1')`, "page 1");
  const p1 = (await api.get("/api/v1/tasks?view=done&size=20&page=1")).body;
  await until(async () => J(await titlesNow()) === J(p1.items.map((x) => x.title)), "page 1 rows");
  check("page 2 shows the rest, and the view is kept in the URL", (await b.url()).includes("view=done") && (await rows()).length === 2);
  await b.viewport(1280, 900); await b.goto("/dashboard"); await b.waitFor(`document.querySelector('[data-tasks-card]')`, "card");
  const sum = (await api.get("/api/v1/tasks/summary")).body;
  const first3 = (await api.get("/api/v1/tasks?view=today&size=3")).body.items.map((x) => x.title);
  const counts = await b.eval(`document.querySelector('[data-due-counts]').innerText.replace(/\\s+/g, ' ').trim()`);
  check("the card shows the due-today and overdue counts from the summary API", counts === `${sum.dueToday} due today, ${sum.overdue} overdue` && sum.overdue === 4 && sum.dueToday === 1, counts);
  check("it lists the first three Today titles, in order", J(await b.eval(`[...document.querySelectorAll('[data-tasks-card] [data-task-title]')].map((x) => x.textContent)`)) === J(first3));
  check("it is read-only: no buttons or inputs, only a View tasks link", (await b.eval(`document.querySelectorAll('[data-tasks-card] button, [data-tasks-card] input').length`)) === 0 && await b.has(`document.querySelector('[data-tasks-card] a[href="/tasks"]')?.textContent.trim() === 'View tasks'`));
  await b.shot("tasks-dashboard-card-1280");
  await b.click(`document.querySelector('[data-tasks-card] a[href="/tasks"]')`, "View tasks"); await b.waitFor(`location.pathname === '/tasks'`, "tasks page");
  check("View tasks opens /tasks", true);
  for (const o of await api.get("/api/v1/tasks?view=today&size=100").then((r) => r.body.items)) await api.post(`/api/v1/tasks/${o.id}/complete`);
  await b.goto("/dashboard"); await b.waitFor(`document.querySelector('[data-tasks-card]')`, "card");
  const s2 = (await api.get("/api/v1/tasks/summary")).body;
  check("with nothing due the card says so and shows the open count from the API", (await b.eval(`document.querySelector('[data-nothing-due]')?.innerText ?? ''`)).includes(`${s2.open} open tasks`) && s2.dueToday + s2.overdue === 0, J(s2));

  // ------------------------------------------------------------------------------------------------
  begin("Responsive: overflow and touch targets at 390, 768 and 1280 px (page, edit sheet, dashboard)");
  await api.post("/api/v1/tasks", { title: "A very long task title " + "with many words ".repeat(5) + "Unbrokenword".repeat(6), dueDate: today, priority: "HIGH", notes: "n" });
  for (const w of [390, 768, 1280]) {
    await b.viewport(w, 900); await openView("today"); await sleep(400);
    const o = await b.overflow(); check(`${w}px tasks: no horizontal overflow (with a very long title)`, !o.overflow && o.offenders.length === 0, J(o));
    const small = await b.eval(`[...document.querySelectorAll('main a, main button, main input')].filter((e) => __h.visible(e) && e.getBoundingClientRect().height < 40).map((e) => (e.getAttribute('aria-label') || e.textContent || e.name).trim().slice(0, 30))`);
    check(`${w}px tasks: every button, link and field is at least 40px tall`, small.length === 0, J(small));
    const named = await b.eval(`[...document.querySelectorAll('[data-task-row] [role=checkbox], [data-task-row] button')].every((e) => (e.getAttribute('aria-label') || '').length > 0)`);
    check(`${w}px tasks: every check and title button has an accessible name`, named);
    await b.click(`document.querySelector('[data-task-row] button[aria-label^="Edit task"]')`, "edit"); await b.waitFor(sheet, "sheet"); await sleep(400);
    const os = await b.overflow(); const box = await b.eval(`(() => { const r = ${sheet}.getBoundingClientRect(); return { l: r.left, r: r.right, w: innerWidth }; })()`);
    const sm = await b.eval(`[...${sheet}.querySelectorAll('button, input, textarea')].filter((e) => __h.visible(e) && e.getBoundingClientRect().height < 40).map((e) => (e.getAttribute('aria-label') || e.textContent || e.name).trim().slice(0, 30))`);
    check(`${w}px edit sheet: no overflow, inside the viewport, touch targets >= 40px`, !os.overflow && box.l >= 0 && box.r <= box.w + 1 && sm.length === 0, J({ os: os.offenders, box, sm }));
    await b.key("Escape", "Escape", 27); await sleep(350);
    await b.goto("/dashboard"); await b.waitFor(`document.querySelector('[data-tasks-card]')`, "card"); await sleep(300);
    const od = await b.overflow(); check(`${w}px dashboard: no overflow with the Tasks-due card`, !od.overflow && od.offenders.length === 0, J(od));
    if (w !== 768) await b.shot(`tasks-${w}`);
  }

  // ------------------------------------------------------------------------------------------------
  begin("Loading state, backend unavailable, a failed add and recovery");
  await b.viewport(1280, 900); await b.goto("/dashboard"); await b.waitFor(`document.querySelector('[data-tasks-card]')`, "dashboard");
  const pid = backendPid();
  execSync(`kill -STOP ${pid}`);
  try {
    await b.click(`document.querySelector('a[href="/tasks"]')`, "Tasks nav");
    let sawLoading = false;
    for (let i = 0; i < 20 && !sawLoading; i++) { sawLoading = await b.has(`!!document.querySelector('[aria-busy="true"]')`); if (!sawLoading) await sleep(150); }
    check("the loading skeleton shows while the backend is slow", sawLoading);
    await b.waitFor(hasText("We can't reach the server"), "unavailable panel", 15000);
    check("a frozen backend shows the unavailable panel, and the user stays on /tasks", (await b.url()).startsWith("/tasks"));
    await b.shot("tasks-unavailable-1280");
  } finally { execSync(`kill -CONT ${pid}`); }
  await sleep(1500); await b.clickText("button", "Try again");
  await b.waitFor(`document.querySelector('form[aria-label="Add a task"]')`, "recovered", 15000);
  check("Try again brings the page back", true);
  await b.viewport(390); await openView("anytime");
  const openBefore = (await api.get("/api/v1/tasks/summary")).body.open;
  await stopBackend();
  check("backend is down", !(await backendHealthy()));
  await addTask("Offline task"); await b.waitFor(`document.querySelector('[role=alert]')`, "inline error", 15000);
  check("adding while the backend is down shows an inline error and keeps what was typed", (await b.eval(`document.querySelector('input[name=title]').value`)) === "Offline task" && !(await b.has(`document.querySelector('[data-undo]')`)));
  await b.shot("tasks-error-390");
  check("backend restarted", await startBackend());
  await b.eval("location.reload()"); await b.waitFor(`document.querySelector('form[aria-label="Add a task"]')`, "reloaded", 20000); await waitHydrated();
  check("after recovery nothing was added by the failed attempt", (await api.get("/api/v1/tasks/summary")).body.open === openBefore);
  await addTask("Back online"); await until(async () => (await apiTitles("anytime")).includes("Back online"), "added once");
  check("a fresh add after recovery works and adds exactly once", (await apiView("anytime")).items.filter((x) => x.title === "Back online").length === 1);
} catch (e) { check("section completed without error", false, e.message); await b.shot("FAILED-run10-tasks"); }
b.close();
summary();
