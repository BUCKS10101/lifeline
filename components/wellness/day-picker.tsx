"use client";

import { useRouter } from "next/navigation";

/** Choose another day to look at and log for. The day lives in the URL (?date=), so the server fetches its entries. */
export function DayPicker({ basePath, date, today, range }: { basePath: string; date: string; today: string; range: string }) {
  const router = useRouter();
  return (
    <label className="flex flex-col gap-1.5 text-sm">
      <span className="font-medium">Another day</span>
      <input
        type="date"
        name="day"
        value={date}
        max={today}
        min="2000-01-01"
        className="h-11 w-full rounded-lg border border-input bg-transparent px-3 text-base outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm dark:bg-input/30"
        onChange={(e) => {
          const next = e.target.value;
          if (!/^\d{4}-\d{2}-\d{2}$/.test(next) || next > today) return;
          router.push(next === today ? `${basePath}?range=${range}` : `${basePath}?range=${range}&date=${next}`);
        }}
      />
    </label>
  );
}
