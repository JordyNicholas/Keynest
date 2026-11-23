export function localDateAfterDays(days: number, now = new Date()): Date {
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() + days);
}

/** A date-only choice expires at the end of that day in the browser's local timezone. */
export function expiryInstant(date: Date | null): string | null {
  return date ? new Date(date.getFullYear(), date.getMonth(), date.getDate(), 23, 59, 59, 999).toISOString() : null;
}
