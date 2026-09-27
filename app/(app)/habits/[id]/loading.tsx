import { Skeleton } from "@/components/ui/skeleton";

export default function HabitDetailLoading() {
  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6" aria-busy="true" aria-label="Loading">
      <Skeleton className="h-9 w-40" />
      <div className="grid grid-cols-2 gap-px">
        <Skeleton className="h-20 rounded-none first:rounded-l-lg" />
        <Skeleton className="h-20 rounded-none last:rounded-r-lg" />
      </div>
      <Skeleton className="h-48 rounded-lg" />
      <Skeleton className="h-64 rounded-lg" />
    </div>
  );
}
