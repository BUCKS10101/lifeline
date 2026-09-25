import Link from "next/link";
import { TemplateActions } from "@/components/fitness/template-actions";
import { TemplateEditor } from "@/components/fitness/template-editor";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
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
        <PageHeader title="Edit template" actions={<Link href="/fitness/templates" className="text-sm underline">All templates</Link>} />
        <TemplateEditor initial={template} />
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <PageHeader
        title={template.name}
        description={template.notes ?? undefined}
        actions={<><Badge variant="secondary">Built-in</Badge><Link href="/fitness/templates" className="text-sm underline">All templates</Link></>}
      />
      <TemplateActions id={template.id} builtIn />
      <ol className="flex flex-col gap-2">
        {template.exercises.map((row) => (
          <li key={row.exercise.id}>
            <Card>
              <CardContent className="flex items-center justify-between gap-3">
                <div className="flex min-w-0 flex-col">
                  <span className="truncate font-medium">{row.position}. {row.exercise.name}</span>
                  <span className="text-xs text-muted-foreground">{MUSCLE_LABEL[row.exercise.primaryMuscleGroup]}</span>
                </div>
                {row.targetSets !== null && <span className="text-sm text-muted-foreground">{row.targetSets} sets</span>}
              </CardContent>
            </Card>
          </li>
        ))}
      </ol>
    </div>
  );
}
