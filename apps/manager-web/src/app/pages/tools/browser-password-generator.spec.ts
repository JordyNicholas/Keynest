import { generateBrowserPassword } from './browser-password-generator';

describe('Browser password generator', () => {
  const allTypes = { lower: true, upper: true, digits: true, symbols: true };

  it('honors length and includes every selected character type', () => {
    const password = generateBrowserPassword(8, allTypes, '');
    expect(password.length).toBe(8);
    expect(password).toMatch(/[a-z]/);
    expect(password).toMatch(/[A-Z]/);
    expect(password).toMatch(/[0-9]/);
    expect(password).toMatch(/[!@#$%^&*()_=+\[\]{},?.-]/);
    expect(generateBrowserPassword(8, { lower: false, upper: false, digits: true, symbols: false }, ''))
      .toMatch(/^\d{8}$/);
  });

  it('includes a transformed keyword with room for independent random characters', () => {
    const password = generateBrowserPassword(16, allTypes, 'unique');
    expect(password.length).toBe(16);
    expect(password).toMatch(/[uU][nN][iI!1][qQ][uU][eE3]/);
    expect(() => generateBrowserPassword(13, allTypes, 'unique')).toThrowError(/eight random characters/);
  });

  it('rejects a length below eight or an empty selection', () => {
    expect(() => generateBrowserPassword(7, allTypes, '')).toThrowError(/length/);
    expect(() => generateBrowserPassword(8, { lower: false, upper: false, digits: false, symbols: false }, ''))
      .toThrowError(/character type/);
  });
  it('applies ambiguous-character restrictions to random characters and transformed keywords', () => {
    for (let attempt = 0; attempt < 100; attempt++) {
      const password = generateBrowserPassword(20, allTypes, 'keyword', { excludeAmbiguous: true });
      expect(password.length).toBe(20);
      expect(password).not.toMatch(/[0OIl1]/);
    }
  });

  it('uses custom restrictions and refuses an unsatisfiable keyword instead of returning a rejected password', () => {
    const lowerOnly = { lower: true, upper: false, digits: false, symbols: false };
    expect(() => generateBrowserPassword(20, lowerOnly, 'keyword', { bannedSubstrings: ['keyword'] }))
      .toThrowError(/conflicts/);
    expect(() => generateBrowserPassword(20, lowerOnly, 'keyword', { username: 'keyword@example.test' }))
      .toThrowError(/conflicts/);
    expect(generateBrowserPassword(20, lowerOnly, 'keyword', {
      username: 'keyword@example.test', forbidUsernameSubstring: false,
    })).toContain('keyword');
    expect(() => generateBrowserPassword(20, lowerOnly, 'aaaa', { bannedRegexes: ['(.)\\1{3,}'] }))
      .toThrowError(/conflicts/);
  });

});
