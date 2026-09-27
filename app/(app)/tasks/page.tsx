import { Pagination } from "@/components/fitness/pagination";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { TaskBoard } from "@/components/tasks/task-board";
import { ViewLinks } from "@/components/tasks/view-links";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { Paged } from "@/lib/fitness-types";
import { todayIn } from "@/lib/format";
import { parseTaskView, type TaskItem } from "@/lib/task-types";

export const metadata = { title: "Tasks | Personal OS" };

const PAGE_SIZE = 20;

export default async function TasksPage(props: PageProps<"/tasks">) {
  const params = await props.searchParams;
  const view = parseTaskView(params.view);
  const parsedPage = Number(Array.isArray(params.page) ? params.page[0] : params.page);
  const page = Number.isInteger(parsedPage) && parsedPage >= 0 ? parsedPage : 0;

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const tasks = resolve(await backendGet<Paged<TaskItem>>(`/api/v1/tasks?view=${view}&page=${page}&size=${PAGE_SIZE}`));
  if (!tasks) return <BackendUnavailable />;

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-4">
      <PageHeader title="Tasks" />
      <ViewLinks current={view} />
      <TaskBoard view={view} tasks={tasks.items} today={todayIn(user.timezone)} />
      <Pagination basePath="/tasks" page={page} totalPages={tasks.totalPages} extraQuery={`view=${view}`} />
    </div>
  );
}
