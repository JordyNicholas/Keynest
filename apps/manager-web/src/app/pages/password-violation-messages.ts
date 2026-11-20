const messages = new Map([
  ['password.too_short', 'Use a longer password.'],
  ['password.too_long', 'Use a shorter password.'],
  ['password.whitespace_forbidden', 'Remove whitespace.'],
  ['password.ambiguous_chars', 'Remove 0, O, I, l and 1, or change the ambiguous-character option in your account policy.'],
  ['password.require_upper', 'Add an uppercase letter.'],
  ['password.require_lower', 'Add a lowercase letter.'],
  ['password.require_digit', 'Add a digit.'],
  ['password.require_symbol', 'Add a symbol.'],
  ['password.min_categories', 'Use the required mix of lowercase letters, uppercase letters, numbers and symbols.'],
  ['password.contains_username', 'Remove the username from the password.'],
  ['password.banned_substring', 'Remove the blocked strings listed in your account policy.'],
  ['password.banned_regex', 'Avoid four or more identical characters in a row.'],
]);

export function passwordViolationMessages(codes: unknown): string[] {
  if (!Array.isArray(codes)) return [];
  return codes.flatMap((code) => typeof code === 'string' ? messages.get(code) ?? [] : []);
}
