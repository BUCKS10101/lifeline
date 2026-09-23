"use client";

import Link from "next/link";
import { useState } from "react";
import { apiPost } from "@/lib/client-api";
import { errorMessage, Notice, SubmitButton } from "./ui";

/** Verification needs an explicit click so link scanners that prefetch URLs cannot consume the token. */
export function VerifyEmailPanel({ token }: { token: string }) {
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await apiPost("/api/v1/auth/verify-email", { token });
      setDone(true);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  if (done) {
    return (
      <div className="flex flex-col gap-3">
        <Notice kind="success">Email verified.</Notice>
        <Link href="/login" className="text-sm underline">Log in</Link>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      {error && <Notice kind="error">{error}</Notice>}
      <SubmitButton pending={pending}>Verify my email</SubmitButton>
    </form>
  );
}
