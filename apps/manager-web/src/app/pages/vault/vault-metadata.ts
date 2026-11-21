import { BuiltInPolicy } from '../tools/built-in-policy';
import { VaultItem } from './vault-item';

export type ExpiryFilter = 'all' | 'attention' | 'expired' | 'soon' | 'upcoming' | 'later' | 'unset';
export type SearchField = 'all' | 'label' | 'url' | 'username';
export type VaultOrder = 'label' | 'expiry';
export type ExpiryCounts = { total: number; expired: number; soon: number; upcoming: number; later: number; unset: number };
export type VaultPage = {
  asOf: string; items: VaultItem[]; totalMatches: number; page: number; size: number; totalPages: number; counts: ExpiryCounts;
};
export type VaultSummary = Pick<VaultItem, 'id' | 'label' | 'url' | 'expiresAt' | 'createdAt' | 'updatedAt'>;
export type VaultOverview = {
  asOf: string; counts: ExpiryCounts; nextExpiry: VaultSummary | null; attention: VaultSummary[]; recent: VaultSummary[]; policy: BuiltInPolicy;
};
export const expiryFilters: { id: ExpiryFilter; label: string; count: (counts: ExpiryCounts) => number }[] = [
  { id: 'all', label: 'All items', count: counts => counts.total },
  { id: 'attention', label: 'Need attention', count: counts => counts.expired + counts.soon },
  { id: 'expired', label: 'Expired', count: counts => counts.expired },
  { id: 'soon', label: 'Less than 14 days', count: counts => counts.soon },
  { id: 'upcoming', label: '14–29 days', count: counts => counts.upcoming },
  { id: 'later', label: '30+ days', count: counts => counts.later },
  { id: 'unset', label: 'No expiry', count: counts => counts.unset },
];

/** Classification uses the exact remaining duration; rounded labels never decide buckets. */
export function expiryStatus(expiresAt: string | undefined, asOf: number) {
  const expiry = expiresAt ? Date.parse(expiresAt) : NaN;
  if (!Number.isFinite(expiry)) return { tone: 'neutral', label: 'No expiry' };
  const remaining = expiry - asOf;
  if (remaining <= 0) return { tone: 'urgent', label: 'Expired' };
  const day = 86_400_000;
  const tone = remaining >= 30 * day ? 'healthy' : remaining >= 14 * day ? 'warning' : 'urgent';
  const days = Math.floor(remaining / day);
  return { tone, label: days === 0 ? 'Less than a day left' : days === 1 ? '1 day left' : `${days} days left` };
}
