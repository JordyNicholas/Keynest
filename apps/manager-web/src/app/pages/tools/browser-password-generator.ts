import { characterGroups, CharacterSelection, maxPasswordLength, minPasswordLength, validPasswordLength } from './password-options';

const substitutions: Readonly<Partial<Record<string, string>>> = {
  a: '@4',
  e: '3',
  i: '!1',
  o: '0',
  s: '$5',
  t: '7',
};

function randomIndex(size: number): number {
  const sample = new Uint32Array(1);
  const range = 2 ** 32;
  const limit = range - (range % size);
  do {
    crypto.getRandomValues(sample);
  } while (sample[0] >= limit);
  return sample[0] % size;
}

function randomCharacter(characters: string): string {
  return characters[randomIndex(characters.length)];
}

export function generateBrowserPassword(
  length: number | null,
  selection: CharacterSelection,
  keyword: string,
  { minLength = minPasswordLength, maxLength = maxPasswordLength, excludeAmbiguous = false } = {},
): string {
  if (!validPasswordLength(length) || length < minLength || length > maxLength) {
    throw new Error(`Choose a length between ${minLength} and ${maxLength}.`);
  }

  const groups = characterGroups
    .filter(({ id }) => selection[id])
    .map(({ characters }) => excludeAmbiguous ? characters.replace(/[0OIl1]/g, '') : characters);
  if (!groups.length) throw new Error('Choose at least one character type.');
  const pool = groups.join('');

  if (!/^[a-z0-9]*$/i.test(keyword)) {
    throw new Error('Use letters and numbers in the keyword.');
  }
  const randomLength = length - keyword.length;
  if (keyword && randomLength < minPasswordLength) {
    throw new Error('Increase the length to leave room for at least eight random characters beyond the keyword.');
  }

  const transformed = Array.from(keyword, (character) => {
    const lower = character.toLowerCase();
    const alternatives = new Set(lower + character.toUpperCase() + (substitutions[lower] ?? ''));
    const available = Array.from(alternatives).filter((variant) => pool.includes(variant)).join('');
    if (!available) throw new Error('Choose character types that can represent the keyword.');
    return randomCharacter(available);
  }).join('');

  const random = groups.map(randomCharacter);
  while (random.length < randomLength) {
    random.push(randomCharacter(pool));
  }
  for (let index = random.length - 1; index > 0; index--) {
    const other = randomIndex(index + 1);
    [random[index], random[other]] = [random[other], random[index]];
  }

  if (transformed) random.splice(randomIndex(random.length + 1), 0, transformed);
  return random.join('');
}
