import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';

describe('Vault update routes', () => {
  const endpoint = '/api/v1/vault/items/item-1';
  const policyId = 'keynest.strict.v1';
  const item = { id: 'item-1', policyId, label: 'Example login', username: 'demo', notifyBeforeDays: 14 };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
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

  async function open(path: string) {
    const harness = await RouterTestingHarness.create(path);
    const http = TestBed.inject(HttpTestingController);
    const request = http.expectOne(endpoint);
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-session');
    request.flush(item);
    harness.detectChanges();
    const content = harness.routeNativeElement!;
    const form = content.querySelector('form')!;
    const submit = () => form.dispatchEvent(new Event('submit', { cancelable: true }));
    return { harness, content, http, submit };
  }

  it('edits details without including a password or asking for replacement confirmation', async () => {
    const { content, http, submit } = await open('/vault/edit/item-1');
    expect(content.querySelector('h1')?.textContent).toContain('Edit vault item');
    expect(content.querySelector('input[autocomplete=new-password]')).toBeNull();
    const label = content.querySelector<HTMLInputElement>('input[name=label]')!;
    const username = content.querySelector<HTMLInputElement>('input[name=username]')!;
    label.value = 'Updated label';
    label.dispatchEvent(new Event('input'));
    username.value = '';
    username.dispatchEvent(new Event('input'));
    const confirm = spyOn(window, 'confirm');
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    submit();

    expect(confirm).not.toHaveBeenCalled();
    const request = http.expectOne(endpoint);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      label: 'Updated label', username: '', expiresInDays: null, notifyBeforeDays: 14,
    });
    request.flush({});
    expect(navigate).toHaveBeenCalledWith('/vault');
  });

  it('opens rotation directly and only changes the password after confirmation', async () => {
    const { harness, content, http, submit } = await open('/vault/rotate/item-1');
    expect(content.querySelector('h1')?.textContent).toContain('Rotate password');
    expect(content.querySelector('input[name=label]')).toBeNull();
    const input = content.querySelector<HTMLInputElement>('input[autocomplete=new-password]')!;
    expect(input.type).toBe('password');
    expect(content.textContent).not.toContain('Show password');
    const copy = Array.from(content.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('Copy replacement'))!;
    expect(copy.disabled).toBeTrue();
    const button = (text: string) => Array.from(content.querySelectorAll('button'))
      .find((action) => action.textContent?.trim() === text)!;
    button('Generator options').click();
    http.expectOne(`/api/v1/tools/policy?policyId=${policyId}`).flush({
      id: policyId,
      rules: {
        minLength: 18, maxLength: 40, requireUpper: true, requireLower: true,
        requireDigit: true, requireSymbol: false, minCategories: 3, excludeAmbiguous: true,
      },
    });
    harness.detectChanges();
    await harness.fixture.whenStable();
    const length = content.querySelector<HTMLInputElement>('input[name=length]')!;
    expect(length.min).toBe('18');
    expect(length.max).toBe('40');
    expect(input.form?.id).toBe('rotation-form');
    expect(length.form).toBeNull();
    const categories = Array.from(content.querySelectorAll<HTMLInputElement>('input[type=checkbox]'));
    expect(categories.map((category) => category.disabled)).toEqual([true, true, true, false]);
    categories[3].click();
    length.value = '24';
    length.dispatchEvent(new Event('input'));
    content.querySelector<HTMLInputElement>('#generator-keyword')!.value = 'mango';

    button('Generate replacement').click();
    const replacement = input.value;
    expect(replacement.length).toBe(24);
    expect(replacement).toMatch(/^\w+$/);
    expect(replacement).toMatch(/m[a4]ngo/i);
    expect(replacement).not.toMatch(/[0OIl1]/);
    http.expectNone('/api/v1/tools/generate');
    TestBed.tick();
    expect(copy.disabled).toBeFalse();
    const writeText = spyOn(navigator.clipboard, 'writeText').and.resolveTo();
    copy.click();
    expect(writeText).toHaveBeenCalledOnceWith(replacement);
    expect(input.type).toBe('password');
    http.expectNone(endpoint);

    const confirm = spyOn(window, 'confirm').and.returnValue(false);
    submit();
    http.expectNone(endpoint);
    confirm.and.returnValue(true);
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    submit();

    const request = http.expectOne(endpoint);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      password: replacement, expiresInDays: null, notifyBeforeDays: 14,
    });
    expect(input.value).toBe('');
    request.flush({});
    expect(navigate).toHaveBeenCalledWith('/vault');
  });
});
