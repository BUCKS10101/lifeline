import { RangeSelector } from "@/components/charts/range-selector";
import { EmptyState } from "@/components/fitness/empty-state";
import { PrEventList } from "@/components/fitness/pr-events";
import { VolumeCharts } from "@/components/fitness/volume-charts";
import { SectionHeading } from "@/components/fitness/ui";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { ExercisePersonalRecord, VolumeSeries } from "@/lib/fitness-types";
import { parseVolumeRange, VOLUME_RANGE_OPTIONS, volumeWindow } from "@/lib/fitness-range";
import { todayIn } from "@/lib/format";

export const metadata = { title: "Progress | Personal OS" };

export default async function FitnessProgressPage(props: PageProps<"/fitness/progress">) {
  const params = await props.searchParams;
  const range = parseVolumeRange(params.range);

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const win = volumeWindow(range, todayIn(user.timezone));
  const [volumeResult, prsResult] = await Promise.all([
    backendGet<VolumeSeries>(`/api/v1/fitness/analytics/volume?from=${win.from}&to=${win.to}&granularity=${win.granularity}`),
    backendGet<ExercisePersonalRecord[]>("/api/v1/fitness/personal-records?limit=10"),
  ]);
  const volume = resolve(volumeResult);
  const prs = resolve(prsResult);
  if (!volume || !prs) return <BackendUnavailable />;

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-8">
      <PageHeader title="Progress" description="How much you are training, and your latest personal records." />

      <section aria-label="Volume and workouts" className="flex flex-col gap-4">
        <RangeSelector basePath="/fitness/progress" options={VOLUME_RANGE_OPTIONS} current={range} />
        <VolumeCharts series={volume} />
      </section>

      <section aria-labelledby="prs-heading" className="flex flex-col gap-3">
        <SectionHeading id="prs-heading">Recent personal records</SectionHeading>
        {prs.length === 0 ? (
          <EmptyState title="No records yet">Your first workout sets the baseline. Beat it and the new record shows up here.</EmptyState>
        ) : (
          <PrEventList items={prs.map((p) => ({ exercise: p.exercise, event: p.record }))} />
        )}
      </section>
    </div>
  );
}
