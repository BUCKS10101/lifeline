import Link from "next/link";
import { AuthCard, Notice } from "@/components/auth/ui";
import { VerifyEmailPanel } from "@/components/auth/verify-email-panel";

export const metadata = { title: "Verify email | Personal OS" };

export default async function VerifyEmailPage(props: PageProps<"/verify-email">) {
  const { token } = await props.searchParams;

  return (
    <AuthCard title="Verify your email">
      {typeof token === "string" && token ? (
        <VerifyEmailPanel token={token} />
      ) : (
        <div className="flex flex-col gap-3">
          <Notice kind="error">This verification link is missing its token.</Notice>
          <Link href="/login" className="text-sm underline">Back to log in</Link>
        </div>
      )}
    </AuthCard>
  );
}
