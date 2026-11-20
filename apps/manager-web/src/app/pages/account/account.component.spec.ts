import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { AccountPageComponent } from './account.component';

describe('AccountPageComponent', () => {
  const endpoint = '/api/v1/ai/policy/explain';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
    TestBed.inject(AuthService).clearSession();
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    TestBed.inject(AuthService).clearSession();
  });

  it('applies the minimum password length only when creating an account', () => {
    const fixture = TestBed.createComponent(AccountPageComponent);
    const account = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);
    const password = '🔑'.repeat(14);
    account.formEmail.set('existing@example.com');
    account.formPassword.set(password);

    account.register();
    http.expectNone('/api/v1/auth/register');
    expect(account.error()).toContain('15 characters');

    account.login();
    const login = http.expectOne('/api/v1/auth/login');
    expect(login.request.body.password).toBe(password);
    login.flush({}, { status: 401, statusText: 'Unauthorized' });
  });

  it('explains invalid email before registration and keeps failed sign-in non-enumerating', () => {
    const fixture = TestBed.createComponent(AccountPageComponent);
    const account = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);
    account.formEmail.set('not-an-email');
    account.formPassword.set('SyntheticMaster!42');
    account.register();
    http.expectNone('/api/v1/auth/register');
    expect(account.error()).toContain('email');
    account.formEmail.set('synthetic@example.test');
    account.login();
    http.expectOne('/api/v1/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(account.error()).toBe('The email address or master password is incorrect.');
  });

  it('keeps AI setup behind sign-in', () => {
    const fixture = TestBed.createComponent(AccountPageComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#ai-settings-heading')).toBeNull();
  });

  it('reuses the one-request explanation flow and discards the setup on close', () => {
    TestBed.inject(AuthService).setSession('test-session', 'test@example.com');
    const fixture = TestBed.createComponent(AccountPageComponent);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/auth/me').flush({ email: 'test@example.com' });
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;
    const setup = host.querySelector<HTMLDetailsElement>('#ai-settings details')!;

    const toggle = (open: boolean) => {
      setup.open = open;
      setup.dispatchEvent(new Event('toggle'));
      fixture.detectChanges();
    };
    expect(host.querySelector('#ai-settings-heading')?.textContent).toContain('AI settings');
    expect(setup.querySelector('input')).toBeNull();
    toggle(true);
    http.expectNone(endpoint);

    setup.querySelector('input')!.value = 'test-provider-key';
    toggle(false);
    toggle(true);
    const input = setup.querySelector('input')!;
    expect(input.value).toBe('');
    input.value = 'test-provider-key';
    setup.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    const request = http.expectOne(endpoint);
    expect(request.request.headers.get('X-Keynest-AI-Key')).toBe('test-provider-key');
    expect(request.request.body).toEqual({ providerId: 'openai', policyId: 'keynest.default.v1' });
    expect(input.value).toBe('');
    toggle(false);
    expect(request.cancelled).toBeTrue();
  });
});
