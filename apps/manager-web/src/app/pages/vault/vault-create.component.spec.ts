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

  it('uses the selected options to generate locally and sends only the resulting password on save', async () => {
    const fixture = TestBed.createComponent(VaultCreatePageComponent);
    const http = TestBed.inject(HttpTestingController);
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    fixture.componentInstance.label.set('Example login');
    fixture.detectChanges();
    await fixture.whenStable();

    const form: HTMLFormElement = fixture.nativeElement.querySelector('form');
    const mode = form.querySelector<HTMLSelectElement>('select[name=mode]')!;
    mode.value = 'generate';
    mode.dispatchEvent(new Event('change'));
    http.expectOne('/api/v1/tools/policy').flush({
      id: 'keynest.default.v1',
      rules: { minLength: 16, maxLength: 128, requireLower: true, requireUpper: true,
        requireDigit: true, requireSymbol: true, minCategories: 3, excludeAmbiguous: true },
    });
    fixture.detectChanges();
    await fixture.whenStable();

    const length = form.querySelector<HTMLInputElement>('input[name=length]')!;
    expect(length.min).toBe('16');
    const categories = Array.from(form.querySelectorAll<HTMLInputElement>('input[type=checkbox]'));
    expect(categories.length).toBe(4);
    expect(categories.every((category) => category.checked && category.disabled)).toBeTrue();
    const keyword = form.querySelector<HTMLInputElement>('#generator-keyword')!;
    keyword.value = 'mango';
    length.value = '22';
    length.dispatchEvent(new Event('input'));
    form.dispatchEvent(new Event('submit', { cancelable: true }));

    const request = http.expectOne(endpoint);
    const { password, ...fields } = request.request.body;
    expect(fields).toEqual({
      label: 'Example login', username: '', url: '', expiresInDays: 90, notifyBeforeDays: 14, generate: false,
    });
    expect(password.length).toBe(22);
    expect(password).toMatch(/m[@a4]ngo/i);
    expect(password).toMatch(/[a-z]/);
    expect(password).toMatch(/[A-Z]/);
    expect(password).toMatch(/[2-9]/);
    expect(password).toMatch(/[^a-z0-9]/i);
    expect(password).not.toMatch(/[0OIl1]/);
    expect(navigate).not.toHaveBeenCalled();
    request.flush({});
    expect(navigate).toHaveBeenCalledWith('/vault');
  });
});
