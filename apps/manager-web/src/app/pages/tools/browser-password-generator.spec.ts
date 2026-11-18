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
});
