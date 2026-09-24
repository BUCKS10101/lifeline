"use client";

import { ServerOff } from "lucide-react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";

/** Shown when the backend cannot be reached. Deliberately not a redirect: you are not logged out. */
export function BackendUnavailable() {
  const router = useRouter();
  return (
    <div role="alert" className="mx-auto flex max-w-md flex-col items-center gap-4 py-24 text-center">
      <ServerOff className="size-10 text-muted-foreground" aria-hidden />
      <div className="flex flex-col gap-1">
        <h1 className="text-xl font-semibold">We can&apos;t reach the server</h1>
        <p className="text-sm text-muted-foreground">
          You are still signed in. The backend is not responding right now, so your data cannot be loaded.
        </p>
      </div>
      <Button onClick={() => router.refresh()}>Try again</Button>
    </div>
  );
}
