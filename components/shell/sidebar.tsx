import { Brand } from "./brand";
import { NavList } from "./nav-list";
import { UserMenu, type ShellUser } from "./user-menu";

/** Fixed navigation for md and wider screens. Smaller screens use the drawer in the top bar. */
export function Sidebar({ user }: { user: ShellUser | null }) {
  return (
    <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col gap-6 border-r bg-card/40 p-4 md:flex">
      <div className="px-3 pt-1">
        <Brand />
      </div>
      <NavList />
      {user && <UserMenu user={user} />}
    </aside>
  );
}
