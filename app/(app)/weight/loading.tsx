import { Skeleton } from "@/components/ui/skeleton";

export default function WeightLoading() {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-8" aria-busy="true" aria-label="Loading">
      <Skeleton className="h-9 w-40" />
      <div className="grid grid-cols-2 gap-px md:grid-cols-4">
        {[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-[4.5rem] rounded-none first:rounded-l-lg last:rounded-r-lg" />)}
      </div>
      <Skeleton className="h-64 rounded-lg" />
      <Skeleton className="h-48 rounded-lg" />
    </div>
  );
}
