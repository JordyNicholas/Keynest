import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../injectionTokens';
import { AuthFormComponent } from './auth-form.component';
import { AuthService } from './auth.service';

describe('Shared account-entry form', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api/v1' }] });
  });
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    TestBed.inject(AuthService).clearSession();
  });

  function setup(mode: 'signup' | 'signin' = 'signin') {
    const fixture = TestBed.createComponent(AuthFormComponent);
    fixture.componentRef.setInput('mode', mode);fixture.detectChanges();
    const form = fixture.componentInstance;
    form.email.set('sample@example.test');form.password.set('Sample master password');
    return { fixture, form, http: TestBed.inject(HttpTestingController), auth: TestBed.inject(AuthService) };
  }

  it('validates email and applies the Unicode-aware minimum only to registration', () => {
    const { form, fixture, http } = setup('signup');
    form.email.set('invalid');form.submit();expect(form.error()).toContain('valid email');
    form.email.set('sample@example.test');form.password.set('🔑'.repeat(14));form.submit();expect(form.error()).toContain('15 characters');
    http.expectNone('/api/v1/auth/register');
    fixture.componentRef.setInput('mode', 'signin');fixture.detectChanges();
    form.password.set('🔑'.repeat(14));form.submit();
    const request = http.expectOne('/api/v1/auth/login');expect(request.request.body.password).toBe('🔑'.repeat(14));
    request.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(form.error()).toBe('The email address or Keynest password is incorrect.');
  });

  it('submits once, announces pending state, then establishes a session and clears the password', async () => {
    const { form, fixture, http, auth } = setup('signup');const complete = jasmine.createSpy('authenticated');form.authenticated.subscribe(complete);
    form.submit();form.submit();fixture.detectChanges();await fixture.whenStable();fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('form').getAttribute('aria-busy')).toBe('true');
    expect(fixture.nativeElement.querySelector('input').disabled).toBeTrue();
    const request = http.expectOne('/api/v1/auth/register');
    request.flush({ token: 'tab-session', email: 'sample@example.test' });
    expect(auth.token()).toBe('tab-session');expect(form.password()).toBe('');expect(form.pending()).toBeFalse();expect(complete).toHaveBeenCalledTimes(1);
  });

  it('cancels immediately and discards fields without establishing a session', () => {
    const { form, http, auth } = setup();form.submit();const request = http.expectOne('/api/v1/auth/login');
    form.cancel();expect(request.cancelled).toBeTrue();expect(form.email()).toBe('');expect(form.password()).toBe('');expect(form.pending()).toBeFalse();expect(auth.signedIn()).toBeFalse();
  });

  it('cancels pending requests on destruction and when another session replaces the attempt', () => {
    const { form, fixture, http, auth } = setup();form.submit();const request = http.expectOne('/api/v1/auth/login');
    auth.setSession('other-session', 'other@example.test');fixture.detectChanges();
    expect(request.cancelled).toBeTrue();expect(auth.token()).toBe('other-session');
    form.email.set('sample@example.test');form.password.set('sample-password');form.submit();
    const second = http.expectOne('/api/v1/auth/login');fixture.destroy();expect(second.cancelled).toBeTrue();expect(form.password()).toBe('');
  });

  it('ignores a stale response before a session-change effect runs', () => {
    const { form, http, auth } = setup();form.submit();const request = http.expectOne('/api/v1/auth/login');
    auth.setSession('other-session', 'other@example.test');request.flush({ token: 'late-session', email: 'sample@example.test' });
    expect(auth.token()).toBe('other-session');
  });

  it('clears a cancelled attempt on mode change and permits retry after failure', () => {
    const { form, fixture, http } = setup();form.submit();const request = http.expectOne('/api/v1/auth/login');
    form.switchMode();fixture.detectChanges();expect(request.cancelled).toBeTrue();expect(form.password()).toBe('');
    expect(fixture.nativeElement.querySelector('input[type=password]').getAttribute('autocomplete')).toBe('new-password');
    form.password.set('Long enough new password');form.submit();http.expectOne('/api/v1/auth/register').flush({}, { status: 429, statusText: 'Too Many Requests' });
    expect(form.pending()).toBeFalse();expect(form.error()).toContain('Too many attempts');
    form.submit();http.expectOne('/api/v1/auth/register').flush({}, { status: 503, statusText: 'Unavailable' });expect(form.error()).toContain('busy');
  });
});
