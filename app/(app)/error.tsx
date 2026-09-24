"use client";

import { Button } from "@/components/ui/button";

export default function Error({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div role="alert" className="mx-auto flex max-w-md flex-col items-center gap-4 py-24 text-center">
      <div className="flex flex-col gap-1">
        <h1 className="text-xl font-semibold">Something went wrong</h1>
        <p className="text-sm text-muted-foreground">This page failed to load. Trying again usually helps.</p>
      </div>
      <Button onClick={reset}>Try again</Button>
    </div>
  );
}
