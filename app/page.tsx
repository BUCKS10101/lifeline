import Link from "next/link";
import { redirect } from "next/navigation";
import { getSession } from "@/lib/backend";

export default async function Home() {
  if ((await getSession()).status === "ok") redirect("/dashboard");

  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col justify-center gap-6 px-6 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">Personal OS</h1>
      <p className="text-muted-foreground">
        One place for your fitness, DSA practice, tasks, habits and goals.
      </p>
      <div className="flex gap-3">
        <Link href="/login" className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground">
          Log in
        </Link>
        <Link href="/register" className="rounded-md border border-input px-4 py-2 text-sm font-medium">
          Create account
        </Link>
      </div>
    </main>
  );
}
