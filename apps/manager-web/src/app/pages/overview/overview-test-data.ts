import { VaultOverview } from '../vault/vault-metadata';

/** Synthetic fixture imported only by tests; not part of the application dependency graph. */
export function sampleOverview(): VaultOverview {
  const expired = { id: '00000000-0000-0000-0000-000000000001', label: 'Sample email', url: 'https://mail.example.test', expiresAt: '2026-11-20T12:00:00Z', createdAt: '2026-11-19T12:00:00Z', updatedAt: '2026-11-19T12:00:00Z' };
  const next = { id: '00000000-0000-0000-0000-000000000002', label: 'Developer portal', url: 'https://dev.example.test', expiresAt: '2026-11-23T12:00:00Z', createdAt: '2026-11-19T12:00:00Z', updatedAt: '2026-11-21T10:00:00Z' };
  const later = { id: '00000000-0000-0000-0000-000000000003', label: 'Docs service', expiresAt: '2027-01-20T12:00:00Z', createdAt: '2026-11-18T12:00:00Z', updatedAt: '2026-11-18T12:00:00Z' };
  return {
    asOf: '2026-11-21T12:00:00Z', counts: { total: 3, expired: 1, soon: 1, upcoming: 0, later: 1, unset: 0 },
    nextExpiry: next, attention: [expired, next], recent: [next, expired, later],
    policy: { id: 'keynest.default.v1', rules: { minLength: 16, maxLength: 128, requireLower: true, requireUpper: true, requireDigit: true, requireSymbol: true,
      minCategories: 3, excludeAmbiguous: true, forbidWhitespace: true, forbidUsernameSubstring: true, bannedSubstrings: [], bannedRegexes: [] } },
  };
}

export function emptyOverview(): VaultOverview {
  return { ...sampleOverview(), counts: { total: 0, expired: 0, soon: 0, upcoming: 0, later: 0, unset: 0 }, nextExpiry: null, attention: [], recent: [] };
}
