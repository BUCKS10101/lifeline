import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { TodayView } from "@/components/wellness/today-view";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { ProteinSuggestion, Today } from "@/lib/wellness-types";

export const metadata = { title: "Wellness | Personal OS" };

export default async function WellnessPage() {
  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const todayResult = await backendGet<Today>("/api/v1/wellness/today");
  const today = resolve(todayResult);
  if (!today) return <BackendUnavailable />;

  // The chips in the protein sheet are only needed when protein is visible.
  const suggestions = today.protein ? resolve(await backendGet<ProteinSuggestion[]>("/api/v1/protein/suggestions")) : [];
  if (!suggestions) return <BackendUnavailable />;

  return (
    <div className="mx-auto flex w-full max-w-xl flex-col gap-6">
      <PageHeader title="Wellness" />
      <TodayView today={today} suggestions={suggestions} timeZone={user.timezone} />
    </div>
  );
}
