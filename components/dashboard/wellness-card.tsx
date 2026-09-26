import Link from "next/link";
import { HeartPulse } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { backendGet } from "@/lib/backend";
import { formatGrams, formatMinutes, formatMl } from "@/lib/format";
import type { Today } from "@/lib/wellness-types";

/**
 * The dashboard's "Today's wellness" card: one line per visible metric and a link to log. It has no inline logging, so the
 * dashboard stays calm. With every metric hidden there is no card at all.
 */
export async function WellnessTodayCard() {
  const result = await backendGet<Today>("/api/v1/wellness/today");
  const today = result.status === "ok" ? result.data : null;
  if (today && !today.sleep && !today.water && !today.protein) return null;

  const rows: { key: string; label: string; value: string }[] = [];
  if (today?.sleep) rows.push({ key: "sleep", label: "Sleep", value: today.sleep.entry ? `${formatMinutes(today.sleep.entry.durationMinutes)}${today.sleep.goalMinutes ? ` of ${formatMinutes(today.sleep.goalMinutes)}` : ""}` : "Not logged" });
  if (today?.water) rows.push({ key: "water", label: "Water", value: `${formatMl(today.water.totalMl)}${today.water.goalMl ? ` of ${formatMl(today.water.goalMl)}` : ""}` });
  if (today?.protein) rows.push({ key: "protein", label: "Protein", value: `${formatGrams(today.protein.totalG)}${today.protein.goalG ? ` of ${formatGrams(today.protein.goalG)}` : ""}` });

  const nothingLogged = !!today && !today.sleep?.entry && !today.water?.totalMl && !today.protein?.totalG;

  return (
    <Card data-wellness-card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <HeartPulse className="size-4 text-muted-foreground" aria-hidden />
          Today&apos;s wellness
        </CardTitle>
        <CardDescription className="num">
          {!today ? "Wellness could not be loaded right now."
            : nothingLogged ? <span data-nothing-logged>Nothing logged today</span>
              : (
                <span className="flex flex-col gap-0.5">
                  {rows.map((r) => (
                    <span key={r.key} className="flex items-baseline gap-2" data-wellness-row={r.key}>
                      <span className="w-14 shrink-0">{r.label}</span>
                      <span className="text-foreground">{r.value}</span>
                    </span>
                  ))}
                </span>
              )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Link href="/wellness" className={buttonVariants({ variant: !today || !nothingLogged ? "outline" : "default", className: "h-12 w-full text-base font-semibold" })}>
          Log
        </Link>
      </CardContent>
    </Card>
  );
}
