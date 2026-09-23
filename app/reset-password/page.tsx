import Link from "next/link";
import { AuthCard, Notice } from "@/components/auth/ui";
import { ResetPasswordForm } from "@/components/auth/reset-password-form";

export const metadata = { title: "Reset password | Personal OS" };

export default async function ResetPasswordPage(props: PageProps<"/reset-password">) {
  const { token } = await props.searchParams;

  return (
    <AuthCard title="Choose a new password">
      {typeof token === "string" && token ? (
        <ResetPasswordForm token={token} />
      ) : (
        <div className="flex flex-col gap-3">
          <Notice kind="error">This reset link is missing its token.</Notice>
          <Link href="/forgot-password" className="text-sm underline">Request a new link</Link>
        </div>
      )}
    </AuthCard>
  );
}
