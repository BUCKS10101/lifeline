import { HairWashView } from "@/components/care/hair-wash-view";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { HairWashSummary } from "@/lib/care-types";
import { todayIn } from "@/lib/format";

export const metadata = { title: "Personal Care | Personal OS" };

function paramString(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

export default async function PersonalCarePage(props: PageProps<"/personal-care">) {
  const params = await props.searchParams;

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const today = todayIn(user.timezone);
  const month = paramString(params.month) ?? today.slice(0, 7);

  const summary = resolve(await backendGet<HairWashSummary>(`/api/v1/personal-care/hair-wash?month=${month}`));
  if (!summary) return <BackendUnavailable />;

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <PageHeader title="Personal Care" />
      <HairWashView month={month} today={today} summary={summary} />
    </div>
  );
}
