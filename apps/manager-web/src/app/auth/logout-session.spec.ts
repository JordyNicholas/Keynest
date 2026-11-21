import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { logoutSession } from './logout-session';

describe('Shared logout action', () => {
  beforeEach(() => {
    sessionStorage.clear();TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(AuthService).setSession('old-session', 'sample@example.test');
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify();TestBed.inject(AuthService).clearSession(); });
  function logout() { logoutSession(TestBed.inject(HttpClient), '/api/v1', TestBed.inject(AuthService)).subscribe();return TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/logout'); }
  it('revokes the captured session and clears local state on success or connection failure', () => {
    const auth = TestBed.inject(AuthService);let request = logout();expect(request.request.headers.get('Authorization')).toBe('Bearer old-session');request.flush({});expect(auth.signedIn()).toBeFalse();
    auth.setSession('retry-session', 'sample@example.test');request = logout();request.flush({}, { status: 0, statusText: 'Offline' });expect(auth.signedIn()).toBeFalse();
  });
  it('does not clear a newer session when a previous logout finishes late', () => {
    const request = logout();const auth = TestBed.inject(AuthService);auth.setSession('new-session', 'other@example.test');request.flush({});expect(auth.token()).toBe('new-session');
  });
  it('bounds a stalled request and clears only the local captured session after five seconds', fakeAsync(() => {
    const request = logout();tick(5000);expect(request.cancelled).toBeTrue();expect(TestBed.inject(AuthService).signedIn()).toBeFalse();
  }));
});
