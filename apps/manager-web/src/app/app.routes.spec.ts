import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { MatDialog } from '@angular/material/dialog';
import { AuthDialogComponent } from './auth/auth-dialog.component';
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

  it('opens public product information without making any API request', async () => {
    const harness = await RouterTestingHarness.create('/');
    expect(TestBed.inject(Router).url).toBe('/');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain('Your passwords.');
    expect(harness.routeNativeElement?.textContent).toContain('Design preview with synthetic data');
    expect(harness.routeNativeElement?.querySelector('form')).toBeNull();
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('keeps anonymous generation on its dedicated route and fetches only the rules', async () => {
    const harness = await RouterTestingHarness.create('/');
    const generator = harness.routeNativeElement!.querySelector<HTMLAnchorElement>('a[href="/tools/generate"]')!;
    expect(generator.textContent).toContain('Try the generator');
    await harness.navigateByUrl(generator.getAttribute('href')!);
    expect(TestBed.inject(Router).url).toBe('/tools/generate');
    expect(harness.routeNativeElement?.textContent).toContain('Sign in to Manager');
    const http = TestBed.inject(HttpTestingController);
    const policy = http.expectOne('/api/v1/tools/policy');
    expect(policy.request.headers.has('Authorization')).toBeFalse();
    policy.flush({ id: 'keynest.default.v1', rules: { minLength: 16, maxLength: 128, bannedSubstrings: [] } });
    http.expectNone('/api/v1/tools/generate');
  });

  it('keeps public section destinations and account entry reachable without account data', async () => {
    const harness = await RouterTestingHarness.create('/#security');
    const host = harness.routeNativeElement!;
    for (const section of ['how', 'security', 'documentation']) {
      const link = host.querySelector<HTMLAnchorElement>(`nav a[href="/#${section}"]`)!;
      expect(link).not.toBeNull();
      expect(host.querySelector('#' + section)).not.toBeNull();
      await harness.navigateByUrl(link.getAttribute('href')!);
      expect(TestBed.inject(Router).url).toBe('/#' + section);
    }
    const entry = host.querySelector<HTMLAnchorElement>('a[href="/?auth=signin"]')!;
    expect(entry.textContent?.trim()).toBe('Sign in');
    expect(host.querySelector('a[href="/?auth=signup"]')?.textContent).toContain('Start your vault');
    await harness.navigateByUrl(entry.getAttribute('href')!);
    await harness.fixture.whenStable();
    const dialog = TestBed.inject(MatDialog).openDialogs[0];
    expect(dialog.componentInstance.mode()).toBe('signin');
    dialog.close();await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/');
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('opens direct signup entry and cancels pending authentication before the closing animation', async () => {
    const harness = await RouterTestingHarness.create('/?auth=signup');
    await harness.fixture.whenStable();
    const dialog = TestBed.inject(MatDialog).openDialogs[0];
    const component = dialog.componentInstance as AuthDialogComponent;
    expect(component.mode()).toBe('signup');
    const form = component.form()!;
    form.email.set('sample@example.test');form.password.set('Sample master password');form.submit();
    const request = TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/register');
    dialog.close();expect(request.cancelled).toBeTrue();expect(form.password()).toBe('');
    await harness.fixture.whenStable();expect(TestBed.inject(AuthService).signedIn()).toBeFalse();
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('resumes a guarded Vault deep link using the shared direct-account fallback', async () => {
    const harness = await RouterTestingHarness.create('/vault/new');
    const router = TestBed.inject(Router);
    expect(router.url).toBe('/account?returnTo=%2Fvault%2Fnew');
    harness.detectChanges();
    const form = harness.routeNativeElement!.querySelector('form')!;
    const email = form.querySelector<HTMLInputElement>('input[type=email]')!;
    const password = form.querySelector<HTMLInputElement>('input[type=password]')!;
    email.value = 'sample@example.test';email.dispatchEvent(new Event('input'));
    password.value = 'Legacy password';password.dispatchEvent(new Event('input'));
    form.dispatchEvent(new Event('submit', { cancelable: true }));
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/auth/login').flush({ token: 'test-session', email: 'sample@example.test' });
    await harness.fixture.whenStable();
    expect(router.url).toBe('/vault/new');
    http.expectOne('/api/v1/tools/policy').flush({ id: 'keynest.default.v1', rules: { minLength: 16, maxLength: 128, bannedSubstrings: [] } });
  });

  it('switches dialog modes without retaining the password and signs in without putting credentials in navigation', async () => {
    const harness = await RouterTestingHarness.create('/?auth=signup&returnTo=%2Fvault');
    await harness.fixture.whenStable();
    const dialog = TestBed.inject(MatDialog).openDialogs[0];
    const component = dialog.componentInstance as AuthDialogComponent;
    const form = component.form()!;
    form.password.set('temporary password');form.switchMode();harness.detectChanges();await harness.fixture.whenStable();
    expect(form.password()).toBe('');expect(component.mode()).toBe('signin');
    expect(TestBed.inject(Router).url).toBe('/?auth=signin&returnTo=%2Fvault');
    form.email.set('sample@example.test');form.password.set('Legacy password');form.submit();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/auth/login').flush({ token: 'test-session', email: 'sample@example.test' });
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/vault');
    expect(TestBed.inject(MatDialog).openDialogs.length).toBe(0);
    expect(JSON.stringify(history.state)).not.toContain('test-session');expect(JSON.stringify(history.state)).not.toContain('Legacy password');
    http.expectOne('/api/v1/vault/items').flush([]);
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
