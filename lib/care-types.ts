/** Types mirroring the backend's personal-care (hair-wash) DTOs. A plain date log: no products, no conditions. */

export type HairWashEntry = {
  id: string;
  washDate: string;
};

/** lastWashedOn/daysAgo cover the whole history; entries are only the requested month's. */
export type HairWashSummary = {
  lastWashedOn: string | null;
  daysAgo: number | null;
  month: string;
  entries: HairWashEntry[];
};
