import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { AuthService } from './auth/auth.service';
import { API_BASE_URL } from './injectionTokens';

describe('Manager entry routes', () => {
  beforeEach(() => {
    sessionStorage.removeItem('keynest.token');
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    TestBed.inject(AuthService).clearSession();
  });

  it('opens the public generator and fetches only the default rule document on entry', async () => {
    const harness = await RouterTestingHarness.create('/');
    expect(TestBed.inject(Router).url).toBe('/tools/generate');
    expect(harness.routeNativeElement?.textContent).toContain('Sign in to Manager');
    const http = TestBed.inject(HttpTestingController);
    const policy = http.expectOne('/api/v1/tools/policy');
    expect(policy.request.headers.has('Authorization')).toBeFalse();
    policy.flush({ id: 'keynest.default.v1', rules: { minLength: 16, maxLength: 128, bannedSubstrings: [] } });
    http.expectNone('/api/v1/tools/generate');
  });

  it('redirects the legacy explanation page and resumes through Account settings', async () => {
    TestBed.inject(AuthService).setSession('test-session', 'demo@example.test');
    const harness = await RouterTestingHarness.create('/policy-explanation');
    const http = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    expect(router.url).toBe('/tools/check#why-these-rules');
    const policy = { id: 'keynest.default.v1', rules: { minLength: 16, maxLength: 128, bannedSubstrings: [] } };
    http.expectOne('/api/v1/tools/policy').flush(policy);
    harness.detectChanges();

    const setup = harness.routeNativeElement!.querySelector<HTMLAnchorElement>('a[href^="/account?ai="]')!;
    await harness.navigateByUrl(setup.getAttribute('href')!);
    expect(router.url).toBe('/account?ai=policy-explanation#ai-settings');
    http.expectOne('/api/v1/auth/me').flush({ email: 'demo@example.test' });
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector<HTMLDetailsElement>('#ai-settings details')?.open).toBeTrue();
    http.expectNone('/api/v1/ai/policy/explain');

    const input = harness.routeNativeElement!.querySelector<HTMLInputElement>('#provider-key')!;
    input.value = 'test-provider-key';
    input.form!.dispatchEvent(new Event('submit', { cancelable: true }));
    expect(input.value).toBe('');
    http.expectOne('/api/v1/ai/policy/explain').flush({ explanation: '<strong>Policy example</strong>' });
    await harness.fixture.whenStable();
    expect(router.url).toBe('/tools/check#why-these-rules');
    http.expectOne('/api/v1/tools/policy').flush(policy);
    harness.detectChanges();
    const inline = harness.routeNativeElement!.querySelector('[aria-labelledby=why-these-rules]')!;
    expect(inline.textContent).toContain('<strong>Policy example</strong>');
    expect(inline.querySelector('strong')).toBeNull();
    expect(JSON.stringify(history.state)).not.toContain('Policy example');
    expect(JSON.stringify(history.state)).not.toContain('test-provider-key');
  });

  it('opens the authenticated vault when a session is already present', async () => {
    TestBed.inject(AuthService).setSession('test-session', 'demo@example.test');
    await RouterTestingHarness.create('/');
    expect(TestBed.inject(Router).url).toBe('/vault');
    const request = TestBed.inject(HttpTestingController).expectOne('/api/v1/vault/items');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-session');
    request.flush([]);
  });
});
