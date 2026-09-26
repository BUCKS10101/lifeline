export type DataColumn = { key: string; label: string };
export type DataRow = { label: string; values: Record<string, string> };

/**
 * The text alternative to a chart: the same numbers as a real table, hidden visually but read by screen readers.
 * Every chart must render one, built from the same data as the chart itself.
 */
export function ChartDataTable({ caption, rowHeader, columns, rows }: {
  caption: string;
  rowHeader: string;
  columns: DataColumn[];
  rows: DataRow[];
}) {
  return (
    // sr-only goes on a wrapper: a <table> ignores the 1px width and clipping, and its caption would widen the page.
    <div className="sr-only">
    <table data-chart-table>
      <caption>{caption}</caption>
      <thead>
        <tr>
          <th scope="col">{rowHeader}</th>
          {columns.map((c) => <th key={c.key} scope="col">{c.label}</th>)}
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.label}>
            <th scope="row">{r.label}</th>
            {columns.map((c) => <td key={c.key}>{r.values[c.key] ?? ""}</td>)}
          </tr>
        ))}
      </tbody>
    </table>
    </div>
  );
}
