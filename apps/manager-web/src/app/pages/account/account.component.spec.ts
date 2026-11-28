import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { ChangePasswordDialogComponent } from './change-password-dialog.component';
import { SignOutDevicesDialogComponent } from './sign-out-devices-dialog.component';
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

  it('informs an unverified existing owner without blocking account actions', () => {
    TestBed.inject(AuthService).setSession('test-session', 'test@example.com');
    const fixture = TestBed.createComponent(AccountPageComponent);
    TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/me')
      .flush({ email: 'test@example.com', emailVerified: false });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#email-verification-pending')?.textContent).toContain('continue using');
    expect(fixture.nativeElement.querySelector('#open-change-password').disabled).toBeFalse();
    expect(TestBed.inject(AuthService).signedIn()).toBeTrue();
    TestBed.inject(AuthService).setSession('different-session', 'other@example.test');fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#email-verification-pending')).toBeNull();
  });


  it('opens the shared confirmation dialog without sending revocation itself', () => {
    TestBed.inject(AuthService).setSession('test-session', 'test@example.com');
    const fixture = TestBed.createComponent(AccountPageComponent);const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/auth/me').flush({ email: 'test@example.com' });fixture.detectChanges();
    const open = spyOn(fixture.debugElement.injector.get(MatDialog), 'open');fixture.componentInstance.signOutDevices();
    expect(open).toHaveBeenCalledWith(SignOutDevicesDialogComponent, jasmine.objectContaining({
      id: 'sign-out-devices', ariaLabelledBy: 'sign-out-devices-title', restoreFocus: true,
    }));http.expectNone('/api/v1/auth/logout-all');
  });

  it('opens password change through the shared dialog without submitting proof', () => {
    TestBed.inject(AuthService).setSession('test-session', 'test@example.com');
    const fixture = TestBed.createComponent(AccountPageComponent);const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/auth/me').flush({ email: 'test@example.com' });fixture.detectChanges();
    const open = spyOn(fixture.debugElement.injector.get(MatDialog), 'open');fixture.componentInstance.changePassword();
    expect(open).toHaveBeenCalledWith(ChangePasswordDialogComponent, jasmine.objectContaining({
      id: 'change-password', ariaLabelledBy: 'change-password-title', restoreFocus: true,
    }));http.expectNone('/api/v1/auth/password');
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
