"use client";

import { Loader2, ServerOff } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";

const RETRY_MS = 5000;
/** About three minutes of automatic retries, longer than a sleeping free-plan backend needs to wake up. */
const MAX_TRIES = 36;

/**
 * Shown when the backend cannot be reached. Deliberately not a redirect: you are not logged out. The usual cause is
 * a sleeping backend that is already waking up, so this keeps retrying by itself and swaps in the page as soon as
 * the server answers; only after a few minutes does it fall back to a manual "Try again".
 */
export function BackendUnavailable() {
  const router = useRouter();
  const [tries, setTries] = useState(0);
  const waiting = tries < MAX_TRIES;

  useEffect(() => {
    if (!waiting) return;
    const timer = setTimeout(() => {
      router.refresh();
      setTries((n) => n + 1);
    }, RETRY_MS);
    return () => clearTimeout(timer);
  }, [tries, waiting, router]);

  if (waiting) {
    return (
      <div role="status" className="mx-auto flex max-w-md flex-col items-center gap-4 py-24 text-center">
        <Loader2 className="size-10 animate-spin text-muted-foreground" aria-hidden />
        <div className="flex flex-col gap-1">
          <h1 className="text-xl font-semibold">Waking up the server</h1>
          <p className="text-sm text-muted-foreground">
            It sleeps when nobody has used it for a while and can take a minute or two to start. You are still signed
            in, and this page will load by itself.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div role="alert" className="mx-auto flex max-w-md flex-col items-center gap-4 py-24 text-center">
      <ServerOff className="size-10 text-muted-foreground" aria-hidden />
      <div className="flex flex-col gap-1">
        <h1 className="text-xl font-semibold">We can&apos;t reach the server</h1>
        <p className="text-sm text-muted-foreground">
          You are still signed in. The backend is not responding right now, so your data cannot be loaded.
        </p>
      </div>
      <Button
        onClick={() => {
          setTries(0);
          router.refresh();
        }}
      >
        Try again
      </Button>
    </div>
  );
}
