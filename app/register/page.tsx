import { redirect } from "next/navigation";
import { AuthCard } from "@/components/auth/ui";
import { RegisterForm } from "@/components/auth/register-form";
import { getSession } from "@/lib/backend";

export const metadata = { title: "Create account | Personal OS" };

export default async function RegisterPage() {
  if ((await getSession()).status === "ok") redirect("/dashboard");
  return (
    <AuthCard title="Create your account" subtitle="We will email you a link to verify your address.">
      <RegisterForm />
    </AuthCard>
  );
}
