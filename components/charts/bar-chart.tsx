"use client";

import dynamic from "next/dynamic";
import { Skeleton } from "@/components/ui/skeleton";
import { chartFormatters, type XStyle } from "@/components/charts/chart-format";
import { ChartDataTable, type DataColumn, type DataRow } from "@/components/charts/data-table";
import type { ChartPoint } from "@/components/charts/line-chart";

export type BarSeries = { key: string; label: string; color: string };

export type BarChartProps = {
  points: ChartPoint[];
  /** More than one series are stacked, in this order from the bottom. */
  series: BarSeries[];
  unit: string;
  xStyle: XStyle;
  label: string;
  rowHeader?: string;
};

/** Lazy and browser-only, like the line chart: Recharts has nothing useful to render on the server. */
const Chart = dynamic(() => import("@/components/charts/bar-chart-inner"), {
  ssr: false,
  loading: () => <Skeleton className="h-64 w-full rounded-lg" aria-hidden />,
});

/** Stacked bars plus their text alternative (a table of the same numbers, with a total when stacked). */
export function BarChart(props: BarChartProps) {
  const f = chartFormatters(props.unit, props.xStyle);
  const stacked = props.series.length > 1;
  const columns: DataColumn[] = [...props.series.map((s) => ({ key: s.key, label: s.label })), ...(stacked ? [{ key: "total", label: "Total" }] : [])];
  const rows: DataRow[] = props.points.map((p) => ({
    label: f.x(p.x),
    values: {
      ...Object.fromEntries(props.series.map((s) => [s.key, f.value(p.values[s.key] ?? 0)])),
      ...(stacked ? { total: f.value(props.series.reduce((sum, s) => sum + (p.values[s.key] ?? 0), 0)) } : {}),
    },
  }));

  return (
    <figure className="m-0" aria-label={props.label} data-chart data-points={props.points.length}>
      <div className="h-64 w-full [&_*]:outline-none" aria-hidden>
        <Chart {...props} />
      </div>
      <ul className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground" aria-hidden>
        {props.series.map((s) => (
          <li key={s.key} className="flex items-center gap-1.5">
            <span className="size-2 rounded-sm" style={{ background: s.color }} />
            {s.label}
          </li>
        ))}
      </ul>
      <ChartDataTable caption={props.label} rowHeader={props.rowHeader ?? "Week"} columns={columns} rows={rows} />
    </figure>
  );
}
