"use client";

import { WEEKDAYS } from "@/lib/weekdays";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/** Seven small toggles for which days a habit applies to, plus an "Every day" shortcut. At least one must stay selected. */
export function WeekdayToggles({ value, onChange, idPrefix }: { value: number[]; onChange: (next: number[]) => void; idPrefix: string }) {
  const set = new Set(value);
  const everyDay = value.length === 7;

  function toggle(iso: number) {
    const next = set.has(iso) ? value.filter((d) => d !== iso) : [...value, iso];
    if (next.length > 0) onChange(next);
  }

  return (
    <fieldset className="flex flex-col gap-1.5">
      <legend className="mb-1.5 text-sm font-medium">Days</legend>
      <div role="group" aria-label="Days of the week" className="grid grid-cols-7 gap-1.5">
        {WEEKDAYS.map((d) => (
          <button
            key={d.iso}
            type="button"
            id={`${idPrefix}-day-${d.iso}`}
            role="checkbox"
            aria-checked={set.has(d.iso)}
            aria-label={d.label}
            onClick={() => toggle(d.iso)}
            className={cn(
              "flex h-11 items-center justify-center rounded-lg border text-sm font-medium transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
              set.has(d.iso) ? "border-primary bg-primary/15 text-primary" : "text-muted-foreground hover:bg-muted hover:text-foreground",
            )}
          >
            {d.short}
          </button>
        ))}
      </div>
      <Button type="button" variant="ghost" className="h-11 self-start px-3 text-muted-foreground"
        onClick={() => onChange(WEEKDAYS.map((d) => d.iso))} disabled={everyDay}>
        Every day
      </Button>
    </fieldset>
  );
}
