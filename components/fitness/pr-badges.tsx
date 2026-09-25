import { Trophy } from "lucide-react";
import type { PersonalRecordType } from "@/lib/fitness-types";
import { RECORD_LABEL } from "@/lib/format";

/** Personal-record badges for a set. Amber is reserved for records. Renders nothing when the set is not one. */
export function PrBadges({ records }: { records: PersonalRecordType[] }) {
  if (records.length === 0) return null;
  return (
    <span className="flex flex-wrap gap-1">
      {records.map((type) => (
        <span key={type} className="inline-flex h-5 items-center gap-1 whitespace-nowrap rounded bg-record/15 px-1.5 text-xs font-medium text-record">
          <Trophy className="size-3" aria-hidden />
          {RECORD_LABEL[type]}
        </span>
      ))}
    </span>
  );
}
