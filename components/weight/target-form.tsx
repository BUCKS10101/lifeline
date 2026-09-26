"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage, Field, fieldErrors, Notice } from "@/components/auth/ui";
import { ConfirmButton } from "@/components/fitness/confirm-button";
import { Button } from "@/components/ui/button";
import { apiDelete, apiPut } from "@/lib/client-api";
import { formatNumber } from "@/lib/format";
import { parseWeight } from "@/lib/weight-input";

/** Sets, changes or clears the single target weight. Setting a different value starts a new goal from today. */
export function TargetForm({ target }: { target: number | null }) {
  const router = useRouter();
  const [value, setValue] = useState(target === null ? "" : formatNumber(target));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const parsed = parseWeight(value);
    if (!parsed.ok) {
      setErrors({ targetWeightKg: parsed.error });
      return;
    }
    setErrors({});
    setPending(true);
    try {
      await apiPut("/api/v1/weight/target", { targetWeightKg: parsed.kg });
      router.refresh();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setPending(false);
    }
  }

  async function clear() {
    setError(null);
    try {
      await apiDelete("/api/v1/weight/target");
      setValue("");
      router.refresh();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <form onSubmit={save} className="flex flex-col gap-3" noValidate aria-label="Target weight">
      <Field label="Target weight (kg)" inputMode="decimal" autoComplete="off" placeholder="0.0" value={value} error={errors.targetWeightKg}
        onChange={(e) => setValue(e.target.value)} />
      <div className="flex gap-2">
        <Button type="submit" variant="outline" className="h-12 flex-1" disabled={pending}>{target === null ? "Set target" : "Update target"}</Button>
        {target !== null && (
          <ConfirmButton label="Clear" confirmLabel="Clear" ariaLabel="Clear target weight" onConfirm={() => void clear()} />
        )}
      </div>
      {error && <Notice kind="error">{error}</Notice>}
    </form>
  );
}
