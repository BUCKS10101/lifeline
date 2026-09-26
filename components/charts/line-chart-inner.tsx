"use client";

import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { chartFormatters } from "@/components/charts/chart-format";
import type { LineChartProps } from "@/components/charts/line-chart";

const DAY_MS = 86_400_000;

function toTime(iso: string): number {
  return Date.parse(`${iso}T00:00:00Z`);
}

/** Keeps the line off the edges of the plot and includes the reference line, so the target is never clipped. */
function yDomain(values: number[], reference: number | null): [number, number] {
  const all = reference === null ? values : [...values, reference];
  if (all.length === 0) return [0, 1];
  const lo = Math.min(...all);
  const hi = Math.max(...all);
  const pad = Math.max((hi - lo) * 0.15, 0.5);
  return [Math.floor(lo - pad), Math.ceil(hi + pad)];
}

/** Whole-hour ticks covering a range of minutes, every hour or every two hours so there are never more than about six. */
function hourTicks(minMinutes: number, maxMinutes: number): number[] {
  const lo = Math.floor(minMinutes / 60);
  const hi = Math.ceil(maxMinutes / 60);
  const step = hi - lo > 6 ? 2 : 1;
  const ticks: number[] = [];
  for (let h = lo; h <= hi; h += step) ticks.push(h * 60);
  return ticks;
}

/** The chart itself. Only ever loaded in the browser, through the lazy wrapper in line-chart.tsx. */
export default function LineChartInner({ points, series, reference, unit, xStyle }: LineChartProps) {
  const f = chartFormatters(unit, xStyle);
  const data = points.map((p) => ({ t: toTime(p.x), x: p.x, ...p.values }));
  const values = data.flatMap((d) => series.map((s) => d[s.key as keyof typeof d]).filter((v): v is number => typeof v === "number"));
  const raw = yDomain(values, reference?.value ?? null);
  // A duration axis (minutes) runs between whole hours, so its ticks read "7 h", "8 h" and never "6.43 h".
  const ticks = unit === "duration" ? hourTicks(raw[0], raw[1]) : undefined;
  const [min, max] = ticks ? [ticks[0], ticks[ticks.length - 1]] as [number, number] : raw;
  const single = data.length === 1;
  const t0 = data[0]?.t ?? 0;
  const domain: [number, number] = single ? [t0 - 3 * DAY_MS, t0 + 3 * DAY_MS] : [data[0]?.t ?? 0, data.at(-1)?.t ?? 1];
  const showDots = data.length <= 45;

  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 0 }} accessibilityLayer={false}>
        <CartesianGrid vertical={false} stroke="var(--border)" />
        <XAxis
          dataKey="t"
          type="number"
          scale="time"
          domain={domain}
          tickCount={4}
          tickFormatter={(t: number) => f.tick(new Date(t).toISOString().slice(0, 10))}
          tickLine={false}
          axisLine={{ stroke: "var(--border)" }}
          tick={{ fill: "var(--muted-foreground)", fontSize: 12 }}
          padding={{ left: 8, right: 8 }}
        />
        <YAxis
          domain={[min, max]}
          ticks={ticks}
          tickFormatter={f.yTick}
          width={unit === "duration" || unit === "ml" ? 46 : 38}
          tickCount={5}
          allowDecimals={false}
          tickLine={false}
          axisLine={false}
          tick={{ fill: "var(--muted-foreground)", fontSize: 12 }}
        />
        {reference && <ReferenceLine y={reference.value} stroke="var(--muted-foreground)" strokeDasharray="5 4" ifOverflow="extendDomain" />}
        <Tooltip
          isAnimationActive={false}
          cursor={{ stroke: "var(--muted-foreground)", strokeDasharray: "3 3" }}
          content={({ active, payload }) => {
            if (!active || !payload?.length) return null;
            const row = payload[0].payload as { x: string } & Record<string, number | null>;
            return (
              <div className="rounded-lg border bg-popover px-3 py-2 text-sm text-popover-foreground shadow-md" data-chart-tooltip>
                <div className="label mb-1">{f.x(row.x)}</div>
                {series.map((s) => (row[s.key] == null ? null : (
                  <div key={s.key} className="num flex items-center gap-2">
                    <span className="size-2 rounded-full" style={{ background: s.color }} />
                    <span className="text-muted-foreground">{s.label}</span>
                    <span className="ml-auto pl-3 font-medium">{f.value(row[s.key] as number)}</span>
                  </div>
                )))}
              </div>
            );
          }}
        />
        {series.map((s) => (
          <Line
            key={s.key}
            dataKey={s.key}
            name={s.label}
            type="linear"
            stroke={s.kind === "dots" ? "none" : s.color}
            strokeWidth={s.kind === "dots" ? 0 : 2.5}
            connectNulls
            isAnimationActive={false}
            dot={s.kind === "line" || !(s.kind === "dots" || showDots) ? false : { r: s.kind === "dots" ? 3 : 2.5, fill: s.color, stroke: "none" }}
            activeDot={{ r: 5, fill: s.color, stroke: "var(--background)", strokeWidth: 2 }}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}
