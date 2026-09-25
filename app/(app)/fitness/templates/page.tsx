import Link from "next/link";
import { Plus } from "lucide-react";
import { EmptyState } from "@/components/fitness/empty-state";
import { TemplateActions } from "@/components/fitness/template-actions";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { Paged, TemplateSummary } from "@/lib/fitness-types";
import { pluralize } from "@/lib/format";

export const metadata = { title: "Templates | Personal OS" };

export default async function TemplatesPage() {
  const user = await requireUser();
  if (!user) return <BackendUnavailable />;
  const templates = resolve(await backendGet<Paged<TemplateSummary>>("/api/v1/workout-templates?size=100"));
  if (!templates) return <BackendUnavailable />;

  const builtIn = templates.items.filter((t) => t.builtIn);
  const mine = templates.items.filter((t) => !t.builtIn);

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-8">
      <PageHeader
        title="Workout templates"
        description="Start a workout from a list of exercises. Duplicate a built-in one to make it your own."
        actions={<Link href="/fitness/templates/new" className={buttonVariants()}><Plus aria-hidden />New template</Link>}
      />

      <TemplateList title="My templates" templates={mine}
        empty={<EmptyState title="No templates of your own yet">Create one, or duplicate Push, Pull or Legs below.</EmptyState>} />
      <TemplateList title="Built-in templates" templates={builtIn} />
    </div>
  );
}

function TemplateList({ title, templates, empty }: { title: string; templates: TemplateSummary[]; empty?: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-sm font-medium text-muted-foreground">{title}</h2>
      {templates.length === 0 ? empty : (
        <ul className="flex flex-col gap-3">
          {templates.map((t) => (
            <li key={t.id}>
              <Card>
                <CardContent className="flex flex-col gap-3">
                  <div className="flex items-center justify-between gap-3">
                    <Link href={`/fitness/templates/${t.id}`} className="min-w-0 truncate font-medium hover:underline">{t.name}</Link>
                    <span className="flex shrink-0 items-center gap-2 text-sm text-muted-foreground">
                      {pluralize(t.exerciseCount, "exercise")}
                      {t.builtIn && <Badge variant="secondary">Built-in</Badge>}
                    </span>
                  </div>
                  <TemplateActions id={t.id} builtIn={t.builtIn} />
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
