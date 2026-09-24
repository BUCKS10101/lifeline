import Link from "next/link";

export function Brand() {
  return (
    <Link href="/dashboard" className="text-base font-semibold tracking-tight outline-none focus-visible:underline">
      Personal OS
    </Link>
  );
}
