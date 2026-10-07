"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { apiPost } from "@/lib/client-api";

/** The dashboard's one allowed write action: marking today's hair wash, without leaving the dashboard. */
export function MarkHairWashButton({ alreadyMarkedToday }: { alreadyMarkedToday: boolean }) {
  const router = useRouter();
  const [pending, setPending] = useState(false);

  async function mark() {
    setPending(true);
    try {
      await apiPost("/api/v1/personal-care/hair-wash", {});
      router.refresh();
    } finally {
      setPending(false);
    }
  }

  return (
    <Button type="button" variant="outline" className="h-11 px-4" disabled={pending || alreadyMarkedToday} onClick={() => void mark()}>
      {alreadyMarkedToday ? "Marked today" : "Mark today"}
    </Button>
  );
}
