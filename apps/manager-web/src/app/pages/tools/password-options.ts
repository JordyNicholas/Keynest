export const minPasswordLength = 8;
export const maxPasswordLength = 128;

export const characterGroups = [
  { id: 'lower', label: 'Lowercase letters', characters: 'abcdefghijklmnopqrstuvwxyz' },
  { id: 'upper', label: 'Uppercase letters', characters: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' },
  { id: 'digits', label: 'Numbers', characters: '0123456789' },
  { id: 'symbols', label: 'Symbols', characters: '!@#$%^&*()-_=+[]{}?,.' },
] as const;

export type CharacterGroup = typeof characterGroups[number]['id'];
export type CharacterSelection = Record<CharacterGroup, boolean>;

export function validPasswordLength(length: number | null): length is number {
  return length !== null && Number.isInteger(length) && length >= minPasswordLength && length <= maxPasswordLength;
}
