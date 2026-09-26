import type { MovementGroup } from "@/lib/fitness-types";

/**
 * How the four movement groups are named and coloured. The grouping itself is decided by the backend (a muscle group
 * belongs to exactly one movement group); this file is the only place the frontend describes them.
 */
export const MOVEMENT_GROUPS: { key: MovementGroup; label: string; color: string }[] = [
  { key: "PUSH", label: "Push", color: "var(--chart-1)" },
  { key: "PULL", label: "Pull", color: "var(--chart-3)" },
  { key: "LEGS", label: "Legs", color: "var(--chart-4)" },
  { key: "CORE_FULL_BODY", label: "Core / full body", color: "var(--chart-5)" },
];
