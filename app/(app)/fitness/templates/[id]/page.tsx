import Link from "next/link";
import { TemplateActions } from "@/components/fitness/template-actions";
import { TemplateEditor } from "@/components/fitness/template-editor";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { TemplateDetail } from "@/lib/fitness-types";
import { MUSCLE_LABEL } from "@/lib/format";

export const metadata = { title: "Template | Personal OS" };

/** Your own templates open in the editor. Built-in ones are shown read-only, with Start and Duplicate. */
export default async function TemplatePage(props: PageProps<"/fitness/templates/[id]">) {
  const { id } = await props.params;
  const user = await requireUser();
  if (!user) return <BackendUnavailable />;
  const template = resolve(await backendGet<TemplateDetail>(`/api/v1/workout-templates/${id}`));
  if (!template) return <BackendUnavailable />;

  if (!template.builtIn) {
    return (
      <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
        <PageHeader title="Edit template" actions={<Link href="/fitness/templates" className="inline-flex h-11 items-center text-sm text-muted-foreground hover:text-foreground hover:underline">All templates</Link>} />
        <TemplateEditor initial={template} />
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <PageHeader
        title={template.name}
        description={template.notes ?? undefined}
        actions={<><span className="rounded border px-1.5 text-xs font-medium text-muted-foreground">Built-in</span><Link href="/fitness/templates" className="inline-flex h-11 items-center text-sm text-muted-foreground hover:text-foreground hover:underline">All templates</Link></>}
      />
      <TemplateActions id={template.id} builtIn />
      <ol className="flex flex-col divide-y overflow-hidden rounded-lg border bg-card">
        {template.exercises.map((row) => (
          <li key={row.exercise.id} className="flex min-h-16 items-center justify-between gap-3 px-4 py-3">
            <span className="num w-6 shrink-0 text-sm text-muted-foreground">{String(row.position).padStart(2, "0")}</span>
            <div className="flex min-w-0 flex-1 flex-col">
              <span className="truncate font-medium">{row.exercise.name}</span>
              <span className="text-sm text-muted-foreground">{MUSCLE_LABEL[row.exercise.primaryMuscleGroup]}</span>
            </div>
            {row.targetSets !== null && <span className="num shrink-0 text-sm text-muted-foreground">{row.targetSets} sets</span>}
          </li>
        ))}
      </ol>
    </div>
  );
}
