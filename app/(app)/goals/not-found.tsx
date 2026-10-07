import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";

/** Shown inside the app shell when a goal id does not exist (or belongs to someone else). */
export default function GoalsNotFound() {
  return (
    <div className="mx-auto flex max-w-md flex-col items-start gap-5 py-20">
      <div className="flex flex-col gap-1.5">
        <h1 className="text-2xl font-semibold tracking-tight">Not found</h1>
        <p className="text-muted-foreground">That goal does not exist.</p>
      </div>
      <Link href="/goals" className={buttonVariants({ className: "h-12 px-5 text-base font-semibold" })}>Back to Goals</Link>
    </div>
  );
}
