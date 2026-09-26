"use client";

import dynamic from "next/dynamic";
import { Skeleton } from "@/components/ui/skeleton";
import { chartFormatters, type XStyle } from "@/components/charts/chart-format";
import { ChartDataTable, type DataColumn, type DataRow } from "@/components/charts/data-table";

export type ChartSeries = {
  key: string;
  label: string;
  /** A CSS colour, normally a chart token such as "var(--chart-1)". */
  color: string;
  /** "dots" is one dot per reading, "line" a solid line, "line-dots" a line with a dot on each reading. */
  kind: "dots" | "line" | "line-dots";
};

export type ChartPoint = { x: string; values: Record<string, number | null> };

export type LineChartProps = {
  points: ChartPoint[];
  series: ChartSeries[];
  /** A horizontal reference line, such as a target. Drawn dashed. */
  reference?: { value: number; label: string } | null;
  /** The unit written after every value, for example "kg". */
  unit: string;
  /** Whether x values are days or the first day of a week; decides how dates are written. */
  xStyle: XStyle;
  /** Names the chart for screen readers. */
  label: string;
  rowHeader?: string;
};

/**
 * Recharts is loaded on demand, in the browser only: it is the heaviest dependency in the app and a chart has nothing
 * useful to render on the server. The reserved height stops the page jumping when it arrives.
 */
const Chart = dynamic(() => import("@/components/charts/line-chart-inner"), {
  ssr: false,
  loading: () => <Skeleton className="h-64 w-full rounded-lg" aria-hidden />,
});

/** A chart plus its text alternative. The picture is hidden from assistive technology; the table says the same. */
export function LineChart(props: LineChartProps) {
  const f = chartFormatters(props.unit, props.xStyle);
  const columns: DataColumn[] = props.series.map((s) => ({ key: s.key, label: s.label }));
  const rows: DataRow[] = props.points.map((p) => ({
    label: f.x(p.x),
    values: Object.fromEntries(props.series.map((s) => [s.key, p.values[s.key] == null ? "" : f.value(p.values[s.key] as number)])),
  }));
  const caption = props.reference ? `${props.label}. ${props.reference.label}: ${f.value(props.reference.value)}.` : props.label;

  return (
    <figure className="m-0" aria-label={props.label} data-chart data-points={props.points.length}>
      <div className="h-64 w-full [&_*]:outline-none" aria-hidden>
        <Chart {...props} />
      </div>
      <ChartLegend series={props.series} reference={props.reference ?? null} />
      <ChartDataTable caption={caption} rowHeader={props.rowHeader ?? "Date"} columns={columns} rows={rows} />
    </figure>
  );
}

function ChartLegend({ series, reference }: { series: ChartSeries[]; reference: { label: string } | null }) {
  return (
    <ul className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground" aria-hidden>
      {series.map((s) => (
        <li key={s.key} className="flex items-center gap-1.5">
          <span className="size-2 rounded-full" style={{ background: s.color }} />
          {s.label}
        </li>
      ))}
      {reference && (
        <li className="flex items-center gap-1.5">
          <span className="w-4 border-t border-dashed border-muted-foreground" />
          {reference.label}
        </li>
      )}
    </ul>
  );
}
