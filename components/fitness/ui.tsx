import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/** A quiet section heading: small, sentence case, with an optional action on the right. */
export function SectionHeading({ id, children, action }: { id?: string; children: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex min-h-6 items-baseline justify-between gap-3">
      <h2 id={id} className="text-sm font-medium text-foreground">{children}</h2>
      {action && <div className="text-sm text-muted-foreground">{action}</div>}
    </div>
  );
}

/** A pulsing-free "live" marker for the workout that is currently open. */
export function LiveDot() {
  return <span aria-hidden className="size-1.5 rounded-full bg-primary ring-4 ring-primary/20" />;
}

/** A hairline-ruled group of rows (history, exercises, templates). */
export function RowList({ children, className }: { children: ReactNode; className?: string }) {
  return <ul className={cn("divide-y overflow-hidden rounded-lg border bg-card", className)}>{children}</ul>;
}

/** Tiles separated by 1px rules instead of each being its own card. */
export function StatGrid({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("grid gap-px overflow-hidden rounded-lg border bg-border", className)}>{children}</div>;
}

/** A plain panel: surface, hairline border, small radius. */
export function Panel({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("rounded-lg border bg-card", className)}>{children}</div>;
}
