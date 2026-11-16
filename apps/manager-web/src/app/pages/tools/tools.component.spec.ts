import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../injectionTokens';
import { ToolsPageComponent } from './tools.component';

describe('ToolsPageComponent', () => {
  it('clears the sample on submit and renders violations from the API', () => {
    TestBed.configureTestingModule({
      imports: [ToolsPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api/v1' }],
    });
    const fixture = TestBed.createComponent(ToolsPageComponent);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/tools/health').flush({ status: 'ok' });
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

    expect(host.querySelector('[role=status]')?.textContent).toContain('password.too_short');
    http.verify();
  });
});
