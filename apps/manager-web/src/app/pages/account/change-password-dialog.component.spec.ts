import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { provideRouter, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { ChangePasswordDialogComponent } from './change-password-dialog.component';

describe('Change account password dialog', () => {
  let closing: Subject<void>;
  const oldToken = 'A'.repeat(43), newToken = 'B'.repeat(43);
  const replacement = () => ({ userId: '00000000-0000-0000-0000-000000000001', token: newToken,
    email: 'synthetic@example.invalid', expiresAt: new Date(Date.now() + 86400000).toISOString() });
  beforeEach(() => {
    closing = new Subject<void>();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: API_BASE_URL, useValue: '/api/v1' },
      { provide: MatDialogRef, useValue: { beforeClosed: () => closing, close: jasmine.createSpy('close'), disableClose: false } },
    ] });
    TestBed.inject(AuthService).setSession(oldToken, 'synthetic@example.invalid');
    spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify();TestBed.inject(AuthService).clearSession(); });
  function open() {
    const fixture = TestBed.createComponent(ChangePasswordDialogComponent);fixture.detectChanges();
    const fields: HTMLInputElement[] = Array.from(fixture.nativeElement.querySelectorAll('input'));
    const fill = () => { fields[0].value = 'legacy';fields[1].value = fields[2].value = 'Synthetic new password!42'; };
    const submit = () => { fill();fixture.componentInstance.submit(new Event('submit')); };
    return { fixture, fields, fill, submit, component: fixture.componentInstance, http: TestBed.inject(HttpTestingController) };
  }

  it('keeps proof hidden with accessible labels and a Show/Hide choice, and cancels without writing', () => {
    const { fixture, fields, fill, http } = open();fill();
    const labels: HTMLLabelElement[] = Array.from(fixture.nativeElement.querySelectorAll('label'));
    expect(labels.map(label => label.htmlFor)).toEqual(fields.map(field => field.id));
    expect(fields.every(field => field.type === 'password')).toBeTrue();
    fixture.nativeElement.querySelector('[aria-controls]').click();fixture.detectChanges();
    expect(fields.every(field => field.type === 'text')).toBeTrue();
    closing.next();expect(fields.every(field => !field.value)).toBeTrue();
    expect(TestBed.inject(AuthService).token()).toBe(oldToken);http.expectNone('/api/v1/auth/password');
  });

  it('sends one current/new proof, clears every field and installs only a valid replacement bearer', () => {
    const { component, fields, http, submit } = open();
    const storage = spyOn(sessionStorage, 'setItem').and.callThrough();
    const local = spyOn(localStorage, 'setItem');
    submit();component.submit(new Event('submit'));expect(fields.every(field => !field.value)).toBeTrue();
    const request = http.expectOne('/api/v1/auth/password');
    expect(request.request.body).toEqual({ currentPassword: 'legacy', newPassword: 'Synthetic new password!42' });
    expect(request.request.headers.get('Authorization')).toBe('Bearer ' + oldToken);
    request.flush(replacement());
    expect(TestBed.inject(AuthService).token()).toBe(newToken);expect(TestBed.inject(AuthService).signedIn()).toBeTrue();
    expect(storage).toHaveBeenCalledOnceWith('keynest.token', newToken);expect(local).not.toHaveBeenCalled();
    expect(TestBed.inject(Router).navigateByUrl).not.toHaveBeenCalled();
  });

  it('rejects mismatched confirmation or fewer than fifteen code points without a request', () => {
    const { component, fields, fill, http } = open();fill();fields[2].value = 'Different synthetic phrase';
    component.submit(new Event('submit'));expect(component.error()).toContain('matching');
    expect(fields.every(field => !field.value)).toBeTrue();fill();fields[1].value = fields[2].value = '🔑'.repeat(14);
    component.submit(new Event('submit'));http.expectNone('/api/v1/auth/password');
    expect(TestBed.inject(AuthService).token()).toBe(oldToken);
  });

  for (const response of [{ status: 403, code: 'PASSWORD_VERIFICATION_FAILED' }, { status: 429, code: 'PASSWORD_CHANGE_BUSY' },
    { status: 503, code: 'PASSWORD_OPERATIONS_BUSY' }]) {
    it(`retains the old session after definite refusal ${response.status}/${response.code}`, () => {
      const { submit, http, component } = open();submit();
      http.expectOne('/api/v1/auth/password').flush({ code: response.code }, { status: response.status, statusText: 'Refused' });
      expect(component.busy()).toBeFalse();expect(component.error()).not.toBe('');expect(TestBed.inject(AuthService).token()).toBe(oldToken);
      submit();http.expectOne('/api/v1/auth/password').flush(replacement());
    });
  }

  for (const response of [0, 401, 503]) {
    it(`requires fresh sign-in without retry on an uncertain or ended response (${response})`, () => {
      const { submit, http } = open();submit();const request = http.expectOne('/api/v1/auth/password');
      if (response === 0) request.error(new ProgressEvent('error'));
      else request.flush({ code: response === 401 ? 'SESSION_REQUIRED' : 'PASSWORD_CHANGE_UNAVAILABLE' }, { status: response, statusText: 'Unavailable' });
      expect(TestBed.inject(AuthService).signedIn()).toBeFalse();expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalled();
      http.expectNone('/api/v1/auth/password');
    });
  }

  it('times out a single attempt and never automatically replays it', fakeAsync(() => {
    const { submit, http, fixture } = open();submit();const request = http.expectOne('/api/v1/auth/password');
    tick(20000);expect(request.cancelled).toBeTrue();expect(TestBed.inject(AuthService).signedIn()).toBeFalse();
    tick(20000);http.expectNone('/api/v1/auth/password');fixture.destroy();
  }));

  it('clears idle passwords on tab hiding and ends the local session if submitted work is canceled', () => {
    const { component, fields, fill, submit, http } = open();spyOnProperty(document, 'hidden', 'get').and.returnValue(true);
    fill();component.visible.set(true);component.onVisibilityChange();expect(fields.every(field => !field.value)).toBeTrue();
    expect(component.visible()).toBeFalse();expect(TestBed.inject(AuthService).token()).toBe(oldToken);
    submit();const request = http.expectOne('/api/v1/auth/password');component.onVisibilityChange();
    expect(request.cancelled).toBeTrue();expect(TestBed.inject(AuthService).signedIn()).toBeFalse();
  });

  it('preserves a replacement account session on a stale result', () => {
    const { submit, http } = open();submit();const request = http.expectOne('/api/v1/auth/password');
    TestBed.inject(AuthService).setSession('other-session', 'other@example.invalid');request.flush(replacement());
    expect(TestBed.inject(AuthService).token()).toBe('other-session');expect(TestBed.inject(Router).navigateByUrl).not.toHaveBeenCalled();
  });

  it('treats malformed success and unavailable browser storage as uncertain', () => {
    const { submit, http } = open();submit();
    http.expectOne('/api/v1/auth/password').flush({ ...replacement(), token: oldToken });
    expect(TestBed.inject(AuthService).signedIn()).toBeFalse();
  });

  it('clears a partially installed replacement if browser storage fails', () => {
    const { submit, http } = open();submit();spyOn(sessionStorage, 'setItem').and.throwError('synthetic storage failure');
    http.expectOne('/api/v1/auth/password').flush(replacement());
    expect(TestBed.inject(AuthService).signedIn()).toBeFalse();expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalled();
  });

  it('treats a close during submitted work as uncertain and clears both proofs', () => {
    const { submit, http, fields } = open();submit();const request = http.expectOne('/api/v1/auth/password');closing.next();
    expect(request.cancelled).toBeTrue();expect(fields.every(field => !field.value)).toBeTrue();
    expect(TestBed.inject(AuthService).signedIn()).toBeFalse();
  });
});
