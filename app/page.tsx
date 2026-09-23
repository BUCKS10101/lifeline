import { Suspense } from "react";
import { BackendStatus } from "@/components/backend-status";

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col justify-center gap-6 px-6 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">Personal OS</h1>
      <p className="text-zinc-600 dark:text-zinc-400">
        Phase 0 check: frontend, Spring Boot and PostgreSQL connected.
      </p>
      <Suspense fallback={<p className="text-zinc-500">Checking backend...</p>}>
        <BackendStatus />
      </Suspense>
    </main>
  );
}
