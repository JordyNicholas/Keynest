import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { API_BASE_URL } from '../../injectionTokens';

describe('API health navigation', () => {
  const endpoint = '/api/v1/tools/health';

  beforeEach(() => {
    sessionStorage.removeItem('keynest.token');
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
  });

  it('shows a connection summary and returns to the previous page', async () => {
    const harness = await RouterTestingHarness.create('/account');
    await harness.navigateByUrl('/status');
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(endpoint).flush({ status: 'ok', product: 'keynest-manager-api', policyId: 'keynest.default.v1' });
    harness.detectChanges();

    const content = harness.routeNativeElement!;
    expect(content.querySelector('[role=status]')?.textContent).toContain('Connected to Manager API');
    expect(content.querySelector('pre')).toBeNull();
    const back = spyOn(TestBed.inject(Location), 'back');
    Array.from(content.querySelectorAll('button')).find((button) => button.textContent?.trim() === 'Back')!.click();
    expect(back).toHaveBeenCalled();
    http.verify();
  });

  it('returns to Account when the status URL was opened directly', async () => {
    const harness = await RouterTestingHarness.create('/status');
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(endpoint).flush({ status: 'ok', product: 'keynest-manager-api', policyId: 'keynest.default.v1' });
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    Array.from(harness.routeNativeElement!.querySelectorAll('button'))
      .find((button) => button.textContent?.trim() === 'Back')!.click();
    expect(navigate).toHaveBeenCalledWith('/account');
    http.verify();
  });
});
