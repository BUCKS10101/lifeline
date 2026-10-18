/** Types mirroring the backend's reminder DTOs. A reminder is a calendar event seen as a to-do. `due` is derived by the backend: pending and its moment has arrived. */

export type Reminder = {
  id: string;
  title: string;
  date: string;
  time: string;
  timeZone: string;
  remindAt: string;
  completedAt: string | null;
  due: boolean;
  allDay: boolean;
};
