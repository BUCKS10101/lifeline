/** Mirrors the server's rules for a weight: 20 to 500 kg, at most two decimals. Returns kilograms, or a message. */
export function parseWeight(text: string): { ok: true; kg: number } | { ok: false; error: string } {
  const t = text.trim().replace(",", ".");
  if (t === "" || !/^\d{1,3}(\.\d{1,2})?$/.test(t)) return { ok: false, error: "20 to 500 kg, up to 2 decimals" };
  const kg = Number(t);
  if (kg < 20 || kg > 500) return { ok: false, error: "20 to 500 kg" };
  return { ok: true, kg };
}
