"use client";

import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";

/**
 * A two-tap button for quick, irreversible actions: the first tap asks "Confirm?", the second does it.
 * It goes back to normal after a few seconds so a stray tap cannot linger.
 */
export function ConfirmButton({ label, confirmLabel = "Confirm", onConfirm, disabled, ariaLabel, children }: {
  label?: string;
  confirmLabel?: string;
  onConfirm: () => void;
  disabled?: boolean;
  ariaLabel: string;
  children?: React.ReactNode;
}) {
  const [armed, setArmed] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  function tap() {
    if (armed) {
      if (timer.current) clearTimeout(timer.current);
      setArmed(false);
      onConfirm();
      return;
    }
    setArmed(true);
    timer.current = setTimeout(() => setArmed(false), 3000);
  }

  return (
    <Button
      type="button"
      variant={armed ? "destructive" : "ghost"}
      size={armed || label ? "default" : "icon"}
      className="h-11 min-w-11"
      disabled={disabled}
      aria-label={armed ? `${confirmLabel}: ${ariaLabel}` : ariaLabel}
      onClick={tap}
    >
      {armed ? confirmLabel : (children ?? label)}
    </Button>
  );
}
