import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  afterEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  function activateVault() {
    return TestBed.runInInjectionContext(() => authGuard(
      {} as ActivatedRouteSnapshot,
      { url: '/vault' } as RouterStateSnapshot
    ));
  }

  function expectAccountRedirect(): void {
    const result = activateVault();
    expect(result instanceof UrlTree).toBeTrue();
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/account');
  }

  it('redirects a signed-out visitor to the account route', () => {
    expectAccountRedirect();
  });

  it('allows a restored tab session to enter the vault', () => {
    sessionStorage.setItem('keynest.token', 'tab-session-token');
    expect(activateVault()).toBeTrue();
  });

  it('rejects a legacy localStorage bearer and removes it', () => {
    localStorage.setItem('keynest.token', 'legacy-token');
    expectAccountRedirect();
    expect(localStorage.getItem('keynest.token')).toBeNull();
  });

  it('redirects again after the tab session is cleared', () => {
    const auth = TestBed.inject(AuthService);
    auth.setSession('tab-session-token', 'user@example.com');
    expect(activateVault()).toBeTrue();

    auth.clearSession();

    expectAccountRedirect();
  });
});
