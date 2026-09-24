"use client";

import { Menu } from "lucide-react";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { LogoutButton } from "@/components/auth/logout-button";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { titleForPath } from "@/lib/nav";
import { Brand } from "./brand";
import { NavList } from "./nav-list";
import type { ShellUser } from "./user-menu";

export function TopBar({ user, dateLabel }: { user: ShellUser | null; dateLabel: string | null }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  return (
    <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2 border-b bg-background/80 px-4 backdrop-blur md:px-6">
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetTrigger
          render={<Button variant="ghost" size="icon" className="-ml-2 md:hidden" aria-label="Open navigation menu" />}
        >
          <Menu aria-hidden />
        </SheetTrigger>
        <SheetContent side="left" className="w-72 gap-0 p-4">
          <SheetTitle className="px-3 pt-1 text-base">
            <Brand />
          </SheetTitle>
          <SheetDescription className="sr-only">Navigate between sections of Personal OS</SheetDescription>
          <div className="mt-6 flex flex-1 flex-col gap-4 overflow-y-auto">
            <NavList onNavigate={() => setOpen(false)} />
            {user && (
              <div className="flex flex-col gap-2 border-t pt-4">
                <p className="truncate px-1 text-xs text-muted-foreground">{user.email}</p>
                <LogoutButton className="w-full" />
              </div>
            )}
          </div>
        </SheetContent>
      </Sheet>

      <span className="text-sm font-medium">{titleForPath(pathname)}</span>
      {dateLabel && <span className="ml-auto text-sm text-muted-foreground">{dateLabel}</span>}
    </header>
  );
}
