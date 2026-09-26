"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { chartFormatters } from "@/components/charts/chart-format";
import type { BarChartProps } from "@/components/charts/bar-chart";

/** The chart itself. Only ever loaded in the browser, through the lazy wrapper in bar-chart.tsx. */
export default function BarChartInner({ points, series, unit, xStyle }: BarChartProps) {
  const f = chartFormatters(unit, xStyle);
  const data = points.map((p) => ({ x: p.x, ...p.values }));
  const stacked = series.length > 1;

  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} accessibilityLayer={false} barCategoryGap="20%">
        <CartesianGrid vertical={false} stroke="var(--border)" />
        <XAxis dataKey="x" tickFormatter={f.tick} interval="preserveStartEnd" minTickGap={24} tickLine={false}
          axisLine={{ stroke: "var(--border)" }} tick={{ fill: "var(--muted-foreground)", fontSize: 12 }} />
        <YAxis width={44} tickCount={5} allowDecimals={false} tickLine={false} axisLine={false}
          tickFormatter={(v: number) => (v >= 1000 ? `${Math.round(v / 100) / 10}k` : String(v))}
          tick={{ fill: "var(--muted-foreground)", fontSize: 12 }} />
        <Tooltip
          isAnimationActive={false}
          cursor={{ fill: "var(--muted)", opacity: 0.4 }}
          content={({ active, payload }) => {
            if (!active || !payload?.length) return null;
            const row = payload[0].payload as { x: string } & Record<string, number>;
            const total = series.reduce((sum, s) => sum + (row[s.key] ?? 0), 0);
            return (
              <div className="rounded-lg border bg-popover px-3 py-2 text-sm text-popover-foreground shadow-md" data-chart-tooltip>
                <div className="label mb-1">{f.x(row.x)}</div>
                {series.map((s) => (
                  <div key={s.key} className="num flex items-center gap-2">
                    <span className="size-2 rounded-sm" style={{ background: s.color }} />
                    <span className="text-muted-foreground">{s.label}</span>
                    <span className="ml-auto pl-3 font-medium">{f.value(row[s.key] ?? 0)}</span>
                  </div>
                ))}
                {stacked && (
                  <div className="num mt-1 flex border-t pt-1">
                    <span className="text-muted-foreground">Total</span>
                    <span className="ml-auto pl-3 font-medium">{f.value(total)}</span>
                  </div>
                )}
              </div>
            );
          }}
        />
        {series.map((s, i) => (
          <Bar key={s.key} dataKey={s.key} name={s.label} stackId="stack" fill={s.color} isAnimationActive={false}
            radius={i === series.length - 1 ? [3, 3, 0, 0] : 0} />
        ))}
      </BarChart>
    </ResponsiveContainer>
  );
}
