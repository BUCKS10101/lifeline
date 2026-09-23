"use client";

import Link from "next/link";
import { useState } from "react";
import { apiPost } from "@/lib/client-api";
import { errorMessage, fieldErrors, Field, Notice, SubmitButton } from "./ui";

export function ResetPasswordForm({ token }: { token: string }) {
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPending(true);
    setError(null);
    setErrors({});
    try {
      await apiPost("/api/v1/auth/reset-password", { token, newPassword: form.get("newPassword") });
      setDone(true);
    } catch (e) {
      setErrors(fieldErrors(e));
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  if (done) {
    return (
      <div className="flex flex-col gap-3">
        <Notice kind="success">Password updated. You have been logged out everywhere.</Notice>
        <Link href="/login" className="text-sm underline">Log in</Link>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      {error && <Notice kind="error">{error}</Notice>}
      <Field
        label="New password (at least 10 characters)"
        name="newPassword"
        type="password"
        autoComplete="new-password"
        required
        minLength={10}
        maxLength={72}
        error={errors.newPassword}
      />
      <SubmitButton pending={pending}>Update password</SubmitButton>
    </form>
  );
}
