import { Skeleton } from "@/components/ui/skeleton";

export default function TasksLoading() {
  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-4" aria-busy="true" aria-label="Loading">
      <Skeleton className="h-9 w-32" />
      <Skeleton className="h-13 rounded-lg" />
      <Skeleton className="h-12 rounded-lg" />
      <div className="flex flex-col divide-y overflow-hidden rounded-lg border">
        {[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-14 rounded-none" />)}
      </div>
    </div>
  );
}
