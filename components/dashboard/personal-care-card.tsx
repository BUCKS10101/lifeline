import Link from "next/link";
import { ShowerHead } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { MarkHairWashButton } from "@/components/dashboard/mark-hair-wash-button";
import { backendGet } from "@/lib/backend";
import type { HairWashSummary } from "@/lib/care-types";
import { pluralize } from "@/lib/format";

/** The dashboard's "Personal Care" card: how long since the last hair wash, with a one-tap "Mark today". */
export async function PersonalCareCard({ today }: { today: string }) {
  const result = await backendGet<HairWashSummary>("/api/v1/personal-care/hair-wash");
  const summary = result.status === "ok" ? result.data : null;
  const alreadyMarkedToday = summary?.lastWashedOn === today;

  return (
    <Card data-personal-care-card className="min-w-0">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <ShowerHead className="size-4 text-muted-foreground" aria-hidden />
          Personal Care
        </CardTitle>
        <CardDescription className="num min-w-0">
          {!summary ? "Could not be loaded right now."
            : summary.daysAgo === null ? <span data-no-entries>Hair wash: no entries yet.</span>
              : (
                <span className="text-foreground" data-last-washed>
                  Hair wash — last washed {summary.daysAgo === 0 ? "today" : summary.daysAgo === 1 ? "yesterday" : `${pluralize(summary.daysAgo, "day")} ago`}
                </span>
              )}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex gap-2">
        {summary && <MarkHairWashButton alreadyMarkedToday={alreadyMarkedToday} />}
        <Link href="/personal-care" className={buttonVariants({ variant: "outline", className: "h-11 flex-1 text-base font-semibold" })}>
          View history
        </Link>
      </CardContent>
    </Card>
  );
}
