import Link from "next/link";
import { redirect } from "next/navigation";
import { getCurrentUser } from "@/lib/backend";

export default async function Home() {
  if (await getCurrentUser()) redirect("/dashboard");

  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col justify-center gap-6 px-6 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">Personal OS</h1>
      <p className="text-zinc-600 dark:text-zinc-400">
        One place for your fitness, DSA practice, tasks, habits and goals.
      </p>
      <div className="flex gap-3">
        <Link href="/login" className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white dark:bg-zinc-100 dark:text-zinc-900">
          Log in
        </Link>
        <Link href="/register" className="rounded-md border border-zinc-300 px-4 py-2 text-sm font-medium dark:border-zinc-700">
          Create account
        </Link>
      </div>
    </main>
  );
}
