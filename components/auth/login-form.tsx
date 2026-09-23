"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { apiPost } from "@/lib/client-api";
import { errorMessage, Field, Notice, SubmitButton } from "./ui";

export function LoginForm() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPending(true);
    setError(null);
    try {
      await apiPost("/api/v1/auth/login", { email: form.get("email"), password: form.get("password") });
      router.push("/dashboard");
      router.refresh();
    } catch (e) {
      setError(errorMessage(e));
      setPending(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      {error && <Notice kind="error">{error}</Notice>}
      <Field label="Email" name="email" type="email" autoComplete="email" required />
      <Field label="Password" name="password" type="password" autoComplete="current-password" required />
      <SubmitButton pending={pending}>Log in</SubmitButton>
      <div className="flex justify-between text-sm text-zinc-600 dark:text-zinc-400">
        <Link href="/forgot-password" className="underline">Forgot password?</Link>
        <Link href="/register" className="underline">Create account</Link>
      </div>
    </form>
  );
}
