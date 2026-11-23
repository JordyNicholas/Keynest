import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { PolicyExplanationComponent } from './policy-explanation.component';

describe('PolicyExplanationComponent', () => {
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
    TestBed.inject(AuthService).setSession('test-session', 'test@example.com');
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    TestBed.inject(AuthService).clearSession();
  });

  function submit() {
    const fixture = TestBed.createComponent(PolicyExplanationComponent);
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;
    const input = host.querySelector('input')!;
    input.value = 'test-provider-key';
    host.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    const request = TestBed.inject(HttpTestingController).expectOne(endpoint);
    expect(input.value).toBe('');
    return { fixture, host, request };
  }

  it('sends the credential only in a header and passes the explanation in transient navigation info', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const { request } = submit();
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-session');
    expect(request.request.headers.get('X-Keynest-AI-Key')).toBe('test-provider-key');
    expect(request.request.body).toEqual({ providerId: 'openai', policyId: 'keynest.default.v1' });
    request.flush({ explanation: '<strong>Example</strong>' });
    expect(navigate).toHaveBeenCalledOnceWith(['/tools'], {
      queryParams: { tool: 'check' },
      fragment: 'why-these-rules',
      info: { policyExplanation: '<strong>Example</strong>' },
    });
  });

  it('keeps provider error details out of the page', () => {
    const { fixture, host, request } = submit();
    request.flush({ error: 'AI_TIMEOUT', message: 'provider-internal-detail' }, { status: 504, statusText: 'Gateway Timeout' });
    fixture.detectChanges();
    expect(host.querySelector('[role=alert]')?.textContent).toContain('Could not get');
    expect(host.textContent).not.toContain('provider-internal-detail');
  });

  it('keeps the form open with key guidance when the provider rejects the credential', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const { fixture, host, request } = submit();
    request.flush({ error: 'AI_PROVIDER_AUTH_FAILED' }, { status: 502, statusText: 'Bad Gateway' });
    fixture.detectChanges();
    expect(host.querySelector('[role=alert]')?.textContent).toContain('did not accept this key');
    expect(host.querySelector('input')?.disabled).toBeFalse();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('cancels the outstanding request when leaving the page', () => {
    const { fixture, request } = submit();
    fixture.destroy();
    expect(request.cancelled).toBeTrue();
  });
});
