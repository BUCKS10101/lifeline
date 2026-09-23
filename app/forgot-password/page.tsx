import { AuthCard } from "@/components/auth/ui";
import { ForgotPasswordForm } from "@/components/auth/forgot-password-form";

export const metadata = { title: "Forgot password | Personal OS" };

export default function ForgotPasswordPage() {
  return (
    <AuthCard title="Forgot your password?" subtitle="Enter your email and we will send a reset link.">
      <ForgotPasswordForm />
    </AuthCard>
  );
}
