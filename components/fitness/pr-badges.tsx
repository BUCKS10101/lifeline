import { Trophy } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { PersonalRecordType } from "@/lib/fitness-types";
import { RECORD_LABEL } from "@/lib/format";

/** Personal-record badges for a set. Renders nothing when the set is not a record. */
export function PrBadges({ records }: { records: PersonalRecordType[] }) {
  if (records.length === 0) return null;
  return (
    <span className="flex flex-wrap gap-1">
      {records.map((type) => (
        <Badge key={type} className="gap-1 bg-amber-500/15 text-amber-400">
          <Trophy className="size-3" aria-hidden />
          {RECORD_LABEL[type]}
        </Badge>
      ))}
    </span>
  );
}
