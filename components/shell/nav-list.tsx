"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Badge } from "@/components/ui/badge";
import { FOOTER_NAV, isActive, MAIN_NAV, type NavItem } from "@/lib/nav";
import { cn } from "@/lib/utils";

const rowClass = "flex min-h-11 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium lg:min-h-9";

function NavEntry({ item, pathname, onNavigate }: { item: NavItem; pathname: string; onNavigate?: () => void }) {
  const Icon = item.icon;

  // Modules that do not exist yet are shown, but they are not links and not focusable.
  if (!item.href) {
    return (
      <li>
        <span aria-disabled="true" className={cn(rowClass, "cursor-not-allowed text-muted-foreground/60")}>
          <Icon className="size-4" aria-hidden />
          {item.label}
          <Badge variant="secondary" className="ml-auto">Soon</Badge>
        </span>
      </li>
    );
  }

  const active = isActive(pathname, item.href);
  return (
    <li>
      <Link
        href={item.href}
        onClick={onNavigate}
        aria-current={active ? "page" : undefined}
        className={cn(
          rowClass,
          "transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
          active ? "bg-accent text-accent-foreground" : "text-muted-foreground hover:bg-accent/60 hover:text-foreground",
        )}
      >
        <Icon className="size-4" aria-hidden />
        {item.label}
      </Link>
    </li>
  );
}

export function NavList({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  return (
    <nav aria-label="Main" className="flex flex-1 flex-col gap-4">
      <ul className="flex flex-col gap-1">
        {MAIN_NAV.map((item) => (
          <NavEntry key={item.label} item={item} pathname={pathname} onNavigate={onNavigate} />
        ))}
      </ul>
      <ul className="mt-auto flex flex-col gap-1 border-t pt-4">
        {FOOTER_NAV.map((item) => (
          <NavEntry key={item.label} item={item} pathname={pathname} onNavigate={onNavigate} />
        ))}
      </ul>
    </nav>
  );
}
