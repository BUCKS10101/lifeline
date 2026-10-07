"use client";

import { useEffect, useState } from "react";
import { SelectField } from "@/components/auth/ui";
import { apiGet } from "@/lib/client-api";
import type { Goal, GoalRef } from "@/lib/goal-types";

/**
 * An optional goal to link, offered from the active list. A task or habit that is already linked to a goal that has
 * since been achieved or archived still shows that goal (it is simply added to the list), so its current link is
 * never silently hidden.
 */
export function GoalPicker({ value, onChange, error, disabled, currentGoal }: {
  value: string;
  onChange: (goalId: string) => void;
  error?: string;
  disabled?: boolean;
  currentGoal?: GoalRef | null;
}) {
  const [goals, setGoals] = useState<Goal[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    apiGet<Goal[]>("/api/v1/goals?status=active")
      .then((g) => { if (!cancelled) setGoals(g ?? []); })
      .catch(() => { if (!cancelled) setGoals([]); });
    return () => { cancelled = true; };
  }, []);

  const options = goals ?? [];
  const withCurrent = currentGoal && !options.some((g) => g.id === currentGoal.id)
    ? [...options, { ...currentGoal, description: null, targetDate: null, status: "ACTIVE", achievedAt: null, taskCount: 0, doneCount: 0, progressPercent: null } as Goal]
    : options;

  return (
    <SelectField label="Goal" name="goalId" value={value} error={error} disabled={disabled || goals === null}
      onChange={(e) => onChange(e.target.value)}>
      <option value="">No goal</option>
      {withCurrent.map((g) => <option key={g.id} value={g.id}>{g.title}</option>)}
    </SelectField>
  );
}
