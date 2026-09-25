import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";

/** Previous / next links for a paged list driven by the ?page= query parameter. */
export function Pagination({ basePath, page, totalPages, extraQuery = "" }: {
  basePath: string;
  page: number;
  totalPages: number;
  extraQuery?: string;
}) {
  if (totalPages <= 1) return null;
  const href = (p: number) => `${basePath}?page=${p}${extraQuery ? `&${extraQuery}` : ""}`;
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-3">
      {page > 0 ? (
        <Link href={href(page - 1)} className={buttonVariants({ variant: "outline" })}>Previous</Link>
      ) : <span />}
      <span className="text-sm text-muted-foreground">Page {page + 1} of {totalPages}</span>
      {page + 1 < totalPages ? (
        <Link href={href(page + 1)} className={buttonVariants({ variant: "outline" })}>Next</Link>
      ) : <span />}
    </nav>
  );
}
