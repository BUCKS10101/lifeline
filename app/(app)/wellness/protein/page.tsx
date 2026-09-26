import Link from "next/link";
import { redirect } from "next/navigation";
import { RangeSelector } from "@/components/charts/range-selector";
import { EmptyState } from "@/components/fitness/empty-state";
import { Panel, SectionHeading } from "@/components/fitness/ui";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { DayPicker } from "@/components/wellness/day-picker";
import { IntakeEntries } from "@/components/wellness/intake-entries";
import { ProteinLog } from "@/components/wellness/protein-log";
import { IntakeAverageText, IntakeChart } from "@/components/wellness/metric-charts";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import { formatFullDate, formatGrams, formatTime, todayIn } from "@/lib/format";
import type { ProteinDay, ProteinSeries, ProteinSuggestion, Today } from "@/lib/wellness-types";
import { DEFAULT_INTAKE_RANGE, INTAKE_RANGES, parseDay, parseRange, rangeWindow } from "@/lib/wellness-range";

export const metadata = { title: "Protein | Personal OS" };

export default async function ProteinPage(props: PageProps<"/wellness/protein">) {
  const params = await props.searchParams;
  const range = parseRange(INTAKE_RANGES, DEFAULT_INTAKE_RANGE, params.range);

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const today = todayIn(user.timezone);
  const date = parseDay(params.date, today);
  const isToday = date === today;
  const win = rangeWindow(range.days, today);
  const [todayResult, seriesResult, dayResult, suggestionsResult] = await Promise.all([
    backendGet<Today>("/api/v1/wellness/today"),
    backendGet<ProteinSeries>(`/api/v1/protein/series?from=${win.from}&to=${win.to}`),
    backendGet<ProteinDay>(`/api/v1/protein-entries?date=${date}`),
    backendGet<ProteinSuggestion[]>("/api/v1/protein/suggestions"),
  ]);
  const overview = resolve(todayResult);
  const series = resolve(seriesResult);
  const day = resolve(dayResult);
  const suggestions = resolve(suggestionsResult);
  if (!overview || !series || !day || !suggestions) return <BackendUnavailable />;
  if (!overview.protein) redirect("/wellness");   // a hidden metric has no page: back to Today

  const goal = day.goalG;
  const description = `${isToday ? "Today" : formatFullDate(date)}: ${formatGrams(day.totalG)}${goal ? ` of ${formatGrams(goal)}` : ""}`;
  const extra = isToday ? "" : `date=${date}`;

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-8">
      <PageHeader
        title="Protein"
        description={description}
        actions={<Link href="/wellness" className="inline-flex h-11 items-center text-sm text-muted-foreground hover:text-foreground hover:underline">Today</Link>}
      />

      <section aria-labelledby="log-heading" className="flex flex-col gap-3">
        <SectionHeading id="log-heading">{isToday ? "Log protein" : `Log protein for ${formatFullDate(date)}`}</SectionHeading>
        <Panel className="p-4"><ProteinLog date={date} isToday={isToday} suggestions={suggestions} /></Panel>
      </section>

      <section aria-labelledby="trend-heading" className="flex flex-col gap-3">
        <SectionHeading id="trend-heading">Daily totals</SectionHeading>
        <RangeSelector basePath="/wellness/protein" options={INTAKE_RANGES} current={range.value} extraQuery={extra} />
        <Panel className="p-3 md:p-4"><IntakeChart kind="protein" series={series} /></Panel>
        <IntakeAverageText kind="protein" average={series.average} previous={series.previousAverage} rangeDays={range.days} />
      </section>

      <section aria-labelledby="entries-heading" className="flex flex-col gap-3">
        <SectionHeading id="entries-heading">{isToday ? "Today's entries" : `Entries on ${formatFullDate(date)}`}</SectionHeading>
        {day.entries.length === 0 ? (
          <EmptyState title={isToday ? "Nothing logged today" : "Nothing logged that day"}>Add some above and it will show up here.</EmptyState>
        ) : (
          <IntakeEntries path="/api/v1/protein-entries" noun="entry"
            rows={day.entries.map((e) => ({ id: e.id, time: formatTime(e.loggedAt, user.timezone), amount: formatGrams(e.grams), label: e.label }))} />
        )}
        <DayPicker basePath="/wellness/protein" date={date} today={today} range={range.value} />
      </section>
    </div>
  );
}
