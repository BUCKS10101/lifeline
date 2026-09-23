"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { apiPost, resetCsrf } from "@/lib/client-api";

export function LogoutButton() {
  const router = useRouter();
  const [pending, setPending] = useState(false);

  async function logout() {
    setPending(true);
    try {
      await apiPost("/api/v1/auth/logout");
    } finally {
      resetCsrf();
      router.push("/login");
      router.refresh();
    }
  }

  return (
    <button
      onClick={logout}
      disabled={pending}
      className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm disabled:opacity-50 dark:border-zinc-700"
    >
      Log out
    </button>
  );
}
