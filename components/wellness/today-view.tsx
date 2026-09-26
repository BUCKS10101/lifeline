"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { Ellipsis } from "lucide-react";
import { errorMessage } from "@/components/auth/ui";
import { CustomizeSheet } from "@/components/wellness/customize-sheet";
import { ProteinSheet } from "@/components/wellness/protein-sheet";
import { SleepSheet } from "@/components/wellness/sleep-sheet";
import { useUndo } from "@/components/wellness/use-undo";
import { WaterSheet } from "@/components/wellness/water-sheet";
import { Button } from "@/components/ui/button";
import { apiDelete, apiPost } from "@/lib/client-api";
import { formatGrams, formatMinutes, formatMl } from "@/lib/format";
import type { ProteinAdded, ProteinSuggestion, Today, WaterAdded } from "@/lib/wellness-types";

/** The quick amounts on the Today lines. Other amounts are one tap further, in the sheet. */
const QUICK_WATER_ML = 250;
const QUICK_PROTEIN_G = 20;

/**
 * The whole Today view: one line per visible metric, each with one primary button. No charts, no history. The lines
 * are plain text for now; they become links when the metric pages exist.
 */
export function TodayView({ today, suggestions, timeZone }: { today: Today; suggestions: ProteinSuggestion[]; timeZone: string }) {
  const router = useRouter();
  const refresh = () => router.refresh();
  const { sleep, water, protein, preferences } = today;
  const nothingVisible = !sleep && !water && !protein;

  const customize = (
    <CustomizeSheet trigger={<Button type="button" variant="ghost" className="h-11 px-3 text-muted-foreground" />} preferences={preferences} onSaved={refresh}>
      Customize
    </CustomizeSheet>
  );

  return (
    <div className="flex flex-col gap-3">
      {nothingVisible ? (
        <p className="rounded-lg border border-dashed p-5 text-sm text-muted-foreground" data-all-hidden>
          Everything is hidden. Use Customize to show sleep, water or protein again. Your data is kept.
        </p>
      ) : (
        <ul className="divide-y overflow-hidden rounded-lg border bg-card" aria-label="Today">
          {sleep && <SleepLine today={today} timeZone={timeZone} onChanged={refresh} />}
          {water && <WaterLine total={water.totalMl} goal={water.goalMl} percent={water.progressPercent} reached={water.goalReached} onChanged={refresh} />}
          {protein && (
            <ProteinLine total={protein.totalG} goal={protein.goalG} percent={protein.progressPercent} reached={protein.goalReached}
              suggestions={suggestions} onChanged={refresh} />
          )}
        </ul>
      )}
      <div className="flex justify-end">{customize}</div>
    </div>
  );
}

/** One line: label, value, and its actions, with an optional progress line and a short-lived note underneath. */
function Line({ metric, label, value, muted, goalText, percent, reached, actions, footer }: {
  metric: string;
  label: string;
  value: string;
  muted?: boolean;
  goalText?: string | null;
  percent?: number | null;
  reached?: boolean;
  actions: React.ReactNode;
  footer?: React.ReactNode;
}) {
  return (
    <li className="flex flex-col gap-1.5 px-4 py-3" data-metric={metric}>
      <div className="flex min-h-11 items-center gap-3">
        <span className="w-[4.5rem] shrink-0 text-sm font-medium">{label}</span>
        <span className="num min-w-0 flex-1 text-lg leading-tight" data-value>
          <span className={muted ? "text-muted-foreground" : "font-medium"}>{value}</span>
          {goalText && <span className="text-sm font-normal text-muted-foreground"> {goalText}</span>}
        </span>
        <div className="flex shrink-0 items-center gap-1.5">{actions}</div>
      </div>
      {percent !== null && percent !== undefined && (
        <div role="progressbar" aria-label={`${label} progress toward goal`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}
          className="ml-[calc(4.5rem+0.75rem)] h-1 overflow-hidden rounded-full bg-muted">
          <div className="h-full rounded-full bg-primary" style={{ width: `${percent}%` }} />
        </div>
      )}
      {reached && <span className="ml-[calc(4.5rem+0.75rem)] text-xs text-muted-foreground">Goal reached</span>}
      {footer}
    </li>
  );
}

function Note({ children }: { children: React.ReactNode }) {
  return <div className="ml-[calc(4.5rem+0.75rem)] flex min-h-6 flex-wrap items-center gap-x-3 text-sm text-muted-foreground">{children}</div>;
}

const quickButton = "h-11 min-w-[4.5rem] px-3 text-base font-semibold";
const moreButton = "size-11 text-muted-foreground";

function SleepLine({ today, timeZone, onChanged }: { today: Today; timeZone: string; onChanged: () => void }) {
  const sleep = today.sleep!;
  const [saved, setSaved] = useState(false);
  const entry = sleep.entry;
  return (
    <Line
      metric="sleep"
      label="Sleep"
      value={entry ? formatMinutes(entry.durationMinutes) : "Not logged"}
      muted={!entry}
      goalText={entry && sleep.goalMinutes ? `of ${formatMinutes(sleep.goalMinutes)}` : null}
      percent={sleep.progressPercent}
      reached={sleep.goalReached}
      actions={
        <SleepSheet trigger={<Button type="button" variant="outline" className={quickButton} />} wakeDate={today.date} timeZone={timeZone}
          existing={entry} onSaved={() => { setSaved(true); setTimeout(() => setSaved(false), 4000); onChanged(); }}>
          {entry ? "Edit" : "Log"}
        </SleepSheet>
      }
      footer={saved ? <Note><span role="status">Saved</span></Note> : undefined}
    />
  );
}

function WaterLine({ total, goal, percent, reached, onChanged }: {
  total: number; goal: number | null; percent: number | null; reached: boolean; onChanged: () => void;
}) {
  const { offer, show, clear } = useUndo();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // One client-generated id per attempt: a retry after a failed request returns the same drink, never a second one.
  const attempt = useRef<{ ml: number; id: string } | null>(null);

  async function add(ml: number) {
    if (!attempt.current || attempt.current.ml !== ml) attempt.current = { ml, id: crypto.randomUUID() };
    const { id } = attempt.current;
    const added = await apiPost<WaterAdded>("/api/v1/water-entries", { amountMl: ml, id });
    attempt.current = null;
    show({
      message: `Added ${formatMl(ml)}`,
      undo: async () => { await apiDelete(`/api/v1/water-entries/${added.entry.id}`); },
    });
    onChanged();
  }

  async function quick() {
    setPending(true);
    setError(null);
    try {
      await add(QUICK_WATER_ML);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  async function undo() {
    if (!offer) return;
    setPending(true);
    try {
      await offer.undo();
      clear();
      onChanged();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  return (
    <Line
      metric="water"
      label="Water"
      value={formatMl(total)}
      goalText={goal ? `of ${formatMl(goal)}` : null}
      percent={percent}
      reached={reached}
      actions={
        <>
          <Button type="button" variant="outline" className={quickButton} disabled={pending} onClick={() => void quick()} aria-label={`Add ${QUICK_WATER_ML} millilitres of water`}>
            +{QUICK_WATER_ML}
          </Button>
          <WaterSheet trigger={<Button type="button" variant="ghost" size="icon" className={moreButton} aria-label="More ways to log water" />} onAdd={add}>
            <Ellipsis aria-hidden />
          </WaterSheet>
        </>
      }
      footer={(offer || error) ? (
        <Note>
          {error ? <span role="alert" className="text-destructive">{error}</span> : <span role="status">{offer!.message}</span>}
          {offer && <Button type="button" variant="ghost" className="h-11 px-2 text-primary" disabled={pending} onClick={() => void undo()}>Undo</Button>}
        </Note>
      ) : undefined}
    />
  );
}

function ProteinLine({ total, goal, percent, reached, suggestions, onChanged }: {
  total: number; goal: number | null; percent: number | null; reached: boolean; suggestions: ProteinSuggestion[]; onChanged: () => void;
}) {
  const { offer, show, clear } = useUndo();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const attempt = useRef<{ key: string; id: string } | null>(null);

  async function add(grams: number, label: string | null) {
    const key = `${grams}|${label ?? ""}`;
    if (!attempt.current || attempt.current.key !== key) attempt.current = { key, id: crypto.randomUUID() };
    const { id } = attempt.current;
    const added = await apiPost<ProteinAdded>("/api/v1/protein-entries", { grams, label, id });
    attempt.current = null;
    show({
      message: `Added ${formatGrams(grams)}${label ? ` · ${label}` : ""}`,
      undo: async () => { await apiDelete(`/api/v1/protein-entries/${added.entry.id}`); },
    });
    onChanged();
  }

  async function quick() {
    setPending(true);
    setError(null);
    try {
      await add(QUICK_PROTEIN_G, null);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  async function undo() {
    if (!offer) return;
    setPending(true);
    try {
      await offer.undo();
      clear();
      onChanged();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  return (
    <Line
      metric="protein"
      label="Protein"
      value={formatGrams(total)}
      goalText={goal ? `of ${formatGrams(goal)}` : null}
      percent={percent}
      reached={reached}
      actions={
        <>
          <Button type="button" variant="outline" className={quickButton} disabled={pending} onClick={() => void quick()} aria-label={`Add ${QUICK_PROTEIN_G} grams of protein`}>
            +{QUICK_PROTEIN_G}
          </Button>
          <ProteinSheet trigger={<Button type="button" variant="ghost" size="icon" className={moreButton} aria-label="More ways to log protein" />}
            suggestions={suggestions} onAdd={add}>
            <Ellipsis aria-hidden />
          </ProteinSheet>
        </>
      }
      footer={(offer || error) ? (
        <Note>
          {error ? <span role="alert" className="text-destructive">{error}</span> : <span role="status">{offer!.message}</span>}
          {offer && <Button type="button" variant="ghost" className="h-11 px-2 text-primary" disabled={pending} onClick={() => void undo()}>Undo</Button>}
        </Note>
      ) : undefined}
    />
  );
}
