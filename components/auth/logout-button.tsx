"use client";

import { LogOut } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useLogout } from "./use-logout";

export function LogoutButton({ className }: { className?: string }) {
  const { logout, pending } = useLogout();
  return (
    <Button variant="outline" onClick={logout} disabled={pending} className={className}>
      <LogOut aria-hidden />
      Log out
    </Button>
  );
}
