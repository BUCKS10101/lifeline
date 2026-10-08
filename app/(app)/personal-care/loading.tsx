import { Skeleton } from "@/components/ui/skeleton";

export default function PersonalCareLoading() {
  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6" aria-busy="true" aria-label="Loading">
      <Skeleton className="h-9 w-40" />
      <Skeleton className="h-28 rounded-lg" />
      <Skeleton className="h-72 rounded-lg" />
    </div>
  );
}
