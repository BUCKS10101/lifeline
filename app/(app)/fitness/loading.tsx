import { Skeleton } from "@/components/ui/skeleton";

export default function FitnessLoading() {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6" aria-busy="true" aria-label="Loading">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-5 w-72 max-w-full" />
      </div>
      <Skeleton className="h-28" />
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-20" />)}
      </div>
      <Skeleton className="h-20" />
      <Skeleton className="h-20" />
    </div>
  );
}
