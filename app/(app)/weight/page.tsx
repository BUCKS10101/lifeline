import { EmptyState } from "@/components/fitness/empty-state";
import { Pagination } from "@/components/fitness/pagination";
import { Panel, SectionHeading } from "@/components/fitness/ui";
import { RangeSelector } from "@/components/charts/range-selector";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { EntriesList } from "@/components/weight/entries-list";
import { LogWeightForm } from "@/components/weight/log-form";
import { PeriodTable } from "@/components/weight/period-table";
import { TargetProgressView } from "@/components/weight/progress";
import { WeightStats } from "@/components/weight/summary";
import { TargetForm } from "@/components/weight/target-form";
import { WeightTrendChart } from "@/components/weight/trend-chart";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import { addDays, addMonths, formatFullDate, formatMonth, startOfMonth, startOfWeek, todayIn } from "@/lib/format";
import type { BucketSeries, DailySeries, Paged, WeightEntry, WeightSummary } from "@/lib/weight-types";
import { parseRange, RANGE_OPTIONS, rangeWindow } from "@/lib/weight-range";

export const metadata = { title: "Weight | Personal OS" };

const PAGE_SIZE = 10;

export default async function WeightPage(props: PageProps<"/weight">) {
  const params = await props.searchParams;
  const range = parseRange(params.range);
  const parsedPage = Number(Array.isArray(params.page) ? params.page[0] : params.page);
  const page = Number.isInteger(parsedPage) && parsedPage >= 0 ? parsedPage : 0;

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const today = todayIn(user.timezone);
  const window = rangeWindow(range, today);
  const weekFrom = addDays(startOfWeek(today), -7 * 7);   // this week and the seven before it, whole weeks only
  const monthFrom = startOfMonth(addMonths(today, -11));  // this month and the eleven before it, whole months only

  const [summaryResult, seriesResult, weeksResult, monthsResult, entriesResult] = await Promise.all([
    backendGet<WeightSummary>("/api/v1/weight/summary"),
    backendGet<DailySeries | BucketSeries>(`/api/v1/weight/series?from=${window.from}&to=${window.to}&granularity=${window.granularity}`),
    backendGet<BucketSeries>(`/api/v1/weight/series?from=${weekFrom}&to=${today}&granularity=WEEKLY`),
    backendGet<BucketSeries>(`/api/v1/weight/series?from=${monthFrom}&to=${today}&granularity=MONTHLY`),
    backendGet<Paged<WeightEntry>>(`/api/v1/weight-entries?page=${page}&size=${PAGE_SIZE}`),
  ]);
  const summary = resolve(summaryResult);
  const series = resolve(seriesResult);
  const weeks = resolve(weeksResult);
  const months = resolve(monthsResult);
  const entries = resolve(entriesResult);
  if (!summary || !series || !weeks || !months || !entries) return <BackendUnavailable />;

  const hasEntries = summary.entryCount > 0;

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-8">
      <PageHeader title="Weight" description={hasEntries ? undefined : "Log your first weight to start your chart."} />

      {hasEntries && <WeightStats summary={summary} />}

      <section aria-labelledby="log-heading" className="flex flex-col gap-3">
        <SectionHeading id="log-heading">Log weight</SectionHeading>
        <Panel className="p-4"><LogWeightForm today={today} /></Panel>
      </section>

      {hasEntries ? (
        <section aria-labelledby="chart-heading" className="flex flex-col gap-3">
          <SectionHeading id="chart-heading">Trend</SectionHeading>
          <RangeSelector basePath="/weight" options={RANGE_OPTIONS} current={range} />
          <Panel className="p-3 md:p-4"><WeightTrendChart series={series} target={summary.target} /></Panel>
          {window.granularity === "WEEKLY" && (
            <p className="text-sm text-muted-foreground">Long ranges show the average for each week.</p>
          )}
        </section>
      ) : (
        <EmptyState title="No weights logged yet">Your chart, trend and averages appear here after your first entry.</EmptyState>
      )}

      <section aria-labelledby="target-heading" className="flex flex-col gap-3">
        <SectionHeading id="target-heading">Target</SectionHeading>
        <Panel className="flex flex-col gap-4 p-4">
          <TargetProgressView summary={summary} />
          <TargetForm target={summary.target?.targetWeightKg ?? null} />
        </Panel>
      </section>

      {hasEntries && (
        <>
          <section aria-labelledby="weeks-heading" className="flex flex-col gap-3">
            <SectionHeading id="weeks-heading">Weekly averages</SectionHeading>
            <PeriodTable label="Weekly averages" points={weeks.points} formatPeriod={(s) => `Week of ${formatFullDate(s)}`} />
          </section>

          <section aria-labelledby="months-heading" className="flex flex-col gap-3">
            <SectionHeading id="months-heading">Monthly trend</SectionHeading>
            <PeriodTable label="Monthly averages" points={months.points} formatPeriod={formatMonth} />
          </section>

          <section aria-labelledby="entries-heading" className="flex flex-col gap-3">
            <SectionHeading id="entries-heading">Entries</SectionHeading>
            <EntriesList entries={entries.items} />
            <Pagination basePath="/weight" page={page} totalPages={entries.totalPages} extraQuery={`range=${range}`} />
          </section>
        </>
      )}
    </div>
  );
}
