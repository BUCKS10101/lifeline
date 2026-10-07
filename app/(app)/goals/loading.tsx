import { Skeleton } from "@/components/ui/skeleton";

export default function GoalsLoading() {
  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6" aria-busy="true" aria-label="Loading">
      <Skeleton className="h-9 w-32" />
      <div className="flex flex-col divide-y overflow-hidden rounded-lg border">
        {[0, 1, 2].map((i) => <Skeleton key={i} className="h-16 rounded-none" />)}
      </div>
    </div>
  );
}
