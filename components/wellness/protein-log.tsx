"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { errorMessage, Field, fieldErrors, Notice } from "@/components/auth/ui";
import { useUndo } from "@/components/wellness/use-undo";
import { Button } from "@/components/ui/button";
import { apiDelete, apiPost } from "@/lib/client-api";
import { formatGrams } from "@/lib/format";
import type { ProteinAdded, ProteinSuggestion } from "@/lib/wellness-types";

/**
 * Logging protein on its page: one-tap chips from the person's own past labels, or grams with an optional label, with
 * Undo. There is no food database; the chips are only what this person typed before.
 */
export function ProteinLog({ date, isToday, suggestions }: { date: string; isToday: boolean; suggestions: ProteinSuggestion[] }) {
  const router = useRouter();
  const { offer, show, clear } = useUndo();
  const [grams, setGrams] = useState("");
  const [label, setLabel] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const attempt = useRef<{ key: string; id: string } | null>(null);

  async function add(g: number, l: string | null) {
    setPending(true);
    setError(null);
    setErrors({});
    try {
      const key = `${g}|${l ?? ""}`;
      if (!attempt.current || attempt.current.key !== key) attempt.current = { key, id: crypto.randomUUID() };
      const added = await apiPost<ProteinAdded>("/api/v1/protein-entries", { grams: g, label: l, id: attempt.current.id, ...(isToday ? {} : { date }) });
      attempt.current = null;
      setGrams("");
      setLabel("");
      show({ message: `Added ${formatGrams(g)}${l ? ` · ${l}` : ""}`, undo: async () => { await apiDelete(`/api/v1/protein-entries/${added.entry.id}`); } });
      router.refresh();
    } catch (e) {
      setErrors(fieldErrors(e));
      setError(errorMessage(e));
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

  async function undo() {
    if (!offer) return;
    setPending(true);
    try {
      await offer.undo();
      clear();
      router.refresh();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {suggestions.length > 0 && (
        <div className="flex flex-col gap-2" data-protein-chips>
          <span className="label">Your usual</span>
          <div className="flex flex-wrap gap-2">
            {suggestions.map((s) => (
              <Button key={s.label} type="button" variant="outline" className="h-11 max-w-full px-3.5 text-sm" disabled={pending} onClick={() => void add(s.grams, s.label)}>
                <span className="min-w-0 truncate">{s.label}</span>
                <span className="num text-muted-foreground">{formatGrams(s.grams)}</span>
              </Button>
            ))}
          </div>
        </div>
      )}
      <form onSubmit={addTyped} noValidate className="flex flex-col gap-3" aria-label="Protein entry">
        <div className="grid grid-cols-[1fr_1.6fr] gap-3">
          <Field label="Grams" name="grams" inputMode="numeric" autoComplete="off" placeholder="e.g. 25" value={grams} error={errors.grams} onChange={(e) => setGrams(e.target.value)} />
          <Field label="Label (optional)" name="label" maxLength={60} autoComplete="off" placeholder="e.g. Whey shake" value={label} error={errors.label} onChange={(e) => setLabel(e.target.value)} />
        </div>
        <Button type="submit" size="lg" className="h-12 text-base font-semibold" disabled={pending}>{pending ? "Adding..." : "Add protein"}</Button>
      </form>
      {error && <Notice kind="error">{error}</Notice>}
      {offer && (
        <div className="flex min-h-11 items-center gap-3 text-sm text-muted-foreground" data-undo>
          <span role="status">{offer.message}</span>
          <Button type="button" variant="ghost" className="h-11 px-2 text-primary" disabled={pending} onClick={() => void undo()}>Undo</Button>
        </div>
      )}
    </div>
  );
}
