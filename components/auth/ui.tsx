import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { ApiRequestError } from "@/lib/client-api";

export function AuthCard({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <main className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center gap-6 px-4 py-16">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {subtitle && <p className="text-sm text-muted-foreground">{subtitle}</p>}
      </div>
      {children}
    </main>
  );
}

const controlClass =
  "h-9 w-full rounded-lg border border-input bg-transparent px-3 text-sm outline-none transition-colors " +
  "focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50 dark:bg-input/30";

function FieldShell({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-1.5 text-sm">
      <span className="font-medium">{label}</span>
      {children}
      {error && <span className="text-destructive">{error}</span>}
    </label>
  );
}

export function Field(props: React.InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string }) {
  const { label, error, ...input } = props;
  return (
    <FieldShell label={label} error={error}>
      <input {...input} aria-invalid={error ? true : undefined} className={controlClass} />
    </FieldShell>
  );
}

export function SelectField(
  props: React.SelectHTMLAttributes<HTMLSelectElement> & { label: string; error?: string },
) {
  const { label, error, children, ...select } = props;
  return (
    <FieldShell label={label} error={error}>
      <select {...select} aria-invalid={error ? true : undefined} className={controlClass}>
        {children}
      </select>
    </FieldShell>
  );
}

export function SubmitButton({ pending, children }: { pending: boolean; children: ReactNode }) {
  return (
    <Button type="submit" size="lg" disabled={pending}>
      {pending ? "Please wait..." : children}
    </Button>
  );
}

export function Notice({ kind, children }: { kind: "error" | "success"; children: ReactNode }) {
  const tone =
    kind === "error"
      ? "border-destructive/40 bg-destructive/10 text-destructive"
      : "border-emerald-500/40 bg-emerald-500/10 text-emerald-400";
  return (
    <p role={kind === "error" ? "alert" : "status"} className={`rounded-lg border px-3 py-2 text-sm ${tone}`}>
      {children}
    </p>
  );
}

/** Maps backend validation violations to per-field messages. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiRequestError)) return {};
  return Object.fromEntries(error.violations.map((v) => [v.field, v.message]));
}

export function errorMessage(error: unknown): string {
  return error instanceof ApiRequestError ? error.message : "Could not reach the server";
}

export function TextareaField(
  props: React.TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; error?: string },
) {
  const { label, error, ...textarea } = props;
  return (
    <FieldShell label={label} error={error}>
      <textarea
        {...textarea}
        aria-invalid={error ? true : undefined}
        className={`${controlClass} min-h-24 py-2 text-base`}
      />
    </FieldShell>
  );
}
