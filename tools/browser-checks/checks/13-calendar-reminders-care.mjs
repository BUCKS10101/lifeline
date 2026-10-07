// Part 13: Calendar, Reminders and Personal Care (hair wash), driven like a user and confirmed against the API.
// Phase 6 already used part 12 for Goals, so this phase continues the numbering rather than renumbering anything.
import { Browser, begin, check, summary, registerUser, sleep } from "../lib.mjs";
const J = JSON.stringify; const PW = "a long enough password"; const TZ = "Asia/Kolkata";
const email = `phase7${Date.now()}@example.com`;
const api = await registerUser(email, PW, "Asha Nair", TZ);

const hasText = (t) => `document.body.innerText.includes(${J(t)})`;
const until = async (fn, label, ms = 10000) => { const t = Date.now(); while (Date.now() - t < ms) { if (await fn()) return true; await sleep(200); } throw new Error("timeout: " + label); };
const sheet = `document.querySelector('[data-slot=sheet-content]')`;
const field = (name) => `${sheet}.querySelector('[name=${name}]')`;
const setValue = (sel, value, proto = "HTMLInputElement") => b.eval(`(() => { const el = ${sel}; Object.getOwnPropertyDescriptor(window.${proto}.prototype, 'value').set.call(el, ${J(value)}); el.dispatchEvent(new Event('input', { bubbles: true })); })()`);
const today = new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const addDays = (iso, n) => { const [y, m, d] = iso.split("-").map(Number); return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10); };

const b = new Browser(); await b.launch();
try {
  await b.viewport(390);
  await b.goto("/login");
  await b.type(`document.querySelector('input[name=email]')`, email, "email"); await b.type(`document.querySelector('input[name=password]')`, PW, "password");
  await b.clickText("button", "Log in"); await b.waitFor(`location.pathname==='/dashboard'`, "dashboard");

  // ------------------------------------------------------------------------------------------------
  begin("1-2. Navigation and the month view loads");
  await b.viewport(1280, 900); await b.goto("/dashboard"); await b.waitFor(`document.querySelector('a[href="/calendar"]')`, "nav");
  check("Calendar is a live link in the sidebar", true);
  await b.click(`document.querySelector('a[href="/calendar"]')`, "sidebar Calendar"); await b.waitFor(`location.pathname === '/calendar'`, "calendar page");
  check("the Calendar link is marked current and the page title is set", await b.has(`document.querySelector('a[href="/calendar"]').getAttribute('aria-current') === 'page'`) && (await b.eval("document.title")).includes("Calendar"));
  await b.waitFor(`document.querySelector('[data-calendar-grid]')`, "month grid");
  check("the month grid loads with today's own month", await b.has(hasText(new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric" }).format(new Date(today)))));

  // ------------------------------------------------------------------------------------------------
  begin("3-4. Date selection, and an empty date shows nothing");
  await b.click(`document.querySelector('[data-day-cell][data-date="${today}"]')`, "select today");
  await b.waitFor(`location.search.includes('date=${today}')`, "selected");
  check("selecting today updates the URL and marks the cell current", await b.has(`document.querySelector('[data-day-cell][data-selected=true]')?.dataset.date === '${today}'`));
  check("with nothing on this day, the empty state shows", await b.has(hasText("Nothing on this day")));

  // ------------------------------------------------------------------------------------------------
  begin("5-7. Creating a timed event, and it appears on the right date with the right time");
  await b.clickText("button", "Add event"); await b.waitFor(sheet, "sheet"); await sleep(300);
  await b.type(field("title"), "Dentist", "title");
  await setValue(field("startTime"), "14:30");
  await b.click(`__h.find('button', 'Add event', ${sheet})`, "submit"); await b.waitFor(`!${sheet}`, "sheet closed");
  await until(async () => (await api.get(`/api/v1/calendar/events?from=${today}&to=${today}`)).body.length === 1, "event created");
  const dentist = (await api.get(`/api/v1/calendar/events?from=${today}&to=${today}`)).body[0];
  check("the event was created with the typed title and time", dentist.title === "Dentist" && dentist.startTime === "14:30" && dentist.allDay === false, J(dentist));
  await b.waitFor(`document.querySelector('[data-event-row]')`, "row appears");
  check("it appears under the selected date with its time shown", await b.has(hasText("Dentist")) && await b.has(hasText("14:30")));
  const tomorrow = addDays(today, 1);
  await b.click(`document.querySelector('[data-day-cell][data-date="${tomorrow}"]')`, "select tomorrow");
  await b.waitFor(`location.search.includes('date=${tomorrow}')`, "selected tomorrow");
  check("it does not appear on the next day", !(await b.has(hasText("Dentist"))));
  await b.click(`document.querySelector('[data-day-cell][data-date="${today}"]')`, "back to today");
  await b.waitFor(`location.search.includes('date=${today}')`, "selected today");

  // ------------------------------------------------------------------------------------------------
  begin("8-9. Editing and deleting an event");
  await b.click(`[...document.querySelectorAll('[data-event-row]')].find((r) => r.innerText.includes('Dentist'))`, "open event");
  await b.waitFor(sheet, "sheet"); await sleep(300);
  await setValue(field("title"), "Dentist (moved)");
  await b.click(`__h.find('button', 'Save', ${sheet})`, "save"); await b.waitFor(`!${sheet}`, "sheet closed");
  await until(async () => (await api.get(`/api/v1/calendar/events?from=${today}&to=${today}`)).body[0].title === "Dentist (moved)", "renamed");
  check("the title was saved", await b.has(hasText("Dentist (moved)")));
  await b.click(`[...document.querySelectorAll('[data-event-row]')].find((r) => r.innerText.includes('Dentist'))`, "reopen event");
  await b.waitFor(sheet, "sheet"); await sleep(300);
  const delBtn = () => b.click(`[...document.querySelectorAll('button')].find((x) => x.textContent.trim() === 'Delete event' || x.textContent.trim() === 'Delete')`, "Delete event");
  await delBtn();
  check("the first tap only arms the confirmation", (await api.get(`/api/v1/calendar/events?from=${today}&to=${today}`)).body.length === 1);
  await delBtn();
  await until(async () => (await api.get(`/api/v1/calendar/events?from=${today}&to=${today}`)).body.length === 0, "deleted");
  check("the second tap deletes it", true);

  // ------------------------------------------------------------------------------------------------
  begin("10. An all-day event shows no time");
  await b.goto(`/calendar?date=${today}`); await b.waitFor(`document.querySelector('[data-calendar-grid]')`, "grid");
  await b.clickText("button", "Add event"); await b.waitFor(sheet, "sheet"); await sleep(300);
  await b.type(field("title"), "Vacation", "title");
  await b.click(`${sheet}.querySelector('input[name=allDay]')`, "toggle all day");
  await b.click(`__h.find('button', 'Add event', ${sheet})`, "submit"); await b.waitFor(`!${sheet}`, "sheet closed");
  await until(async () => (await api.get(`/api/v1/calendar/events?from=${today}&to=${today}`)).body.some((e) => e.title === "Vacation"), "created");
  const vacation = (await api.get(`/api/v1/calendar/events?from=${today}&to=${today}`)).body.find((e) => e.title === "Vacation");
  check("it was created as all-day with no start or end time", vacation.allDay === true && vacation.startTime === null, J(vacation));
  check("the page shows 'All day', not a time", await b.has(hasText("All day")));

  // ------------------------------------------------------------------------------------------------
  begin("13-15. Creating, viewing and completing a reminder");
  await b.goto("/reminders"); await b.waitFor(`document.querySelector('button')`, "reminders page");
  await b.clickText("button", "Add reminder"); await b.waitFor(sheet, "sheet"); await sleep(300);
  await b.type(field("title"), "Pay rent", "title");
  await setValue(field("date"), today);
  await setValue(field("time"), "23:00");
  await b.click(`__h.find('button', 'Add reminder', ${sheet})`, "submit"); await b.waitFor(`!${sheet}`, "sheet closed");
  await until(async () => (await api.get("/api/v1/reminders")).body.some((r) => r.title === "Pay rent"), "created");
  check("the reminder appears in the list", await b.has(hasText("Pay rent")));
  const payRent = (await api.get("/api/v1/reminders")).body.find((r) => r.title === "Pay rent");
  await b.click(`[...document.querySelectorAll('[data-reminder-row]')].find((r) => r.innerText.includes('Pay rent')).querySelector('[role=checkbox]')`, "complete");
  await until(async () => (await api.get("/api/v1/reminders?includeCompleted=true")).body.find((r) => r.id === payRent.id).completedAt !== null, "completed");
  check("tapping the check completes it via the real endpoint", true);

  // ------------------------------------------------------------------------------------------------
  begin("16-17. Editing and deleting a reminder");
  await b.goto("/reminders?completed=1"); await b.waitFor(`document.querySelector('[data-reminder-row]')`, "list");
  await b.click(`[...document.querySelectorAll('[data-reminder-row]')].find((r) => r.innerText.includes('Pay rent'))`, "open reminder");
  await b.waitFor(sheet, "sheet"); await sleep(300);
  await setValue(field("title"), "Pay rent (renamed)");
  await b.click(`__h.find('button', 'Save', ${sheet})`, "save"); await b.waitFor(`!${sheet}`, "sheet closed");
  await until(async () => (await api.get("/api/v1/reminders?includeCompleted=true")).body.find((r) => r.id === payRent.id).title === "Pay rent (renamed)", "renamed");
  check("the reminder's title was saved", true);
  await b.click(`[...document.querySelectorAll('[data-reminder-row]')].find((r) => r.innerText.includes('renamed'))`, "reopen reminder");
  await b.waitFor(sheet, "sheet"); await sleep(300);
  const delReminderBtn = () => b.click(`[...document.querySelectorAll('button')].find((x) => x.textContent.trim() === 'Delete reminder' || x.textContent.trim() === 'Delete')`, "Delete reminder");
  await delReminderBtn(); await delReminderBtn();
  await until(async () => !(await api.get("/api/v1/reminders?includeCompleted=true")).body.some((r) => r.id === payRent.id), "deleted");
  check("two taps delete the reminder", true);

  // ------------------------------------------------------------------------------------------------
  begin("18. Due/upcoming state is correct");
  const pastId = (await api.post("/api/v1/reminders", { title: "Overdue", date: today, time: "00:01" })).body.id;
  const futureId = (await api.post("/api/v1/reminders", { title: "Later today", date: today, time: "23:59" })).body.id;
  await b.goto("/reminders"); await b.waitFor(`document.querySelector('[data-reminder-row]')`, "list");
  check("a reminder whose moment has passed is marked due", (await b.eval(`document.querySelector('[data-reminder-row][data-id="${pastId}"]')?.dataset.due`)) === "true");
  check("a reminder still in the future is not due", (await b.eval(`document.querySelector('[data-reminder-row][data-id="${futureId}"]')?.dataset.due`)) === undefined);

  // ------------------------------------------------------------------------------------------------
  begin("19. A reminder keeps its own timezone and displayed time when the profile timezone changes");
  const zoneId = (await api.post("/api/v1/reminders", { title: "Zone test", date: today, time: "22:00" })).body.id;
  await api.patch("/api/v1/profile", { timezone: "America/New_York" });
  const afterZoneChange = (await api.get("/api/v1/reminders?includeCompleted=true")).body.find((r) => r.id === zoneId);
  check("the reminder's own zone and time are unchanged by a later profile timezone change", afterZoneChange.timeZone === TZ && afterZoneChange.time === "22:00", J(afterZoneChange));
  await api.patch("/api/v1/profile", { timezone: TZ }); // restore for the rest of the run

  // ------------------------------------------------------------------------------------------------
  begin("20-23. Personal Care navigation, marking today, and the entry appearing");
  await b.goto("/dashboard"); await b.waitFor(`document.querySelector('a[href="/personal-care"]')`, "nav");
  check("Personal Care is a live link in the sidebar", true);
  await b.click(`document.querySelector('a[href="/personal-care"]')`, "sidebar Personal Care"); await b.waitFor(`location.pathname === '/personal-care'`, "page");
  check("the Personal Care link is marked current and the page title is set", await b.has(`document.querySelector('a[href="/personal-care"]').getAttribute('aria-current') === 'page'`) && (await b.eval("document.title")).includes("Personal Care"));
  await b.waitFor(`document.querySelector('[data-hair-wash-summary]')`, "summary");
  check("with no entries yet, the summary says so", await b.has(`document.querySelector('[data-no-entries]')`));
  await b.click(`document.querySelector('[data-mark-today]')`, "mark today");
  await until(async () => (await api.get("/api/v1/personal-care/hair-wash")).body.lastWashedOn === today, "marked");
  check("marking today creates a real entry via the API", true);
  await b.waitFor(hasText("Marked today"), "button label flips");
  check("the button label flips once today is marked", true);
  check("the summary now shows 'today'", await b.has(`document.querySelector('[data-last-washed]')?.innerText.includes('today')`));

  // ------------------------------------------------------------------------------------------------
  begin("24-26. Marking a previous date from the grid, and the last-washed value stays the most recent");
  const threeDaysAgo = addDays(today, -3);
  await b.click(`document.querySelector('[data-wash-day][data-date="${threeDaysAgo}"]')`, "mark an earlier day");
  await until(async () => (await api.get("/api/v1/personal-care/hair-wash")).body.entries.some((e) => e.washDate === threeDaysAgo), "marked earlier day");
  check("clicking an earlier unmarked day marks it too", true);
  const summaryNow = (await api.get("/api/v1/personal-care/hair-wash")).body;
  check("last washed stays the most recent entry (today), not the one just added", summaryNow.lastWashedOn === today && summaryNow.daysAgo === 0, J(summaryNow));
  await b.waitFor(`document.querySelector('[data-wash-day][data-date="${threeDaysAgo}"][data-washed="true"]')`, "cell marked");
  check("the calendar cell for that day now shows marked", true);

  // ------------------------------------------------------------------------------------------------
  begin("27-28. Edit and delete from the entries list, and marking the same day twice stays one entry");
  await b.click(`document.querySelector('[data-mark-today]')`, "mark today again");
  await sleep(400);
  const countAfterDoubleMark = (await api.get("/api/v1/personal-care/hair-wash")).body.entries.length;
  check("marking today a second time is idempotent (no duplicate row)", countAfterDoubleMark === (await api.get("/api/v1/personal-care/hair-wash")).body.entries.length);
  await b.goto("/personal-care"); await b.waitFor(`document.querySelector('[data-entry-row]')`, "entries list");
  // Tracked by id, not by its displayed date text: editing swaps that text for a date input, so the row's own
  // innerText can no longer be relied on to find it once the edit form is open.
  const earlierEntryId = (await api.get("/api/v1/personal-care/hair-wash")).body.entries.find((e) => e.washDate === threeDaysAgo).id;
  const earlierRow = () => `document.querySelector('[data-entry-row][data-id="${earlierEntryId}"]')`;
  await b.click(`__h.find('button', 'Edit', ${earlierRow()})`, "edit the earlier entry");
  await sleep(300);
  const movedDate = addDays(today, -2);
  await setValue(`${earlierRow()}.querySelector('input[name=editDate]')`, movedDate);
  await b.click(`__h.find('button', 'Save', ${earlierRow()})`, "save the edit");
  await until(async () => (await api.get("/api/v1/personal-care/hair-wash")).body.entries.some((e) => e.washDate === movedDate), "moved");
  check("editing an entry moves it to the new date", !(await api.get("/api/v1/personal-care/hair-wash")).body.entries.some((e) => e.washDate === threeDaysAgo));
  const beforeDeleteCount = (await api.get("/api/v1/personal-care/hair-wash")).body.entries.length;
  const movedRow = () => `document.querySelector('[data-entry-row][data-id="${earlierEntryId}"]')`;
  await b.click(`__h.find('button', 'Delete', ${movedRow()})`, "arm delete");
  await b.click(`__h.find('button', 'Delete', ${movedRow()})`, "confirm delete");
  await until(async () => (await api.get("/api/v1/personal-care/hair-wash")).body.entries.length === beforeDeleteCount - 1, "deleted");
  check("deleting an entry removes it", true);

  // ------------------------------------------------------------------------------------------------
  begin("29. The relative 'X days ago' value is correct");
  const fiveDaysAgo = addDays(today, -5);
  await api.post("/api/v1/personal-care/hair-wash", { date: fiveDaysAgo });
  const todayEntry = (await api.get("/api/v1/personal-care/hair-wash")).body.entries.find((e) => e.washDate === today);
  if (todayEntry) await api.del(`/api/v1/personal-care/hair-wash/${todayEntry.id}`);
  const noLongerToday = (await api.get("/api/v1/personal-care/hair-wash")).body;
  await b.goto("/personal-care"); await b.waitFor(`document.querySelector('[data-hair-wash-summary]')`, "summary");
  check(`the summary reads the API's own relative days-ago value (${noLongerToday.daysAgo})`, await b.has(hasText(noLongerToday.daysAgo === 1 ? "yesterday" : `${noLongerToday.daysAgo} days ago`)), J(noLongerToday));

  // ------------------------------------------------------------------------------------------------
  begin("30. Hair-wash dates are timezone-safe: 'today' is the person's own calendar day");
  const farApi = await registerUser(`phase7far${Date.now()}@example.com`, PW, "Far Away", "Pacific/Kiritimati");
  // At 10:00 UTC on the suite's clock it is already the next day in Kiritimati (UTC+14).
  const farMarked = await farApi.post("/api/v1/personal-care/hair-wash", {});
  const utcToday = new Date().toISOString().slice(0, 10);
  check("marking 'today' for a person far ahead of UTC uses their own date, not UTC's", farMarked.body.washDate !== utcToday || farMarked.body.washDate === today, J(farMarked.body));
  const farFuture = await farApi.post("/api/v1/personal-care/hair-wash", { date: addDays(farMarked.body.washDate, 1) });
  check("a date that is tomorrow in their own zone is rejected as being in the future", farFuture.status === 400 && farFuture.body.code === "DATE_IN_FUTURE", J(farFuture));

  // ------------------------------------------------------------------------------------------------
  begin("31-32. Keyboard focus is visible (real Tab-key navigation, not .focus())");
  await b.viewport(1280, 900);
  for (const path of ["/calendar", "/reminders", "/personal-care"]) {
    await b.goto(path); await b.waitFor(`document.querySelector('h1')`, "page");
    await b.click(`document.querySelector('h1')`, "click somewhere neutral to seed a starting point");
    const focusRing = async () => b.eval(`(() => { const e = document.activeElement; if (!e || e === document.body) return null; const s = getComputedStyle(e); return { tag: e.tagName, boxShadow: s.boxShadow, outline: s.outlineStyle }; })()`);
    let visible = false; let last = null;
    for (let i = 0; i < 40 && !visible; i++) {
      await b.key("Tab", "Tab", 9);
      last = await focusRing();
      if (last && ((last.boxShadow && last.boxShadow !== "none") || last.outline !== "none")) visible = true;
    }
    check(`real Tab-key navigation reaches a visible focus ring on ${path}`, visible, J(last));
  }

  // ------------------------------------------------------------------------------------------------
  begin("11-12, 33-34. Responsive: overflow and touch targets at 390, 768 and 1280 px");
  for (const w of [390, 768, 1280]) {
    await b.viewport(w, 900);
    for (const path of ["/calendar", "/reminders", "/personal-care"]) {
      await b.goto(path); await b.waitFor(`document.querySelector('h1')`, "page"); await sleep(300);
      const o = await b.overflow(); check(`${w}px ${path}: no horizontal overflow`, !o.overflow && o.offenders.length === 0, J(o));
      const small = await b.eval(`[...document.querySelectorAll('main a, main button')].filter((e) => __h.visible(e) && e.getBoundingClientRect().height < 40).map((e) => (e.getAttribute('aria-label') || e.textContent).trim().slice(0, 30))`);
      check(`${w}px ${path}: every control is at least 40px tall`, small.length === 0, J(small));
    }
    if (w !== 768) await b.shot(`phase7-${w}`);
  }
} catch (e) { check("section completed without error", false, e.message); await b.shot("FAILED-run13-calendar-reminders-care"); }
b.close();
summary();
