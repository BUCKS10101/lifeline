import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";

/** Previous / next links for a paged list driven by the ?page= query parameter. */
export function Pagination({ basePath, page, totalPages, extraQuery = "", param = "page", label = "Pagination" }: {
  basePath: string;
  page: number;
  totalPages: number;
  extraQuery?: string;
  /** The query parameter that carries the page, when a page has more than one paged list. */
  param?: string;
  label?: string;
}) {
  if (totalPages <= 1) return null;
  const href = (p: number) => `${basePath}?${param}=${p}${extraQuery ? `&${extraQuery}` : ""}`;
  return (
    <nav aria-label={label} className="flex items-center justify-between gap-3">
      {page > 0 ? (
        <Link href={href(page - 1)} className={buttonVariants({ variant: "outline", className: "h-11 px-4" })}>Previous</Link>
      ) : <span />}
      <span className="num text-sm text-muted-foreground">Page {page + 1} of {totalPages}</span>
      {page + 1 < totalPages ? (
        <Link href={href(page + 1)} className={buttonVariants({ variant: "outline", className: "h-11 px-4" })}>Next</Link>
      ) : <span />}
    </nav>
  );
}
