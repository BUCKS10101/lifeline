"use client";

import { useState } from "react";
import { errorMessage, Field, fieldErrors, Notice } from "@/components/auth/ui";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";

/** Other ways to log a drink: +500 ml and a custom amount in millilitres. (+250 ml is the button on the Today line.) */
export function WaterSheet({ trigger, children, onAdd }: {
  trigger: React.ReactElement;
  children: React.ReactNode;
  /** Adds the amount. Rejects with an error the sheet shows; resolves when it is saved. */
  onAdd: (ml: number) => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [custom, setCustom] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function onOpenChange(next: boolean) {
    setOpen(next);
    if (next) {
      setCustom("");
      setErrors({});
      setError(null);
    }
  }

  async function add(ml: number) {
    setPending(true);
    setError(null);
    setErrors({});
    try {
      await onAdd(ml);
      setOpen(false);
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
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

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetTrigger render={trigger}>{children}</SheetTrigger>
      <SheetContent side="bottom" className="max-h-[90vh] gap-0 p-0 sm:mx-auto sm:max-w-lg">
        <SheetHeader className="gap-1 border-b p-4">
          <SheetTitle>Log water</SheetTitle>
          <SheetDescription>Add a drink in millilitres.</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-4 p-4">
          <Button type="button" variant="outline" className="h-14 text-base font-semibold" disabled={pending} onClick={() => void add(500)}>+500 ml</Button>
          <form onSubmit={addCustom} noValidate className="flex items-end gap-2" aria-label="Custom amount">
            <div className="min-w-0 flex-1">
              <Field label="Other amount (ml)" name="amountMl" inputMode="numeric" autoComplete="off" placeholder="e.g. 330" value={custom}
                error={errors.amountMl} onChange={(e) => setCustom(e.target.value)} />
            </div>
            <Button type="submit" className="h-11 px-5" disabled={pending}>Add</Button>
          </form>
          {error && <Notice kind="error">{error}</Notice>}
        </div>
      </SheetContent>
    </Sheet>
  );
}
