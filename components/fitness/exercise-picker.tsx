"use client";

import { useRef, useState } from "react";
import { Notice } from "@/components/auth/ui";
import { Input } from "@/components/ui/input";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { apiGet } from "@/lib/client-api";
import type { Exercise, Paged } from "@/lib/fitness-types";
import { MUSCLE_LABEL } from "@/lib/format";

/** A bottom sheet for choosing an exercise from the catalogue (built-in and custom), with search. */
export function ExercisePicker({ trigger, children, onSelect, excludeIds = [] }: {
  /** The button element that opens the sheet; its label goes in {@code children}. */
  trigger: React.ReactElement;
  children: React.ReactNode;
  onSelect: (exercise: Exercise) => void | Promise<void>;
  excludeIds?: string[];
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [items, setItems] = useState<Exercise[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  // True from the moment you type until the matching results arrive. The list is about to change, so taps on
  // it are ignored: otherwise you could pick an item that shifts away just as your finger lands.
  const [searching, setSearching] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const latest = useRef(0);

  async function load(q: string) {
    const ticket = ++latest.current; // ignore answers that arrive after a newer search
    try {
      const page = await apiGet<Paged<Exercise>>(`/api/v1/exercises?size=100&q=${encodeURIComponent(q)}`);
      if (ticket === latest.current) {
        setItems(page?.items ?? []);
        setError(null);
        setSearching(false);
      }
    } catch {
      if (ticket === latest.current) {
        setError("Could not load exercises");
        setSearching(false);
      }
    }
  }

  function onOpenChange(next: boolean) {
    setOpen(next);
    if (next) {
      setQuery("");
      setItems(null);
      void load("");
    }
  }

  function onSearch(value: string) {
    setQuery(value);
    setSearching(true);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => void load(value), 250);
  }

  async function choose(exercise: Exercise) {
    await onSelect(exercise);
    setOpen(false);
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetTrigger render={trigger}>{children}</SheetTrigger>
      <SheetContent side="bottom" className="max-h-[85vh] gap-0 p-0 sm:mx-auto sm:max-w-lg">
        <SheetHeader className="gap-1 border-b p-4">
          <SheetTitle>Choose an exercise</SheetTitle>
          <SheetDescription className="sr-only">Search the exercise catalogue and pick one to add</SheetDescription>
          <Input
            className="mt-2 h-11"
            type="search"
            placeholder="Search exercises"
            aria-label="Search exercises"
            value={query}
            onChange={(e) => onSearch(e.target.value)}
          />
        </SheetHeader>
        <div className="flex-1 overflow-y-auto p-2">
          {error && <div className="p-2"><Notice kind="error">{error}</Notice></div>}
          {items === null && !error && <p className="p-4 text-sm text-muted-foreground">Loading...</p>}
          {items?.length === 0 && (
            <p className="p-4 text-sm text-muted-foreground">
              No exercises match. You can create your own on the Exercises page.
            </p>
          )}
          <ul className={`flex flex-col transition-opacity ${searching ? "pointer-events-none opacity-60" : ""}`} aria-busy={searching}>
            {items?.map((exercise) => {
              const already = excludeIds.includes(exercise.id);
              return (
                <li key={exercise.id}>
                  <button
                    type="button"
                    disabled={already}
                    onClick={() => void choose(exercise)}
                    className="flex min-h-12 w-full items-center justify-between gap-3 rounded-lg px-3 py-2 text-left outline-none transition-colors hover:bg-accent/60 focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50"
                  >
                    <span className="flex min-w-0 flex-col">
                      <span className="truncate font-medium">{exercise.name}</span>
                      <span className="text-xs text-muted-foreground">
                        {MUSCLE_LABEL[exercise.primaryMuscleGroup]}
                        {exercise.equipment ? ` · ${exercise.equipment.toLowerCase()}` : ""}
                        {!exercise.builtIn ? " · custom" : ""}
                      </span>
                    </span>
                    {already && <span className="shrink-0 text-xs text-muted-foreground">Added</span>}
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      </SheetContent>
    </Sheet>
  );
}
