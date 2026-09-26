"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Trash2 } from "lucide-react";
import { errorMessage, Notice } from "@/components/auth/ui";
import { ConfirmButton } from "@/components/fitness/confirm-button";
import { RowList } from "@/components/fitness/ui";
import { apiDelete } from "@/lib/client-api";

export type IntakeRow = { id: string; time: string; amount: string; label?: string | null };

/** One day's drinks or protein entries, newest first, each with a two-tap delete. */
export function IntakeEntries({ path, rows, noun }: { path: string; rows: IntakeRow[]; noun: string }) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);

  async function remove(id: string) {
    setError(null);
    try {
      await apiDelete(`${path}/${id}`);
      router.refresh();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <div className="flex flex-col gap-2">
      {error && <Notice kind="error">{error}</Notice>}
      <RowList>
        {rows.map((row) => (
          <li key={row.id} className="px-4" data-entry-row data-id={row.id}>
            <div className="flex min-h-14 items-center gap-2 py-2">
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="num flex flex-wrap items-baseline gap-x-3">
                  <span className="text-lg font-medium">{row.amount}</span>
                  <span className="text-sm text-muted-foreground">{row.time}</span>
                </span>
                {row.label && <span className="text-sm text-muted-foreground wrap-anywhere">{row.label}</span>}
              </div>
              <ConfirmButton ariaLabel={`Delete ${noun} of ${row.amount} at ${row.time}`} confirmLabel="Delete" onConfirm={() => void remove(row.id)}>
                <Trash2 aria-hidden />
              </ConfirmButton>
            </div>
          </li>
        ))}
      </RowList>
    </div>
  );
}
