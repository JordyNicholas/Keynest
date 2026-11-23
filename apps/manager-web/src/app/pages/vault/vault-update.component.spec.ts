import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { By } from '@angular/platform-browser';
import { VaultUpdatePageComponent } from './vault-update.component';
import { expiryInstant } from '../../ui/expiry-date';
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

  it('saves the selected expiry date in edit without rounding it to days', async () => {
    const { harness, http, submit } = await open('/vault/edit/item-1');
    const component = harness.fixture.debugElement.query(By.directive(VaultUpdatePageComponent)).componentInstance as VaultUpdatePageComponent;
    const date = new Date(2032, 4, 20);component.expiryDate.set(date);submit();
    const request = http.expectOne(endpoint);expect(request.request.body.expiresAt).toBe(expiryInstant(date));
    expect(request.request.body.expiresInDays).toBeUndefined();request.flush({});
  });

  it('saves a selected expiry date during password rotation', async () => {
    const { harness, content, http, submit } = await open('/vault/rotate/item-1');
    const component = harness.fixture.debugElement.query(By.directive(VaultUpdatePageComponent)).componentInstance as VaultUpdatePageComponent;
    const date = new Date(2032, 4, 21);component.expiryDate.set(date);
    content.querySelector<HTMLInputElement>('input[autocomplete=new-password]')!.value = 'Synthetic replacement';
    spyOn(window, 'confirm').and.returnValue(true);submit();
    const request = http.expectOne(endpoint);expect(request.request.body.expiresAt).toBe(expiryInstant(date));
    expect(request.request.body.password).toBe('Synthetic replacement');request.flush({});
  });

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
      label: 'Updated label', username: '', expiresAt: null, notifyBeforeDays: 14,
    });
    request.flush({});
    expect(navigate).toHaveBeenCalledWith('/vault');
  });

  it('aligns replacement actions at phone width and stacks them on narrower layouts', async () => {
    const { content } = await open('/vault/rotate/item-1');
    const actions = content.querySelector<HTMLElement>('.replacement-actions')!;
    const [show, copy] = actions.querySelectorAll('button');

    content.style.inlineSize = '320px';
    const showBounds = show.getBoundingClientRect();
    const copyBounds = copy.getBoundingClientRect();
    expect(showBounds.top).toBe(copyBounds.top);
    expect(showBounds.width).toBeCloseTo(copyBounds.width, 1);
    expect(showBounds.height).toBeGreaterThanOrEqual(44);
    expect(copyBounds.height).toBeGreaterThanOrEqual(44);
    expect(showBounds.right).toBeLessThan(copyBounds.left);
    expect(copyBounds.right).toBeLessThanOrEqual(actions.getBoundingClientRect().right);

    content.style.inlineSize = '240px';
    expect(show.getBoundingClientRect().bottom).toBeLessThan(copy.getBoundingClientRect().top);
  });

  it('opens rotation directly and only changes the password after confirmation', async () => {
    const { harness, content, http, submit } = await open('/vault/rotate/item-1');
    expect(content.querySelector('h1')?.textContent).toContain('Rotate password');
    expect(content.querySelector('input[name=label]')).toBeNull();
    const input = content.querySelector<HTMLInputElement>('input[autocomplete=new-password]')!;
    expect(input.type).toBe('password');
    const copy = Array.from(content.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('Copy replacement'))!;
    expect(copy.disabled).toBeTrue();
    const button = (text: string) => Array.from(content.querySelectorAll('button'))
      .find((action) => action.textContent?.trim() === text)!;
    const visibility = button('Show password');
    expect(visibility.disabled).toBeTrue();
    input.value = 'manually-typed-sample';
    input.dispatchEvent(new Event('input'));
    harness.detectChanges();
    visibility.click();
    harness.detectChanges();
    expect(visibility.textContent?.trim()).toBe('Hide password');
    expect(input.type).toBe('text');
    expect(input.value).toBe('manually-typed-sample');

    const generator = content.querySelector('details')!;
    const toggleGenerator = (open: boolean) => {
      generator.open = open;
      generator.dispatchEvent(new Event('toggle'));
      harness.detectChanges();
    };
    expect(generator.open).toBeFalse();
    expect(content.querySelector('select[name=generationMode]')).toBeNull();
    http.expectNone('/api/v1/tools/policy');
    toggleGenerator(true);
    http.expectOne('/api/v1/tools/policy').flush({
      id: policyId,
      rules: {
        minLength: 22, maxLength: 40, requireUpper: true, requireLower: true,
        requireDigit: true, requireSymbol: false, minCategories: 3, excludeAmbiguous: true,
      },
    });
    harness.detectChanges();
    await harness.fixture.whenStable();
    const generationMode = content.querySelector<HTMLSelectElement>('select[name=generationMode]')!;
    expect(generationMode.value).toBe('policy');
    expect(content.querySelector('input[name=length]')).toBeNull();
    button('Generate replacement').click();
    expect(input.value.length).toBe(22);
    expect(input.value).not.toMatch(/[0OIl1]/);
    harness.detectChanges();
    expect(input.type).toBe('password');
    expect(visibility.textContent?.trim()).toBe('Show password');

    generationMode.value = 'custom';
    generationMode.dispatchEvent(new Event('change'));
    harness.detectChanges();
    await harness.fixture.whenStable();
    const length = content.querySelector<HTMLInputElement>('input[name=length]')!;
    expect(length.min).toBe('22');
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
    visibility.click();
    harness.detectChanges();
    expect(input.type).toBe('text');
    expect(input.value).toBe(replacement);
    visibility.click();
    harness.detectChanges();
    expect(input.type).toBe('password');
    const writeText = spyOn(navigator.clipboard, 'writeText').and.resolveTo();
    copy.click();
    expect(writeText).toHaveBeenCalledOnceWith(replacement);
    expect(input.type).toBe('password');
    http.expectNone(endpoint);

    toggleGenerator(false);
    expect(generator.querySelector('input')).toBeNull();
    toggleGenerator(true);
    await harness.fixture.whenStable();
    expect(content.querySelector<HTMLSelectElement>('select[name=generationMode]')?.value).toBe('policy');
    expect(content.querySelector('#generator-keyword')).toBeNull();
    expect(input.value).toBe(replacement);

    const confirm = spyOn(window, 'confirm').and.returnValue(false);
    submit();
    http.expectNone(endpoint);
    confirm.and.returnValue(true);
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    visibility.click();
    harness.detectChanges();
    expect(input.type).toBe('text');
    submit();
    harness.detectChanges();
    expect(input.type).toBe('password');

    const request = http.expectOne(endpoint);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      password: replacement, policyId, expiresAt: null, notifyBeforeDays: 14,
    });
    expect(input.value).toBe('');
    request.flush({});
    expect(navigate).toHaveBeenCalledWith('/vault');
  });
});
