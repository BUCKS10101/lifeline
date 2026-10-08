/** Types mirroring the backend's reminder DTOs. `due` is derived by the backend: pending and its moment has arrived. */

export type Reminder = {
  id: string;
  title: string;
  date: string;
  time: string;
  timeZone: string;
  remindAt: string;
  completedAt: string | null;
  due: boolean;
};
