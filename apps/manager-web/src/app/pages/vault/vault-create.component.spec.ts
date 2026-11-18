import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { VaultCreatePageComponent } from './vault-create.component';

describe('VaultCreatePageComponent', () => {
  const endpoint = '/api/v1/vault/items';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
    TestBed.inject(AuthService).setSession('test-session', 'demo@example.test');
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    TestBed.inject(AuthService).clearSession();
  });

  it('clears the entered password on submit and shows policy feedback', () => {
    const fixture = TestBed.createComponent(VaultCreatePageComponent);
    const component = fixture.componentInstance;
    const input = document.createElement('input');
    const http = TestBed.inject(HttpTestingController);
    component.label.set('Example login');
    component.save(new Event('submit', { cancelable: true }), input);
    http.expectNone(endpoint);

    input.value = 'test-password';
    component.save(new Event('submit', { cancelable: true }), input);
    const request = http.expectOne(endpoint);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-session');
    expect(request.request.body).toEqual(jasmine.objectContaining({
      label: 'Example login', generate: false, password: 'test-password',
    }));
    expect(input.value).toBe('');
    request.flush({ code: 'VAULT_PASSWORD_POLICY', violations: ['password.too_short'] },
      { status: 400, statusText: 'Bad Request' });
    expect(component.details()).toEqual(['Use a longer password.']);
    expect(component.saving()).toBeFalse();
  });

  it('generates within the save request and returns to the vault after success', () => {
    const fixture = TestBed.createComponent(VaultCreatePageComponent);
    const component = fixture.componentInstance;
    const input = document.createElement('input');
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    component.label.set('Example login');
    component.mode.set('generate');
    input.value = 'discarded sample';
    component.save(new Event('submit', { cancelable: true }), input);
    const request = TestBed.inject(HttpTestingController).expectOne(endpoint);
    expect(request.request.body.generate).toBeTrue();
    expect(request.request.body.password).toBeUndefined();
    expect(input.value).toBe('');
    expect(navigate).not.toHaveBeenCalled();
    request.flush({});
    expect(navigate).toHaveBeenCalledWith('/vault');
  });
});
