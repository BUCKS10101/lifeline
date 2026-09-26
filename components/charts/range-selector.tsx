import Link from "next/link";
import { cn } from "@/lib/utils";

export type RangeOption = { value: string; label: string };

/**
 * A row of links, not buttons: the range lives in the URL (?range=), so the server fetches the data for it and the
 * page can be shared or reloaded. The current range is marked with aria-current.
 */
export function RangeSelector({ basePath, options, current, label = "Range", extraQuery = "" }: {
  basePath: string;
  options: RangeOption[];
  current: string;
  label?: string;
  /** Other query parameters to keep when the range changes, for example "prpage=2". */
  extraQuery?: string;
}) {
  return (
    <nav aria-label={label} className="flex gap-1 rounded-lg border bg-card p-1">
      {options.map((o) => (
        <Link
          key={o.value}
          href={`${basePath}?range=${o.value}${extraQuery ? `&${extraQuery}` : ""}`}
          aria-current={o.value === current ? "true" : undefined}
          scroll={false}
          className={cn(
            "num flex h-11 min-w-0 flex-1 items-center justify-center rounded-md px-2 text-sm transition-colors",
            o.value === current ? "bg-primary text-primary-foreground font-medium" : "text-muted-foreground hover:bg-muted hover:text-foreground",
          )}
        >
          {o.label}
        </Link>
      ))}
    </nav>
  );
}
