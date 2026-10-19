"use client";

import { useEffect, useState } from "react";

/**
 * Rendered only while a form is waiting on the server. If it takes more than a few seconds, say why: on the free
 * hosting plan the backend sleeps when idle and needs a minute or two to wake up. Unmounting resets it.
 */
export function PendingHint() {
  const [slow, setSlow] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setSlow(true), 6000);
    return () => clearTimeout(timer);
  }, []);

  if (!slow) return null;
  return (
    <p role="status" className="text-sm text-muted-foreground">
      The server was asleep and is waking up. This can take a minute or two, so please keep this page open.
    </p>
  );
}
