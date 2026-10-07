import { Code } from "lucide-react";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { BodyWeightCard } from "@/components/dashboard/body-weight-card";
import { EmptyCard } from "@/components/dashboard/empty-card";
import { GoalsCard } from "@/components/dashboard/goals-card";
import { PersonalCareCard } from "@/components/dashboard/personal-care-card";
import { TodaysScheduleCard } from "@/components/dashboard/todays-schedule-card";
import { TodaysWorkoutCard } from "@/components/dashboard/todays-workout-card";
import { HabitsTodayCard } from "@/components/dashboard/habits-today-card";
import { TasksDueCard } from "@/components/dashboard/tasks-due-card";
import { UpcomingRemindersCard } from "@/components/dashboard/upcoming-reminders-card";
import { WellnessTodayCard } from "@/components/dashboard/wellness-card";
import { requireUser } from "@/lib/backend";
import { formatToday, greeting } from "@/lib/timezones";
import { todayIn } from "@/lib/format";

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
        <div className="grid gap-4 md:grid-cols-2">
          <TodaysWorkoutCard timezone={user.timezone} />
          <WellnessTodayCard />
          <TasksDueCard />
          <HabitsTodayCard />
          <TodaysScheduleCard timezone={user.timezone} />
        </div>
      </section>

      <section aria-labelledby="upcoming-heading" className="flex flex-col gap-3">
        <h2 id="upcoming-heading" className="text-sm font-medium text-muted-foreground">Coming up</h2>
        <div className="grid gap-4 md:grid-cols-2">
          <UpcomingRemindersCard />
        </div>
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
          <GoalsCard />
          <PersonalCareCard today={todayIn(user.timezone)} />
        </div>
      </section>
    </div>
  );
}
