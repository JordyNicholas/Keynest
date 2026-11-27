import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { provideRouter, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { SignOutDevicesDialogComponent } from './sign-out-devices-dialog.component';

describe('Sign out all devices dialog', () => {
  let closing: Subject<void>;
  beforeEach(() => {
    closing = new Subject<void>();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: API_BASE_URL, useValue: '/api/v1' },
      { provide: MatDialogRef, useValue: { beforeClosed: () => closing, close: jasmine.createSpy('close'), disableClose: false } },
    ] });
    TestBed.inject(AuthService).setSession('synthetic-session', 'sample@example.invalid');
    spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify();TestBed.inject(AuthService).clearSession(); });
  function open() {
    const fixture = TestBed.createComponent(SignOutDevicesDialogComponent);fixture.detectChanges();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    const submit = () => { input.value = 'Synthetic current credential';fixture.componentInstance.submit(new Event('submit')); };
    return { fixture, input, component: fixture.componentInstance, http: TestBed.inject(HttpTestingController), submit };
  }

  it('requires explicit confirmation, supports accessible show/hide and cancels without a request', () => {
    const { fixture, input, component, http } = open();http.expectNone('/api/v1/auth/logout-all');
    expect(input.type).toBe('password');expect(input.autocomplete).toBe('current-password');
    expect(fixture.nativeElement.querySelector('label').htmlFor).toBe(input.id);
    const toggle: HTMLButtonElement = fixture.nativeElement.querySelector('[aria-controls]');
    input.value = 'Synthetic current credential';toggle.click();fixture.detectChanges();
    expect(input.type).toBe('text');expect(toggle.getAttribute('aria-pressed')).toBe('true');
    toggle.click();fixture.detectChanges();expect(input.type).toBe('password');expect(component.visible()).toBeFalse();
    closing.next();expect(input.value).toBe('');expect(TestBed.inject(AuthService).signedIn()).toBeTrue();
  });

  it('sends only current proof once, clears it immediately and signs out locally on success', () => {
    const { component, input, http, submit } = open();
    const storage = [spyOn(localStorage, 'setItem'), spyOn(sessionStorage, 'setItem')];
    submit();component.submit(new Event('submit'));expect(input.value).toBe('');
    const request = http.expectOne('/api/v1/auth/logout-all');
    expect(request.request.body).toEqual({ accountPassword: 'Synthetic current credential' });
    expect(request.request.headers.get('Authorization')).toBe('Bearer synthetic-session');
    request.flush({ status: 'ok' });expect(TestBed.inject(AuthService).signedIn()).toBeFalse();
    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith('/', { replaceUrl: true });
    storage.forEach(spy => expect(spy).not.toHaveBeenCalled());
  });

  for (const failure of [{ status: 403, code: 'PASSWORD_VERIFICATION_FAILED' }, { status: 429, code: 'REVOCATION_BUSY' },
    { status: 503, code: 'PASSWORD_OPERATIONS_BUSY' }]) {
    it(`retains the session after a definite ${failure.status}/${failure.code} refusal`, () => {
      const { component, http, submit } = open();submit();
      http.expectOne('/api/v1/auth/logout-all').flush({ code: failure.code }, { status: failure.status, statusText: 'Refused' });
      expect(TestBed.inject(AuthService).signedIn()).toBeTrue();expect(component.busy()).toBeFalse();expect(component.error()).not.toBe('');
      submit();http.expectOne('/api/v1/auth/logout-all').flush({ status: 'ok' });
    });
  }

  for (const failure of [0, 401, 503]) {
    it(`clears the captured session without retry after an uncertain or ended response (${failure})`, () => {
      const { http, submit } = open();submit();const request = http.expectOne('/api/v1/auth/logout-all');
      if (failure === 0) request.error(new ProgressEvent('error'));
      else request.flush({ code: failure === 401 ? 'SESSION_REQUIRED' : 'REVOCATION_UNAVAILABLE' }, { status: failure, statusText: 'Unavailable' });
      expect(TestBed.inject(AuthService).signedIn()).toBeFalse();http.expectNone('/api/v1/auth/logout-all');
    });
  }

  it('times out a single request and requires fresh sign-in', fakeAsync(() => {
    const { http, submit, fixture } = open();submit();const request = http.expectOne('/api/v1/auth/logout-all');
    tick(15000);expect(request.cancelled).toBeTrue();expect(TestBed.inject(AuthService).signedIn()).toBeFalse();
    tick(15000);http.expectNone('/api/v1/auth/logout-all');fixture.destroy();
  }));

  it('clears idle secrets on hiding the tab and treats pending cancellation as uncertain', () => {
    const { component, input, http, submit } = open();spyOnProperty(document, 'hidden', 'get').and.returnValue(true);
    input.value = 'Synthetic current credential';component.visible.set(true);component.onVisibilityChange();
    expect(input.value).toBe('');expect(component.visible()).toBeFalse();expect(TestBed.inject(AuthService).signedIn()).toBeTrue();
    submit();const request = http.expectOne('/api/v1/auth/logout-all');component.onVisibilityChange();
    expect(request.cancelled).toBeTrue();expect(TestBed.inject(AuthService).signedIn()).toBeFalse();
  });

  it('preserves a replacement account session on a stale completion', () => {
    const { http, submit } = open();submit();const request = http.expectOne('/api/v1/auth/logout-all');
    TestBed.inject(AuthService).setSession('replacement-session', 'other@example.invalid');request.flush({ status: 'ok' });
    expect(TestBed.inject(AuthService).token()).toBe('replacement-session');expect(TestBed.inject(Router).navigateByUrl).not.toHaveBeenCalled();
  });

  it('treats closing submitted work as uncertain and cancels the transport', () => {
    const { http, submit } = open();submit();const request = http.expectOne('/api/v1/auth/logout-all');
    closing.next();expect(request.cancelled).toBeTrue();expect(TestBed.inject(AuthService).signedIn()).toBeFalse();
  });
});
