import { Skeleton } from "@/components/ui/skeleton";

export default function FitnessLoading() {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-8" aria-busy="true" aria-label="Loading">
      <Skeleton className="h-9 w-40" />
      <Skeleton className="h-32 rounded-lg" />
      <div className="flex flex-col gap-3">
        <Skeleton className="h-4 w-24" />
        <div className="grid grid-cols-2 gap-px md:grid-cols-4">
          {[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-[4.5rem] rounded-none first:rounded-l-lg last:rounded-r-lg" />)}
        </div>
      </div>
      <div className="flex flex-col gap-3">
        <Skeleton className="h-4 w-32" />
        <div className="flex flex-col divide-y overflow-hidden rounded-lg border">
          {[0, 1, 2].map((i) => <Skeleton key={i} className="h-16 rounded-none" />)}
        </div>
      </div>
    </div>
  );
}
