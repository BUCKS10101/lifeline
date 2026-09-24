"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { apiPost, resetCsrf } from "@/lib/client-api";

export function useLogout() {
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

  return { logout, pending };
}
