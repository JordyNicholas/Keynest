const apiPath = /^\/(?:[A-Za-z0-9_-]+\/)*api\/v1$/;
const loopbackHosts = new Set(['localhost', '127.0.0.1', '[::1]']);

/** Public build configuration. Never accept credentials or request-specific URL parts. */
export function apiBaseUrl(value) {
  if (value === undefined) {
    return '/api/v1';
  }
  const invalid = () => new Error('KEYNEST_API_BASE_URL must be an API path or HTTPS URL ending in /api/v1 (HTTP is loopback-only)');
  if (typeof value !== 'string' || !value || /[\s\\%?#@]/u.test(value)) {
    throw invalid();
  }
  const normalized = value.replace(/\/$/, '');
  if (apiPath.test(normalized)) {
    return normalized;
  }
  if (!/^https?:\/\//.test(normalized)) {
    throw invalid();
  }
  let url;
  try {
    url = new URL(normalized);
  } catch {
    throw invalid();
  }
  if (!url.hostname || url.hostname.includes('replace-with') || url.username || url.password || !apiPath.test(url.pathname)
    || (url.protocol === 'http:' && !loopbackHosts.has(url.hostname))) {
    throw invalid();
  }
  // Reject paths normalized by URL parsing (such as dot segments).
  const suppliedPath = normalized.slice(normalized.indexOf('/', normalized.indexOf('://') + 3));
  if (suppliedPath !== url.pathname) {
    throw invalid();
  }
  return url.origin + url.pathname;
}
