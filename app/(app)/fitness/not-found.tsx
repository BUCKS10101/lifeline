import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";

/** Shown inside the app shell when a workout, exercise or template id does not exist (or belongs to someone else). */
export default function FitnessNotFound() {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-4 py-24 text-center">
      <div className="flex flex-col gap-1">
        <h1 className="text-xl font-semibold">Not found</h1>
        <p className="text-sm text-muted-foreground">That workout, exercise or template does not exist.</p>
      </div>
      <Link href="/fitness" className={buttonVariants()}>Back to Fitness</Link>
    </div>
  );
}
