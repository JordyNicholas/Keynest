import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../injectionTokens';
import { CheckPasswordPageComponent } from './check-password.component';

describe('CheckPasswordPageComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CheckPasswordPageComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api/v1' }],
    });
  });

  it('clears the sample on submit and renders violations from the API', () => {
    const fixture = TestBed.createComponent(CheckPasswordPageComponent);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/tools/policy').flush({ id: 'keynest.default.v1', rules: { bannedSubstrings: [] } });
    fixture.detectChanges();

    const host: HTMLElement = fixture.nativeElement;
    const input = host.querySelector<HTMLInputElement>('#policy-sample')!;
    input.value = 'sample';
    host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    expect(input.value).toBe('');
    const request = http.expectOne('/api/v1/tools/validate?operation=update');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ password: 'sample' });
    request.flush({ valid: false, violations: ['password.too_short'], policyId: 'keynest.default.v1' });
    fixture.detectChanges();

    expect(host.querySelector('[role=status]')?.textContent).toContain('Use a longer password.');
    http.verify();
  });

  it('checks custom rules without sending the sample or rules to the API', () => {
    const fixture = TestBed.createComponent(CheckPasswordPageComponent);
    const component = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/tools/policy').flush({ id: 'keynest.default.v1', rules: { bannedSubstrings: [] } });
    const sample = document.createElement('input');
    component.selectMode('custom', sample);
    component.minimum.set(8);
    component.required.symbols = false;
    sample.value = 'Testword1';
    component.validateSample(new Event('submit', { cancelable: true }), sample);

    expect(sample.value).toBe('');
    expect(component.validation()).toEqual({ valid: true, messages: [], policyId: 'custom' });
    http.verify();
  });
});
