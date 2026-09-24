import {
  CalendarDays,
  Code,
  Dumbbell,
  Flame,
  LayoutDashboard,
  ListChecks,
  Scale,
  Settings,
  Target,
  type LucideIcon,
} from "lucide-react";

export type NavItem = {
  label: string;
  icon: LucideIcon;
  /** Present for pages that exist. Items without an href are shown disabled with a "Soon" badge. */
  href?: string;
};

/** Single source for the sidebar and the mobile drawer. Only add an href once the page really exists. */
export const MAIN_NAV: NavItem[] = [
  { label: "Dashboard", icon: LayoutDashboard, href: "/dashboard" },
  { label: "Fitness", icon: Dumbbell },
  { label: "Weight", icon: Scale },
  { label: "Tasks", icon: ListChecks },
  { label: "Habits", icon: Flame },
  { label: "Goals", icon: Target },
  { label: "Calendar", icon: CalendarDays },
  { label: "DSA", icon: Code },
];

export const FOOTER_NAV: NavItem[] = [{ label: "Settings", icon: Settings, href: "/settings" }];

export function isActive(pathname: string, href: string): boolean {
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function titleForPath(pathname: string): string {
  const item = [...MAIN_NAV, ...FOOTER_NAV].find((i) => i.href && isActive(pathname, i.href));
  return item?.label ?? "Personal OS";
}
