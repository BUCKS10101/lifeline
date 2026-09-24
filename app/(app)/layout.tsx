import { redirect } from "next/navigation";
import { Sidebar } from "@/components/shell/sidebar";
import { TopBar } from "@/components/shell/top-bar";
import { getSession } from "@/lib/backend";
import { formatToday } from "@/lib/timezones";

/**
 * The authenticated shell. It reads the user to render the chrome and redirects logged-out visitors
 * early, which gives a real 307 on a full page load (a redirect inside a streamed page cannot).
 * This is not the security check: each page still calls requireUser(), because layouts do not
 * re-render on client-side navigation, and the backend authorizes every API call itself.
 */
export default async function AppLayout({ children }: LayoutProps<"/">) {
  const session = await getSession();
  if (session.status === "unauthenticated") redirect("/login");
  const user = session.status === "ok" ? session.user : null;

  return (
    <div className="flex min-h-screen">
      <Sidebar user={user} />
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar user={user} dateLabel={user ? formatToday(user.timezone) : null} />
        <main className="flex-1 px-4 py-6 md:px-6 md:py-8">{children}</main>
      </div>
    </div>
  );
}
