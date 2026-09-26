import Link from "next/link";
import { PrBadges } from "@/components/fitness/pr-badges";
import { RowList } from "@/components/fitness/ui";
import type { PersonalRecordEvent, PersonalRecordType } from "@/lib/fitness-types";
import { formatDate, formatKg, formatSet } from "@/lib/format";

export type PrItem = { exercise?: { id: string; name: string }; event: PersonalRecordEvent };

type Row = { key: string; exercise?: { id: string; name: string }; event: PersonalRecordEvent; types: PersonalRecordType[] };

/** One set can be several records (a new best weight that is also a new best 1RM); show it once with all its badges. */
function group(items: PrItem[]): Row[] {
  const rows: Row[] = [];
  for (const { exercise, event } of items) {
    const existing = rows.find((r) => r.event.setId === event.setId);
    if (existing) existing.types.push(event.type);
    else rows.push({ key: event.setId, exercise, event, types: [event.type] });
  }
  return rows;
}

/** Personal records, newest first. With an exercise on each item the exercise name links to its page. */
export function PrEventList({ items }: { items: PrItem[] }) {
  return (
    <RowList>
      {group(items).map((r) => (
        <li key={r.key} className="flex min-h-16 flex-col justify-center gap-1 px-4 py-3" data-pr-row>
          <span className="flex items-baseline justify-between gap-3">
            {r.exercise ? (
              <Link href={`/fitness/exercises/${r.exercise.id}`} className="-my-2.5 min-w-0 py-2.5 font-semibold wrap-anywhere hover:underline">{r.exercise.name}</Link>
            ) : (
              <span className="num font-semibold">{formatSet(r.event.weightKg, r.event.reps)}</span>
            )}
            <span className="num shrink-0 text-sm text-muted-foreground">{formatDate(r.event.performedOn)}</span>
          </span>
          <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
            {r.exercise && <span className="num text-sm text-muted-foreground">{formatSet(r.event.weightKg, r.event.reps)}</span>}
            <span className="num text-sm text-muted-foreground">est. 1RM {formatKg(r.event.estimated1rmKg)}</span>
            <PrBadges records={r.types} />
          </span>
        </li>
      ))}
    </RowList>
  );
}
