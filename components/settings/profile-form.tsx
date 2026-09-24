"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { errorMessage, fieldErrors, Field, Notice, SelectField, SubmitButton } from "@/components/auth/ui";
import { apiPatch } from "@/lib/client-api";

export function ProfileForm({
  email,
  displayName,
  timezone,
  timezones,
}: {
  email: string;
  displayName: string;
  timezone: string;
  timezones: string[];
}) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPending(true);
    setError(null);
    setErrors({});
    setSaved(false);
    try {
      await apiPatch("/api/v1/profile", {
        displayName: form.get("displayName"),
        timezone: form.get("timezone"),
      });
      setSaved(true);
      router.refresh(); // re-render the shell so the greeting and date use the new values
    } catch (e) {
      setErrors(fieldErrors(e));
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Profile</CardTitle>
        <CardDescription>Signed in as {email}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="flex flex-col gap-4">
          {error && <Notice kind="error">{error}</Notice>}
          {saved && <Notice kind="success">Saved.</Notice>}
          <Field
            label="Name"
            name="displayName"
            defaultValue={displayName}
            required
            maxLength={100}
            autoComplete="name"
            error={errors.displayName}
          />
          <SelectField label="Timezone" name="timezone" defaultValue={timezone} error={errors.timezone}>
            {timezones.map((zone) => (
              <option key={zone} value={zone}>
                {zone.replaceAll("_", " ")}
              </option>
            ))}
          </SelectField>
          <SubmitButton pending={pending}>Save changes</SubmitButton>
        </form>
      </CardContent>
    </Card>
  );
}
