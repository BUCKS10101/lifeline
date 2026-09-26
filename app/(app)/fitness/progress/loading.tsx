import { Skeleton } from "@/components/ui/skeleton";

export default function ProgressLoading() {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-8" aria-busy="true" aria-label="Loading">
      <Skeleton className="h-9 w-40" />
      <Skeleton className="h-13 rounded-lg" />
      <Skeleton className="h-64 rounded-lg" />
      <Skeleton className="h-64 rounded-lg" />
      <div className="flex flex-col divide-y overflow-hidden rounded-lg border">
        {[0, 1, 2].map((i) => <Skeleton key={i} className="h-16 rounded-none" />)}
      </div>
    </div>
  );
}
