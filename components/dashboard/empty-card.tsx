import type { LucideIcon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

/**
 * A dashboard card for a module that does not exist yet. It states what will appear here and
 * which module supplies it. It never shows sample or made-up numbers.
 */
export function EmptyCard({
  icon: Icon,
  title,
  description,
  module,
  className,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
  module: string;
  className?: string;
}) {
  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Icon className="size-4 text-muted-foreground" aria-hidden />
          {title}
        </CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="flex items-center gap-2 text-sm text-muted-foreground">
        <Badge variant="secondary">Soon</Badge>
        <span>Arrives with {module}</span>
      </CardContent>
    </Card>
  );
}
