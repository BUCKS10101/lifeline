import { TemplateEditor } from "@/components/fitness/template-editor";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { requireUser } from "@/lib/backend";

export const metadata = { title: "New template | Personal OS" };

export default async function NewTemplatePage() {
  const user = await requireUser();
  if (!user) return <BackendUnavailable />;
  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <PageHeader title="New template" description="Choose the exercises and their order." />
      <TemplateEditor />
    </div>
  );
}
