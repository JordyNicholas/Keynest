import { BuiltInPolicy } from './built-in-policy';
import { characterGroups, CharacterSelection } from './password-options';

export type PolicyCheck = {
  id: string;
  label: string;
  met: boolean | null;
  help?: string;
};

const categoryLabels = {
  lower: 'Include a lowercase letter',
  upper: 'Include an uppercase letter',
  digits: 'Include a number',
  symbols: 'Include a symbol',
};

const categoryCodes = {
  lower: 'password.require_lower',
  upper: 'password.require_upper',
  digits: 'password.require_digit',
  symbols: 'password.require_symbol',
};

export function checkCustomRules(sample: string, minimum: number, required: CharacterSelection): PolicyCheck[] {
  const characters = Array.from(sample);
  return [
    { id: 'length', label: `Use at least ${minimum} characters`, met: characters.length >= minimum },
    ...characterGroups.filter(({ id }) => required[id]).map(({ id, characters: group }) => ({
      id: categoryCodes[id],
      label: categoryLabels[id],
      met: characters.some((character) => group.includes(character)),
    })),
  ];
}

export function previewBuiltInPolicy(sample: string, rules: BuiltInPolicy['rules']): PolicyCheck[] {
  // Java's Unicode character classifications can differ from the browser's. Let the API
  // resolve categories the ASCII portion of the sample does not already satisfy.
  const hasNonAscii = /[^\x00-\x7f]/.test(sample);
  const categories: Record<keyof CharacterSelection, boolean> = {
    lower: /[a-z]/.test(sample),
    upper: /[A-Z]/.test(sample),
    digits: /[0-9]/.test(sample),
    symbols: /[\x00-\x7f]/.test(sample.replace(/[a-zA-Z0-9\x09-\x0d\x1c-\x20]/g, '')),
  };
  const required = {
    lower: rules.requireLower,
    upper: rules.requireUpper,
    digits: rules.requireDigit,
    symbols: rules.requireSymbol,
  };
  const knownCategoryCount = Object.values(categories).filter(Boolean).length;
  const containsAsciiWhitespace = /[\x09-\x0d\x1c-\x20]/.test(sample);
  const containsBannedString = rules.bannedSubstrings
    .some((text) => text.trim() && sample.toLowerCase().includes(text.toLowerCase()));

  const checks: PolicyCheck[] = [
    { id: 'length', label: `Use ${rules.minLength}–${rules.maxLength} characters`,
      met: sample.length >= rules.minLength && sample.length <= rules.maxLength },
    ...characterGroups.filter(({ id }) => required[id]).map(({ id }) => ({
      id: categoryCodes[id],
      label: categoryLabels[id],
      met: categories[id] || (hasNonAscii ? null : false),
    })),
  ];
  if (rules.minCategories > Object.values(required).filter(Boolean).length) {
    checks.push({ id: 'password.min_categories', label: `Use at least ${rules.minCategories} of lowercase, uppercase, numbers and symbols`,
      met: knownCategoryCount >= rules.minCategories || (hasNonAscii ? null : false) });
  }
  if (rules.excludeAmbiguous) {
    checks.push({ id: 'password.ambiguous_chars', label: 'Avoid ambiguous characters (0, O, I, l, 1)',
      help: 'These specific characters are excluded anywhere in the password because they can look alike when copied by hand. Change this in your account policy.',
      met: !/[0OIl1]/.test(sample) });
  }
  if (rules.forbidWhitespace) {
    checks.push({ id: 'password.whitespace_forbidden', label: 'Avoid whitespace',
      met: containsAsciiWhitespace ? false : hasNonAscii ? null : true });
  }
  if (rules.bannedSubstrings.length) {
    checks.push({ id: 'password.banned_substring', label: 'Avoid blocked words or strings', help: `Blocked anywhere, ignoring case: ${rules.bannedSubstrings.join(', ')}.`,
      met: containsBannedString ? false : hasNonAscii ? null : true });
  }
  if (rules.bannedRegexes === undefined || rules.bannedRegexes.length) checks.push({ id: 'password.banned_regex', label: 'Avoid blocked patterns', help: (rules.bannedRegexes ?? []).map(pattern => pattern === '(.)\\1{3,}' ? 'Four or more identical characters in a row, such as aaaa or !!!!.' : pattern).join(' '), met: null });
  return checks;
}

export function confirmBuiltInPolicy(checks: PolicyCheck[], valid: boolean, violations: string[]): PolicyCheck[] {
  const confirmed = checks.map((check) => ({
    ...check,
    met: check.id === 'length'
      ? !violations.includes('password.too_short') && !violations.includes('password.too_long')
      : !violations.includes(check.id),
  }));
  if (!valid && confirmed.every(({ met }) => met)) {
    confirmed.push({ id: 'api', label: 'Pass the Manager API’s policy check', met: false });
  }
  return confirmed;
}
