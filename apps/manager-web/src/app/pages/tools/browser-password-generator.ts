import { characterGroups, CharacterSelection, minPasswordLength, validPasswordLength } from './password-options';

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

export function generateBrowserPassword(length: number | null, selection: CharacterSelection, keyword: string): string {
  if (!validPasswordLength(length)) {
    throw new Error('Choose a length between 8 and 128.');
  }

  const groups = characterGroups.filter(({ id }) => selection[id]);
  if (!groups.length) throw new Error('Choose at least one character type.');
  const pool = groups.map(({ characters }) => characters).join('');

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

  const random = groups.map(({ characters }) => randomCharacter(characters));
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
