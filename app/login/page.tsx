import { redirect } from "next/navigation";
import { AuthCard } from "@/components/auth/ui";
import { LoginForm } from "@/components/auth/login-form";
import { getSession } from "@/lib/backend";

export const metadata = { title: "Log in | Personal OS" };

export default async function LoginPage() {
  if ((await getSession()).status === "ok") redirect("/dashboard");
  return (
    <AuthCard title="Log in" subtitle="Welcome back to Personal OS.">
      <LoginForm />
    </AuthCard>
  );
}
