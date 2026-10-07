/** Types mirroring the backend's calendar event DTOs. startTime/endTime are null for an all-day event. timeZone is
 * the zone the event was created in, not the viewer's current one: it never changes after creation. */

export type CalendarEvent = {
  id: string;
  title: string;
  description: string | null;
  startDate: string;
  startTime: string | null;
  endDate: string | null;
  endTime: string | null;
  allDay: boolean;
  timeZone: string;
  startAt: string;
  endAt: string | null;
};
