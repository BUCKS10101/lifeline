import { redirect } from "next/navigation";
import { Suspense } from "react";
import { LogoutButton } from "@/components/auth/logout-button";
import { BackendStatus } from "@/components/backend-status";
import { getCurrentUser } from "@/lib/backend";

export const metadata = { title: "Dashboard | Personal OS" };

export default async function DashboardPage() {
  // The backend enforces authorization on every API call; this check only decides what to render.
  const user = await getCurrentUser();
  if (!user) redirect("/login");

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-8 px-6 py-16">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Hello, {user.displayName}</h1>
          <p className="text-sm text-zinc-600 dark:text-zinc-400">{user.email}</p>
        </div>
        <LogoutButton />
      </header>
      <p className="text-zinc-600 dark:text-zinc-400">
        You are signed in. The real dashboard arrives in Phase 2.
      </p>
      <Suspense fallback={<p className="text-zinc-500">Checking backend...</p>}>
        <BackendStatus />
      </Suspense>
    </main>
  );
}
