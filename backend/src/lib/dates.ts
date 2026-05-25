/**
 * Date helpers. We store earned_date as 'YYYY-MM-DD' strings in the family's
 * local timezone (IANA), so "today" depends on the family TZ, not server TZ.
 */

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export function isValidDateString(s: string): boolean {
  if (!DATE_RE.test(s)) return false;
  const d = new Date(`${s}T00:00:00Z`);
  return !Number.isNaN(d.getTime()) && s === d.toISOString().slice(0, 10);
}

/** Today's date in the given IANA timezone, as 'YYYY-MM-DD'. */
export function todayInTz(tz: string, now: Date = new Date()): string {
  // en-CA gives ISO-style YYYY-MM-DD.
  const fmt = new Intl.DateTimeFormat("en-CA", {
    timeZone: tz,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  return fmt.format(now);
}

/** Returns true if `date` is on or before today in the given TZ. */
export function isTodayOrPast(date: string, tz: string, now: Date = new Date()): boolean {
  return date <= todayInTz(tz, now);
}
