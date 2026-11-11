import { AuthService } from './auth.service';

describe('AuthService', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('stores the bearer only for the browser tab session', () => {
    const service = new AuthService();

    service.setSession('opaque-token', 'user@example.com');

    expect(sessionStorage.getItem('keynest.token')).toBe('opaque-token');
    expect(localStorage.getItem('keynest.token')).toBeNull();
  });

  it('deletes a legacy localStorage token instead of restoring it', () => {
    localStorage.setItem('keynest.token', 'legacy-token');

    const service = new AuthService();

    expect(service.token()).toBe('');
    expect(localStorage.getItem('keynest.token')).toBeNull();
  });

  it('clears session and legacy storage on logout', () => {
    const service = new AuthService();
    service.setSession('opaque-token', 'user@example.com');
    localStorage.setItem('keynest.token', 'legacy-token');

    service.clearSession();

    expect(service.token()).toBe('');
    expect(sessionStorage.getItem('keynest.token')).toBeNull();
    expect(localStorage.getItem('keynest.token')).toBeNull();
  });
});
