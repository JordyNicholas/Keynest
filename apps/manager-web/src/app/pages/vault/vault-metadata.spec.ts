import { expiryStatus } from './vault-metadata';

describe('Expiry labels use exact duration boundaries', () => {
  const now = Date.parse('2026-11-21T12:00:00Z');
  it('keeps rounding separate from the 14/30-day groups and handles absent/invalid expiry', () => {
    const samples = [30, 29.9, 14, 13.9, 0.5, 0];
    expect(samples.map(days => expiryStatus(new Date(now + days * 86400000).toISOString(), now))).toEqual([
      { tone: 'healthy', label: '30 days left' }, { tone: 'warning', label: '29 days left' },
      { tone: 'warning', label: '14 days left' }, { tone: 'urgent', label: '13 days left' },
      { tone: 'urgent', label: 'Less than a day left' }, { tone: 'urgent', label: 'Expired' },
    ]);
    expect(expiryStatus(undefined, now)).toEqual({ tone: 'neutral', label: 'No expiry' });
    expect(expiryStatus('invalid', now)).toEqual({ tone: 'neutral', label: 'No expiry' });
  });
});
