"use client";

import { useState } from "react";
import { errorMessage, Field, fieldErrors, Notice } from "@/components/auth/ui";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { formatGrams } from "@/lib/format";
import type { ProteinSuggestion } from "@/lib/wellness-types";

/**
 * Other ways to log protein: one-tap chips from the person's own past labels, or grams with an optional label. There is
 * no food database; the chips are only what this person typed before.
 */
export function ProteinSheet({ trigger, children, suggestions, onAdd }: {
  trigger: React.ReactElement;
  children: React.ReactNode;
  suggestions: ProteinSuggestion[];
  /** Adds the entry. Rejects with an error the sheet shows; resolves when it is saved. */
  onAdd: (grams: number, label: string | null) => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [grams, setGrams] = useState("");
  const [label, setLabel] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function onOpenChange(next: boolean) {
    setOpen(next);
    if (next) {
      setGrams("");
      setLabel("");
      setErrors({});
      setError(null);
    }
  }

  async function add(g: number, l: string | null) {
    setPending(true);
    setError(null);
    setErrors({});
    try {
      await onAdd(g, l);
      setOpen(false);
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setPending(false);
    }
  }

  function addTyped(e: React.FormEvent) {
    e.preventDefault();
    const text = grams.trim();
    if (!/^\d{1,3}$/.test(text) || Number(text) < 1 || Number(text) > 500) {
      setErrors({ grams: "1 to 500 g" });
      return;
    }
    void add(Number(text), label.trim() === "" ? null : label.trim());
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetTrigger render={trigger}>{children}</SheetTrigger>
      <SheetContent side="bottom" className="max-h-[90vh] gap-0 overflow-y-auto p-0 sm:mx-auto sm:max-w-lg">
        <SheetHeader className="gap-1 border-b p-4">
          <SheetTitle>Log protein</SheetTitle>
          <SheetDescription>Add grams, with an optional label to find it again.</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-4 p-4">
          {suggestions.length > 0 && (
            <div className="flex flex-col gap-2" data-protein-chips>
              <span className="label">Your usual</span>
              <div className="flex flex-wrap gap-2">
                {suggestions.map((s) => (
                  <Button key={s.label} type="button" variant="outline" className="h-11 max-w-full px-3.5 text-sm" disabled={pending}
                    onClick={() => void add(s.grams, s.label)}>
                    <span className="min-w-0 truncate">{s.label}</span>
                    <span className="num text-muted-foreground">{formatGrams(s.grams)}</span>
                  </Button>
                ))}
              </div>
            </div>
          )}
          <form onSubmit={addTyped} noValidate className="flex flex-col gap-3" aria-label="Protein entry">
            <div className="grid grid-cols-[1fr_1.6fr] gap-3">
              <Field label="Grams" name="grams" inputMode="numeric" autoComplete="off" placeholder="e.g. 25" value={grams}
                error={errors.grams} onChange={(e) => setGrams(e.target.value)} />
              <Field label="Label (optional)" name="label" maxLength={60} autoComplete="off" placeholder="e.g. Whey shake" value={label}
                error={errors.label} onChange={(e) => setLabel(e.target.value)} />
            </div>
            {error && <Notice kind="error">{error}</Notice>}
            <Button type="submit" size="lg" className="h-12 text-base font-semibold" disabled={pending}>{pending ? "Adding..." : "Add protein"}</Button>
          </form>
        </div>
      </SheetContent>
    </Sheet>
  );
}
