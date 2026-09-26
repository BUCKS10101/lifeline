import Link from "next/link";
import { redirect } from "next/navigation";
import { RangeSelector } from "@/components/charts/range-selector";
import { EmptyState } from "@/components/fitness/empty-state";
import { Pagination } from "@/components/fitness/pagination";
import { Panel, SectionHeading } from "@/components/fitness/ui";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { SleepLogForm } from "@/components/wellness/sleep-log-form";
import { SleepHistory } from "@/components/wellness/sleep-history";
import { SleepAverageText, SleepChart } from "@/components/wellness/metric-charts";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { Paged } from "@/lib/fitness-types";
import { formatMinutes, todayIn } from "@/lib/format";
import type { SleepEntry, SleepSeries, Today } from "@/lib/wellness-types";
import { DEFAULT_SLEEP_RANGE, parseRange, rangeWindow, SLEEP_RANGES } from "@/lib/wellness-range";

export const metadata = { title: "Sleep | Personal OS" };

const PAGE_SIZE = 10;

export default async function SleepPage(props: PageProps<"/wellness/sleep">) {
  const params = await props.searchParams;
  const range = parseRange(SLEEP_RANGES, DEFAULT_SLEEP_RANGE, params.range);
  const parsedPage = Number(Array.isArray(params.page) ? params.page[0] : params.page);
  const page = Number.isInteger(parsedPage) && parsedPage >= 0 ? parsedPage : 0;

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const today = todayIn(user.timezone);
  const win = rangeWindow(range.days, today);
  const [todayResult, seriesResult, listResult] = await Promise.all([
    backendGet<Today>("/api/v1/wellness/today"),
    backendGet<SleepSeries>(`/api/v1/sleep/series?from=${win.from}&to=${win.to}`),
    backendGet<Paged<SleepEntry>>(`/api/v1/sleep-entries?page=${page}&size=${PAGE_SIZE}`),
  ]);
  const overview = resolve(todayResult);
  const series = resolve(seriesResult);
  const list = resolve(listResult);
  if (!overview || !series || !list) return <BackendUnavailable />;
  if (!overview.sleep) redirect("/wellness");   // a hidden metric has no page: back to Today

  const last = overview.sleep.entry;
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-8">
      <PageHeader
        title="Sleep"
        description={last ? `Last night: ${formatMinutes(last.durationMinutes)}` : "Last night is not logged yet."}
        actions={<Link href="/wellness" className="inline-flex h-11 items-center text-sm text-muted-foreground hover:text-foreground hover:underline">Today</Link>}
      />

      <section aria-labelledby="log-heading" className="flex flex-col gap-3">
        <SectionHeading id="log-heading">Log a night</SectionHeading>
        <Panel className="p-4"><SleepLogForm today={today} timeZone={user.timezone} /></Panel>
      </section>

      <section aria-labelledby="trend-heading" className="flex flex-col gap-3">
        <SectionHeading id="trend-heading">Trend</SectionHeading>
        <RangeSelector basePath="/wellness/sleep" options={SLEEP_RANGES} current={range.value} />
        <Panel className="p-3 md:p-4"><SleepChart series={series} goalMinutes={overview.sleep.goalMinutes} /></Panel>
        <SleepAverageText average={series.average} previous={series.previousAverage} rangeDays={range.days} />
      </section>

      <section aria-labelledby="history-heading" className="flex flex-col gap-3">
        <SectionHeading id="history-heading">Recent nights</SectionHeading>
        {list.items.length === 0 ? (
          <EmptyState title="No nights logged yet">Log last night above and it will show up here.</EmptyState>
        ) : (
          <>
            <SleepHistory entries={list.items} />
            <Pagination basePath="/wellness/sleep" page={page} totalPages={list.totalPages} extraQuery={`range=${range.value}`} />
          </>
        )}
      </section>
    </div>
  );
}
