"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export type UndoOffer = { message: string; undo: () => Promise<void> };

const OFFER_MS = 8000;

/**
 * The short-lived "Undo" that appears on a line after an add. It goes away by itself after a few seconds, and
 * a newer add replaces it.
 */
export function useUndo() {
  const [offer, setOffer] = useState<UndoOffer | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clear = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    setOffer(null);
  }, []);

  const show = useCallback((next: UndoOffer) => {
    if (timer.current) clearTimeout(timer.current);
    setOffer(next);
    timer.current = setTimeout(() => setOffer(null), OFFER_MS);
  }, []);

  useEffect(() => () => { if (timer.current) clearTimeout(timer.current); }, []);

  return { offer, show, clear };
}
