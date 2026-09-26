import Link from "next/link";
import { Scale } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { backendGet } from "@/lib/backend";
import { formatDate, formatKg, formatNumber, formatSignedKg } from "@/lib/format";
import type { WeightSummary } from "@/lib/weight-types";

/** The dashboard's body-weight card: current weight, the recent change and progress toward the target, all from the API. */
export async function BodyWeightCard() {
  const result = await backendGet<WeightSummary>("/api/v1/weight/summary");
  const summary = result.status === "ok" ? result.data : null;
  const current = summary?.current ?? null;
  const change = summary?.change?.last7Days ?? null;
  const progress = summary?.progress ?? null;
  const target = summary?.target ?? null;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Scale className="size-4 text-muted-foreground" aria-hidden />
          Body weight
        </CardTitle>
        <CardDescription className="num">
          {result.status === "unavailable"
            ? "Weight could not be loaded right now."
            : !current
              ? "No weight logged yet. Log one to start your trend."
              : <>
                  <span className="text-2xl leading-none font-semibold text-foreground" data-body-weight>{formatNumber(current.weightKg)}</span>
                  {" "}<span className="text-foreground">kg</span>
                  {" "}<span className="ml-1">{formatDate(current.date)}</span>
                  {change && <><br /><span data-body-weight-change>{formatSignedKg(change.changeKg)} in 7 days</span></>}
                </>}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {progress && target && (
          <div className="flex flex-col gap-1.5" data-body-weight-progress>
            {progress.percent !== null && (
              <div role="progressbar" aria-label="Progress toward target" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress.percent}
                className="h-1.5 overflow-hidden rounded-full bg-muted">
                <div className="h-full rounded-full bg-primary" style={{ width: `${progress.percent}%` }} />
              </div>
            )}
            <span className="num text-sm text-muted-foreground">
              {progress.reached ? "Target reached" : `${formatNumber(progress.remainingKg)} kg to ${formatKg(target.targetWeightKg)}`}
            </span>
          </div>
        )}
        <Link href="/weight" className={buttonVariants({ variant: current ? "outline" : "default", className: "h-12 w-full text-base font-semibold" })}>
          {current ? "View weight" : "Log weight"}
        </Link>
      </CardContent>
    </Card>
  );
}
