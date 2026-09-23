"use client";

import Link from "next/link";
import { useState } from "react";
import { apiPost } from "@/lib/client-api";
import { errorMessage, fieldErrors, Field, Notice, SubmitButton } from "./ui";

export function RegisterForm() {
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
      await apiPost("/api/v1/auth/register", {
        displayName: form.get("displayName"),
        email: form.get("email"),
        password: form.get("password"),
      });
      setDone(true);
    } catch (e) {
      setErrors(fieldErrors(e));
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  if (done) {
    return <Notice kind="success">Check your email for a verification link. If you already had an account, the email says so.</Notice>;
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      {error && <Notice kind="error">{error}</Notice>}
      <Field label="Name" name="displayName" autoComplete="name" required maxLength={100} error={errors.displayName} />
      <Field label="Email" name="email" type="email" autoComplete="email" required error={errors.email} />
      <Field
        label="Password (at least 10 characters)"
        name="password"
        type="password"
        autoComplete="new-password"
        required
        minLength={10}
        maxLength={72}
        error={errors.password}
      />
      <SubmitButton pending={pending}>Create account</SubmitButton>
      <p className="text-sm text-zinc-600 dark:text-zinc-400">
        Already registered? <Link href="/login" className="underline">Log in</Link>
      </p>
    </form>
  );
}
