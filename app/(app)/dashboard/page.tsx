import { CalendarDays, Code, Flame, ListChecks, Target } from "lucide-react";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { BodyWeightCard } from "@/components/dashboard/body-weight-card";
import { EmptyCard } from "@/components/dashboard/empty-card";
import { TodaysWorkoutCard } from "@/components/dashboard/todays-workout-card";
import { requireUser } from "@/lib/backend";
import { formatToday, greeting } from "@/lib/timezones";

export const metadata = { title: "Dashboard | Personal OS" };

export default async function DashboardPage() {
  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const firstName = user.displayName.trim().split(/\s+/)[0] || "there";

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-8">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
          {greeting(user.timezone)}, {firstName}
        </h1>
        <p className="text-muted-foreground">{formatToday(user.timezone)}. What do you need to do today?</p>
      </header>

      <section aria-labelledby="today-heading" className="flex flex-col gap-3">
        <h2 id="today-heading" className="text-sm font-medium text-muted-foreground">Today</h2>
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <TodaysWorkoutCard timezone={user.timezone} />
          <EmptyCard
            icon={ListChecks}
            title="Tasks due"
            description="Tasks and deadlines that need attention today."
            module="Tasks"
          />
          <EmptyCard
            icon={Flame}
            title="Habits today"
            description="Habits to complete and your current streaks."
            module="Habits"
          />
        </div>
      </section>

      <section aria-labelledby="upcoming-heading" className="flex flex-col gap-3">
        <h2 id="upcoming-heading" className="text-sm font-medium text-muted-foreground">Coming up</h2>
        <EmptyCard
          icon={CalendarDays}
          title="Upcoming events"
          description="Events, deadlines and study sessions for the next few days."
          module="Calendar"
        />
      </section>

      <section aria-labelledby="progress-heading" className="flex flex-col gap-3">
        <h2 id="progress-heading" className="text-sm font-medium text-muted-foreground">Progress</h2>
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <BodyWeightCard />
          <EmptyCard
            icon={Code}
            title="DSA progress"
            description="Problems solved and recent activity."
            module="DSA"
          />
          <EmptyCard
            icon={Target}
            title="Goals"
            description="How far along each of your goals is."
            module="Goals"
          />
        </div>
      </section>
    </div>
  );
}
