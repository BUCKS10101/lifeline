import { Skeleton } from "@/components/ui/skeleton";

export default function CalendarLoading() {
  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6" aria-busy="true" aria-label="Loading">
      <Skeleton className="h-9 w-32" />
      <Skeleton className="h-72 rounded-lg" />
      <Skeleton className="h-40 rounded-lg" />
    </div>
  );
}
