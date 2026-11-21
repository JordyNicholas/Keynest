import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../injectionTokens';
import { AuthService } from '../../auth/auth.service';
import { OverviewPageComponent } from './overview.component';
import { emptyOverview, sampleOverview } from './overview-test-data';

describe('Owner-specific account overview', () => {
  const endpoint = '/api/v1/vault/overview';
  let fixture: ComponentFixture<OverviewPageComponent>;
  let component: OverviewPageComponent;
  let http: HttpTestingController;
  let initial: TestRequest;
  beforeEach(() => {
    sessionStorage.clear();sessionStorage.setItem('keynest.token', 'session-token');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: API_BASE_URL, useValue: '/api/v1' }] });
    fixture = TestBed.createComponent(OverviewPageComponent);component = fixture.componentInstance;http = TestBed.inject(HttpTestingController);
    initial = http.expectOne(endpoint);expect(initial.request.method).toBe('GET');expect(initial.request.headers.get('Authorization')).toBe('Bearer session-token');
    fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy();http.verify();TestBed.inject(AuthService).clearSession(); });

  it('announces loading without invented zeroes and renders only real snapshot metrics and destinations', () => {
    const host: HTMLElement = fixture.nativeElement;
    expect(host.textContent).toContain('Loading your overview');expect(host.querySelector('kn-overview-metrics')).toBeNull();
    initial.flush(sampleOverview());fixture.detectChanges();
    expect(Array.from(host.querySelectorAll('.metric-value strong'), element => element.textContent?.trim())).toEqual(['3', '2', '2']);
    expect(host.querySelector('.expired-badge')?.textContent).toContain('1 expired');
    expect(host.querySelector('.metric.attention')?.getAttribute('href')).toBe('/vault?expiry=attention');
    expect(host.querySelector('.metric:last-child')?.getAttribute('href')).toBe('/vault?item=' + sampleOverview().nextExpiry!.id);
    const groups = Array.from(host.querySelectorAll('.legend a'), element => element.getAttribute('href'));
    expect(groups).toEqual(['/vault?expiry=expired', '/vault?expiry=soon', '/vault?expiry=upcoming', '/vault?expiry=later', '/vault?expiry=unset']);
    expect(host.textContent).toContain('Recent item changes');expect(host.textContent).toContain('not a password strength');
    http.expectNone('/api/v1/vault/items');http.expectNone('/api/v1/tools/policy');http.expectNone('/api/v1/ai/policy/explain');
  });

  it('shows a useful first step for an empty account instead of a sample dashboard', () => {
    initial.flush(emptyOverview());fixture.detectChanges();const host: HTMLElement = fixture.nativeElement;
    expect(host.textContent).toContain('first password');expect(host.querySelector('kn-overview-metrics')).toBeNull();
    expect(host.querySelector('.empty a[href="/vault/new"]')).not.toBeNull();expect(host.querySelector('.empty a[href="/account#password-policy-heading"]')).not.toBeNull();
  });

  it('keeps no upcoming expiry and no attention distinct from zero days or a safety guarantee', () => {
    const data = sampleOverview();data.counts = { total: 3, expired: 0, soon: 0, upcoming: 0, later: 0, unset: 3 };
    data.nextExpiry = null;data.attention = [];data.recent = data.recent.map(item => ({ ...item, expiresAt: undefined }));
    initial.flush(data);fixture.detectChanges();const host: HTMLElement = fixture.nativeElement;
    expect(host.textContent).toContain('No upcoming expiry');expect(host.textContent).toContain('No items need attention');
    expect(host.querySelectorAll('.summary-grid > a').length).toBe(2);expect(host.querySelector('.metric-unit')).toBeNull();
  });

  it('summarizes the selected policy without redundant category requirements or fabricated activity', () => {
    const data = sampleOverview();data.policy.id = 'keynest.account.synthetic';data.policy.rules.minLength = 24;data.policy.rules.maxLength = 64;
    initial.flush(data);fixture.detectChanges();const host: HTMLElement = fixture.nativeElement;
    const policy = host.querySelector('[aria-labelledby=policy-heading]')!;
    expect(policy.textContent).toContain('Custom account policy');expect(policy.textContent).toContain('24–64 characters');
    expect(policy.textContent).not.toContain('At least 3');expect(host.querySelector('.recent-list')?.textContent).toContain('Updated');
    expect(host.querySelector('.recent-list')?.textContent).toContain('Created');
    expect(host.textContent).toContain('rather than a complete activity history');
  });

  it('keeps failures visible, offers retry and replaces a refresh with an honest loading state', () => {
    initial.flush({}, { status: 503, statusText: 'Unavailable' });fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('temporarily busy');
    component.refresh();fixture.detectChanges();expect(fixture.nativeElement.querySelector('kn-overview-metrics')).toBeNull();
    http.expectOne(endpoint).flush(sampleOverview());fixture.detectChanges();expect(fixture.nativeElement.querySelector('[role=alert]')).toBeNull();
    component.refresh();expect(component.overview()).toBeNull();http.expectOne(endpoint).flush(emptyOverview());
  });

  it('cancels older requests and clears account data on replacement, logout and destruction', () => {
    component.refresh();expect(initial.cancelled).toBeTrue();const latest = http.expectOne(endpoint);latest.flush(sampleOverview());
    expect(component.overview()?.counts.total).toBe(3);TestBed.inject(AuthService).setSession('other-session', 'other@example.test');fixture.detectChanges();
    expect(component.overview()).toBeNull();expect(component.error()).toContain('session changed');
    component.refresh();const pending = http.expectOne(endpoint);fixture.destroy();expect(pending.cancelled).toBeTrue();expect(component.overview()).toBeNull();
  });

  it('discards a response after immediate sign-out and does not mistake authentication failure for an empty account', () => {
    TestBed.inject(AuthService).clearSession();initial.flush(sampleOverview());expect(component.overview()).toBeNull();fixture.detectChanges();
    expect(component.error()).toContain('session ended');
    TestBed.inject(AuthService).setSession('new-session', 'sample@example.test');fixture.detectChanges();component.refresh();
    http.expectOne(endpoint).flush({}, { status: 401, statusText: 'Unauthorized' });fixture.detectChanges();
    expect(TestBed.inject(AuthService).signedIn()).toBeFalse();expect(fixture.nativeElement.querySelector('.empty')).toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/account?returnTo=%2Foverview"]')).not.toBeNull();
  });
});
