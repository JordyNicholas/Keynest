import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { API_BASE_URL } from '../../injectionTokens';
import { AuthService } from '../../auth/auth.service';

describe('Inline API connection check', () => {
  const endpoint = '/api/v1/tools/health';
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api/v1' }] });
    TestBed.inject(AuthService).setSession('test-session', 'sample@example.test');
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify();TestBed.inject(AuthService).clearSession(); });

  it('loads on demand inside Account, renders the JSON response and stays in the same page', async () => {
    const harness = await RouterTestingHarness.create('/account');
    const http = TestBed.inject(HttpTestingController);http.expectOne('/api/v1/auth/me').flush({ email: 'sample@example.test' });http.expectNone(endpoint);
    const details = harness.routeNativeElement!.querySelector<HTMLDetailsElement>('#diagnostics-heading details')!;
    details.open = true;details.dispatchEvent(new Event('toggle'));harness.detectChanges();
    http.expectOne(endpoint).flush({ status: 'ok', product: 'keynest-manager-api', policyId: 'keynest.default.v1' });harness.detectChanges();
    expect(details.textContent).toContain('Connected to Manager API');expect(details.querySelector('pre')).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/account');
    expect(details.querySelector('.connection-summary')?.getAttribute('data-state')).toBe('connected');
  });

  it('opens the signed-in Account check and supports failure/retry', async () => {
    const harness = await RouterTestingHarness.create('/account#diagnostics-heading');const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/auth/me').flush({ email: 'sample@example.test' });
    expect(TestBed.inject(Router).url).toBe('/account#diagnostics-heading');
    http.expectOne(endpoint).flush({}, { status: 0, statusText: 'Offline' });harness.detectChanges();
    const check = harness.routeNativeElement!.querySelector<HTMLElement>('kn-api-health')!;
    expect(check.textContent).toContain('Connection not confirmed');
    Array.from(check.querySelectorAll('button')).find(button => button.textContent?.trim() === 'Check again')!.click();
    http.expectOne(endpoint).flush({ status: 'ok', product: 'keynest-manager-api', policyId: 'keynest.default.v1' });harness.detectChanges();
    expect(check.textContent).toContain('Connected to Manager API');
  });

  it('cancels connection work when the inline check closes', async () => {
    const harness = await RouterTestingHarness.create('/account#diagnostics-heading');const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/auth/me').flush({ email: 'sample@example.test' });
    const request = http.expectOne(endpoint);
    const details = harness.routeNativeElement!.querySelector<HTMLDetailsElement>('#diagnostics-heading details')!;
    details.open = false;details.dispatchEvent(new Event('toggle'));harness.detectChanges();
    expect(request.cancelled).toBeTrue();expect(details.querySelector('kn-api-health')).toBeNull();
  });
});
