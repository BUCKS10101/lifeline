import { formatKg, formatNumber, formatFullDate } from "@/lib/format";
import type { WeightSummary } from "@/lib/weight-types";

const DIRECTION_LABEL = { LOSE: "Losing", GAIN: "Gaining", MAINTAIN: "Maintaining" } as const;

/** Where you are against the target, in words and a bar. All numbers come from the summary endpoint. */
export function TargetProgressView({ summary }: { summary: WeightSummary }) {
  const { target, progress, starting } = summary;
  if (!target) return <p className="text-sm text-muted-foreground">No target set.</p>;
  if (!progress || !starting) {
    return <p className="text-sm text-muted-foreground">Target {formatKg(target.targetWeightKg)} since {formatFullDate(target.startedOn)}. Log a weight to see your progress.</p>;
  }

  const headline = progress.reached
    ? (progress.direction === "MAINTAIN" ? "On target" : "Target reached")
    : progress.direction === "MAINTAIN"
      ? `${formatNumber(progress.remainingKg)} kg away from ${formatKg(target.targetWeightKg)}`
      : `${formatNumber(progress.remainingKg)} kg to go`;

  return (
    <div className="flex flex-col gap-2" data-target-progress>
      <div className="flex flex-wrap items-baseline justify-between gap-x-3">
        <span className="text-base font-medium">{headline}</span>
        <span className="num text-sm text-muted-foreground">
          {DIRECTION_LABEL[progress.direction]} · from {formatKg(starting.weightKg)} to {formatKg(target.targetWeightKg)}
        </span>
      </div>
      {progress.percent !== null && (
        <>
          <div role="progressbar" aria-label="Progress toward target" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress.percent}
            className="h-2 overflow-hidden rounded-full bg-muted">
            <div className="h-full rounded-full bg-primary" style={{ width: `${progress.percent}%` }} />
          </div>
          <span className="num text-sm text-muted-foreground">{formatNumber(progress.percent)}% of the way</span>
        </>
      )}
    </div>
  );
}
