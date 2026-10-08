import Link from "next/link";
import { ReminderBoard } from "@/components/reminders/reminder-board";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { Reminder } from "@/lib/reminder-types";

export const metadata = { title: "Reminders | Personal OS" };

export default async function RemindersPage(props: PageProps<"/reminders">) {
  const params = await props.searchParams;
  const showCompleted = (Array.isArray(params.completed) ? params.completed[0] : params.completed) === "1";

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const reminders = resolve(await backendGet<Reminder[]>(`/api/v1/reminders${showCompleted ? "?includeCompleted=true" : ""}`));
  if (!reminders) return <BackendUnavailable />;

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <PageHeader title="Reminders" />
      <ReminderBoard reminders={reminders} showCompleted={showCompleted} />
      <div className="flex justify-center">
        <Link href={showCompleted ? "/reminders" : "/reminders?completed=1"} className="inline-flex h-11 items-center text-sm text-muted-foreground hover:text-foreground hover:underline">
          {showCompleted ? "Hide completed reminders" : "Show completed reminders"}
        </Link>
      </div>
    </div>
  );
}
