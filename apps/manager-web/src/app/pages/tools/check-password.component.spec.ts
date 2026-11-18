import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../injectionTokens';
import { BuiltInPolicy } from './built-in-policy';
import { CheckPasswordPageComponent } from './check-password.component';

describe('CheckPasswordPageComponent', () => {
  const validationUrl = '/api/v1/tools/validate?operation=update';
  const policy: BuiltInPolicy = {
    id: 'keynest.default.v1',
    rules: {
      minLength: 16, maxLength: 128,
      requireLower: true, requireUpper: true, requireDigit: true, requireSymbol: true, minCategories: 3,
      excludeAmbiguous: true, forbidWhitespace: true, forbidUsernameSubstring: true,
      bannedSubstrings: ['password'],
    },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CheckPasswordPageComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api/v1' }],
    });
  });

  function open() {
    const fixture = TestBed.createComponent(CheckPasswordPageComponent);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/tools/policy').flush(policy);
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;
    const input = host.querySelector<HTMLInputElement>('#policy-sample')!;
    const meter = host.querySelector<HTMLElement>('.meter-label')!;
    const type = (value: string) => {
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    const status = (label: string) => Array.from(host.querySelectorAll('.policy-checks li'))
      .find((row) => row.firstElementChild?.textContent === label)
      ?.querySelector('.check-status')?.textContent?.trim();
    const submit = () => host.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    return { fixture, host, http, input, meter, status, submit, type };
  }

  it('previews rules while typing and waits for explicit API confirmation', () => {
    const { fixture, http, input, meter, status, submit, type } = open();
    expect(meter.dataset['tone']).toBe('neutral');

    type('A2');
    expect(meter.dataset['tone']).toBe('weak');
    expect(status('Include an uppercase letter')).toContain('Met');
    expect(status('Include a lowercase letter')).toContain('Not met');

    const sample = 'K7!ymdvRcTzq9pnA';
    type(sample);
    expect(meter.dataset['tone']).toBe('partial');
    expect(meter.textContent).toContain('Ready for final check');
    expect(status('Avoid blocked patterns')).toContain('Checked on submit');
    http.expectNone(validationUrl);

    submit();
    expect(input.value).toBe('');
    const request = http.expectOne(validationUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ password: sample, policyId: policy.id });
    request.flush({ valid: true, violations: [], policyId: policy.id });
    fixture.detectChanges();
    expect(meter.dataset['tone']).toBe('strong');
    expect(status('Avoid blocked patterns')).toContain('Met');

    type('again');
    expect(meter.dataset['tone']).toBe('weak');
    expect(status('Avoid blocked patterns')).toContain('Checked on submit');
    http.verify();
  });

  it('shows API violations alongside satisfied rules for the checked sample', () => {
    const { fixture, http, meter, status, submit, type } = open();
    type('K7!ymdvvvvTzq9pn');
    submit();
    http.expectOne(validationUrl).flush({
      valid: false, violations: ['password.banned_regex'], policyId: policy.id,
    });
    fixture.detectChanges();

    expect(meter.dataset['tone']).toBe('partial');
    expect(meter.textContent).toContain('Some rules unmet');
    expect(status('Avoid blocked patterns')).toContain('Not met');
    expect(status('Include a lowercase letter')).toContain('Met');
    http.verify();
  });

  it('rechecks a custom sample when the selected rules change and makes no request', async () => {
    const { fixture, host, http, input, status, type } = open();
    await fixture.whenStable();

    const mode = host.querySelector<HTMLSelectElement>('select[name=policy]')!;
    mode.value = 'custom';
    mode.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const sample = 'Abcdefgh23456789';
    type(sample);
    expect(status('Include a symbol')).toContain('Not met');
    const symbolOption = Array.from(host.querySelectorAll('fieldset label'))
      .find((label) => label.textContent?.trim() === 'Symbols')!;
    const symbols = symbolOption.querySelector('input')!;
    await fixture.whenStable();
    symbols.click();
    fixture.detectChanges();

    expect(host.querySelector('.meter-label')?.getAttribute('data-tone')).toBe('strong');
    expect(status('Include a symbol')).toBeUndefined();
    expect(input.value).toBe(sample);
    expect(host.querySelector('button[type=submit]')).toBeNull();
    http.verify();
  });
});
