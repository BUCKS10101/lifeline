"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { errorMessage, Field, fieldErrors, Notice } from "@/components/auth/ui";
import { useUndo } from "@/components/wellness/use-undo";
import { Button } from "@/components/ui/button";
import { apiDelete, apiPost } from "@/lib/client-api";
import { formatMl } from "@/lib/format";
import type { WaterAdded } from "@/lib/wellness-types";

/**
 * Logging water on its page: the two quick amounts and a custom amount, with Undo. It logs for the day being viewed
 * ({@code date} is omitted for today, so the server decides the date in the person's timezone).
 */
export function WaterLog({ date, isToday }: { date: string; isToday: boolean }) {
  const router = useRouter();
  const { offer, show, clear } = useUndo();
  const [custom, setCustom] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  // One client-generated id per attempt: a retry after a failed request returns the same drink, never a second one.
  const attempt = useRef<{ ml: number; id: string } | null>(null);

  async function add(ml: number) {
    setPending(true);
    setError(null);
    setErrors({});
    try {
      if (!attempt.current || attempt.current.ml !== ml) attempt.current = { ml, id: crypto.randomUUID() };
      const added = await apiPost<WaterAdded>("/api/v1/water-entries", { amountMl: ml, id: attempt.current.id, ...(isToday ? {} : { date }) });
      attempt.current = null;
      setCustom("");
      show({ message: `Added ${formatMl(ml)}`, undo: async () => { await apiDelete(`/api/v1/water-entries/${added.entry.id}`); } });
      router.refresh();
    } catch (e) {
      setErrors(fieldErrors(e));
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  function addCustom(e: React.FormEvent) {
    e.preventDefault();
    const text = custom.trim();
    if (!/^\d{1,4}$/.test(text) || Number(text) < 10 || Number(text) > 5000) {
      setErrors({ amountMl: "10 to 5000 ml" });
      return;
    }
    void add(Number(text));
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
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-2 gap-3">
        <Button type="button" variant="outline" className="h-14 text-base font-semibold" disabled={pending} onClick={() => void add(250)}>+250 ml</Button>
        <Button type="button" variant="outline" className="h-14 text-base font-semibold" disabled={pending} onClick={() => void add(500)}>+500 ml</Button>
      </div>
      <form onSubmit={addCustom} noValidate className="flex items-end gap-2" aria-label="Custom amount">
        <div className="min-w-0 flex-1">
          <Field label="Other amount (ml)" name="amountMl" inputMode="numeric" autoComplete="off" placeholder="e.g. 330" value={custom}
            error={errors.amountMl} onChange={(e) => setCustom(e.target.value)} />
        </div>
        <Button type="submit" className="h-11 px-5" disabled={pending}>Add</Button>
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
