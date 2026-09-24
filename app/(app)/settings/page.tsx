import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { ProfileForm } from "@/components/settings/profile-form";
import { requireUser } from "@/lib/backend";
import { listTimezones } from "@/lib/timezones";

export const metadata = { title: "Settings | Personal OS" };

export default async function SettingsPage() {
  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  return (
    <div className="mx-auto flex w-full max-w-xl flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
        <p className="text-muted-foreground">Your profile. The timezone decides when your day starts and ends.</p>
      </header>
      <ProfileForm
        email={user.email}
        displayName={user.displayName}
        timezone={user.timezone}
        timezones={listTimezones(user.timezone)}
      />
    </div>
  );
}
