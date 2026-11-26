import { readFile, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { apiBaseUrl } from './api-base-url.mjs';
import { isDeepStrictEqual } from 'node:util';
import { isIP } from 'node:net';

export const policyFile = 'web-security.json';
export const staticHeaders = Object.freeze({
  'Content-Security-Policy': "frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'",
  'X-Frame-Options': 'DENY',
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
});

export function securityPolicy(destination) {
  const api = apiBaseUrl(destination);
  const origin = api.startsWith('/') ? '' : new URL(api).origin;
  if (origin) {
    const host = new URL(api).hostname;
    if (host.length > 253 || host.split('.').some(label => label.length > 63)
      || !/^[a-z0-9-]+(?:\.[a-z0-9-]+)*$/i.test(host)
      || (isIP(host) && host !== '127.0.0.1')) {
      throw new Error('Production Web CSP requires a DNS API hostname or a same-origin proxy');
    }
  }
  const document = `default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'${origin ? ' ' + origin : ''}; base-uri 'self'; object-src 'none'; form-action 'self'`;
  return { schemaVersion: 1, apiBaseUrl: api, document,
    responseHeaders: { ...staticHeaders, 'Content-Security-Policy': document + "; frame-ancestors 'none'" } };
}

export function validatePolicy(value) {
  if (!value || value.schemaVersion !== 1 || typeof value.apiBaseUrl !== 'string'
    || !isDeepStrictEqual(value, securityPolicy(value.apiBaseUrl))) {
    throw new Error('Web policy artifact does not match its validated build destination');
  }
  return value;
}

const escapeAttribute = value => value.replace(/[&"<>]/g, char => ({ '&': '&amp;', '"': '&quot;', '<': '&lt;', '>': '&gt;' })[char]);
export const policyMeta = policy => `<meta http-equiv="Content-Security-Policy" content="${escapeAttribute(policy.document)}">`;
export async function installPolicy(directory, destination) {
  const policy = securityPolicy(destination);
  const path = join(directory, 'index.html');
  const html = await readFile(path, 'utf8');
  if ((html.match(/<head\b[^>]*>/gi) ?? []).length !== 1 || /http-equiv\s*=\s*["']?content-security-policy/i.test(html)) {
    throw new Error('Production HTML needs one head and no pre-existing CSP meta');
  }
  const meta = policyMeta(policy);
  await writeFile(path, html.replace(/<head\b[^>]*>/i, head => head + meta));
  await writeFile(join(directory, policyFile), JSON.stringify(policy, null, 2) + '\n');
}

export function productionOutput(root, config, args) {
  const option = (name, short) => {
    let value;
    for (let i = 0; i < args.length; i++) {
      if (args[i].startsWith(name + '=')) value = args[i].slice(name.length + 1);
      else if (args[i] === name || args[i] === short) value = args[++i];
    }
    return value;
  };
  const build = config.projects['manager-web'].architect.build;
  const configuration = option('--configuration', '-c') ?? build.defaultConfiguration;
  if (!configuration.split(',').includes('production')) return null;
  const output = option('--output-path') ?? build.configurations.production.outputPath ?? build.options.outputPath;
  const base = typeof output === 'string' ? output : output.base;
  const browser = typeof output === 'string' ? 'browser' : output.browser ?? 'browser';
  if (typeof base !== 'string' || typeof browser !== 'string') throw new Error('Unsupported production output path');
  return resolve(root, base, browser);
}
