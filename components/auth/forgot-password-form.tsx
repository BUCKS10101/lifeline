"use client";

import Link from "next/link";
import { useState } from "react";
import { apiPost } from "@/lib/client-api";
import { errorMessage, Field, Notice, SubmitButton } from "./ui";

export function ForgotPasswordForm() {
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPending(true);
    setError(null);
    try {
      await apiPost("/api/v1/auth/forgot-password", { email: form.get("email") });
      setDone(true);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  if (done) {
    return <Notice kind="success">If an account exists for that email, a reset link is on its way.</Notice>;
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      {error && <Notice kind="error">{error}</Notice>}
      <Field label="Email" name="email" type="email" autoComplete="email" required />
      <SubmitButton pending={pending}>Send reset link</SubmitButton>
      <Link href="/login" className="text-sm text-zinc-600 underline dark:text-zinc-400">Back to log in</Link>
    </form>
  );
}
