import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { GeneratePasswordPageComponent } from './generate-password.component';

const policy = { id: 'keynest.default.v1', rules: { minLength: 16, maxLength: 128, requireLower: true,
  requireUpper: true, requireDigit: true, requireSymbol: true, minCategories: 3, bannedSubstrings: [], bannedRegexes: [] } };

describe('Password tools in context', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api/v1' }] });
    TestBed.inject(AuthService).setSession('test-session', 'sample@example.test');
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify();TestBed.inject(AuthService).clearSession(); });

  it('shows two choices without loading policies or either tool before selection', async () => {
    const harness = await RouterTestingHarness.create('/tools');
    expect(harness.routeNativeElement!.querySelectorAll('mat-expansion-panel-header').length).toBe(2);
    expect(harness.routeNativeElement!.querySelector('kn-generate-password-page, kn-check-password-page')).toBeNull();
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('opens the active tools through their canonical query destinations', async () => {
    const harness = await RouterTestingHarness.create('/tools?tool=generate#generate');
    const http = TestBed.inject(HttpTestingController);http.expectOne('/api/v1/tools/policy').flush(policy);
    expect(harness.routeNativeElement!.querySelector('kn-generate-password-page')).not.toBeNull();
    await harness.navigateByUrl('/tools?tool=check#why-these-rules');
    http.expectOne('/api/v1/tools/policy').flush(policy);harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('kn-check-password-page')).not.toBeNull();
    expect(harness.routeNativeElement!.querySelector('kn-generate-password-page')).toBeNull();
  });

  it('clears an unsubmitted sample immediately when its section closes', async () => {
    const harness = await RouterTestingHarness.create('/tools?tool=check#check');
    TestBed.inject(HttpTestingController).expectOne('/api/v1/tools/policy').flush(policy);harness.detectChanges();
    const input = harness.routeNativeElement!.querySelector<HTMLInputElement>('#policy-sample')!;
    input.value = 'synthetic unsubmitted sample';input.dispatchEvent(new Event('input'));
    harness.routeNativeElement!.querySelectorAll<HTMLElement>('mat-expansion-panel-header')[1].click();harness.detectChanges();
    await harness.fixture.whenStable();expect(input.value).toBe('');
    TestBed.inject(HttpTestingController).expectNone('/api/v1/tools/validate?operation=update');
  });

  it('clears generated values when switching tools and clears samples/cancels checks when closing', async () => {
    const harness = await RouterTestingHarness.create('/tools?tool=generate#generate');
    const http = TestBed.inject(HttpTestingController);http.expectOne('/api/v1/tools/policy').flush(policy);harness.detectChanges();
    const generator = harness.fixture.debugElement.query(By.directive(GeneratePasswordPageComponent)).componentInstance as GeneratePasswordPageComponent;
    generator.generated.set('synthetic generated value');harness.detectChanges();
    harness.routeNativeElement!.querySelectorAll<HTMLElement>('mat-expansion-panel-header')[1].click();
    harness.detectChanges();await harness.fixture.whenStable();harness.detectChanges();
    http.expectOne('/api/v1/tools/policy').flush(policy);harness.detectChanges();
    expect(generator.generated()).toBe('');
    expect(harness.routeNativeElement!.querySelectorAll('mat-expansion-panel-header[aria-expanded=true]').length).toBe(1);
    const input = harness.routeNativeElement!.querySelector<HTMLInputElement>('#policy-sample')!;
    input.value = 'synthetic sample password';input.dispatchEvent(new Event('input'));harness.detectChanges();
    const check = Array.from(harness.routeNativeElement!.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Check password')!;
    check.click();const pending = http.expectOne('/api/v1/tools/validate?operation=update');
    harness.routeNativeElement!.querySelectorAll<HTMLElement>('mat-expansion-panel-header')[1].click();
    harness.detectChanges();await harness.fixture.whenStable();
    expect(input.value).toBe('');expect(pending.cancelled).toBeTrue();
    expect(harness.routeNativeElement!.querySelector('kn-check-password-page')).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/tools');
  });
});
