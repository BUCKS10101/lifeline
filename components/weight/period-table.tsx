import { RowList } from "@/components/fitness/ui";
import { formatKg, formatNumber, pluralize } from "@/lib/format";
import type { BucketPoint } from "@/lib/weight-types";

/** Newest first. Each row is a week or a month: the average, the low-high spread and how many entries it rests on. */
export function PeriodTable({ points, formatPeriod, label }: { points: BucketPoint[]; formatPeriod: (start: string) => string; label: string }) {
  return (
    <section aria-label={label}>
    <RowList>
      {[...points].reverse().map((p) => (
        <li key={p.periodStart} className="num flex items-center justify-between gap-3 px-4 py-3" data-period-row>
          <div className="flex min-w-0 flex-col">
            <span className="font-medium">{formatPeriod(p.periodStart)}</span>
            <span className="text-sm text-muted-foreground">
              {pluralize(p.entries, "entry", "entries")}{p.entries > 1 ? ` · ${formatNumber(p.minKg)}–${formatNumber(p.maxKg)} kg` : ""}
            </span>
          </div>
          <span className="text-lg font-medium">{formatKg(p.averageKg)}</span>
        </li>
      ))}
    </RowList>
    </section>
  );
}
