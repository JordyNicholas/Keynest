const messages = new Map([
  ['password.too_short', 'Use a longer password.'],
  ['password.too_long', 'Use a shorter password.'],
  ['password.whitespace_forbidden', 'Remove whitespace.'],
  ['password.ambiguous_chars', 'Remove ambiguous characters.'],
  ['password.require_upper', 'Add an uppercase letter.'],
  ['password.require_lower', 'Add a lowercase letter.'],
  ['password.require_digit', 'Add a digit.'],
  ['password.require_symbol', 'Add a symbol.'],
  ['password.min_categories', 'Use more character categories.'],
  ['password.contains_username', 'Remove the username from the password.'],
  ['password.banned_substring', 'Avoid disallowed text.'],
  ['password.banned_regex', 'Avoid disallowed patterns.'],
]);

export function passwordViolationMessages(codes: unknown): string[] {
  if (!Array.isArray(codes)) return [];
  return codes.flatMap((code) => typeof code === 'string' ? messages.get(code) ?? [] : []);
}
