import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { firstValueFrom } from 'rxjs';
import { routes } from '../app.routes';
import { API_BASE_URL } from '../injectionTokens';
import { AuthService } from './auth.service';

function checkReferences(host: HTMLElement): void {
  const links = host.querySelectorAll<HTMLAnchorElement>('a[href^="https://github.com/"]');
  expect(links.length).toBeGreaterThan(0);
  for (const link of links) {
    expect(link.target).toBe('_blank');
    expect(link.relList.contains('noopener')).toBeTrue();
    expect(link.relList.contains('noreferrer')).toBeTrue();
    expect(link.textContent).toContain('opens in a new tab');
  }
}

describe('Account entry and reference presentation', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api/v1' }] });
  });
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    TestBed.inject(AuthService).clearSession();
  });

  it('opens GitHub references separately while keeping homepage section navigation in Keynest', async () => {
    const harness = await RouterTestingHarness.create('/');
    const host = harness.routeNativeElement!;
    checkReferences(host);
    for (const link of host.querySelectorAll<HTMLAnchorElement>('nav a[href^="/#"]')) expect(link.target).toBe('');
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('keeps account help and workspace security references in separate tabs', async () => {
    const harness = await RouterTestingHarness.create('/account');
    checkReferences(harness.routeNativeElement!);
  });

  it('uses the scoped backdrop, password label, focus trap and existing close behavior', async () => {
    const harness = await RouterTestingHarness.create('/?auth=signin');
    await harness.fixture.whenStable();
    const dialog = TestBed.inject(MatDialog).openDialogs[0];
    expect(document.querySelector('.cdk-overlay-backdrop')?.classList.contains('kn-auth-backdrop')).toBeTrue();
    const host = document.querySelector<HTMLElement>('kn-auth-dialog')!;
    expect(host.querySelector('label[for=auth-password]')?.textContent).toContain('Keynest password');
    expect(host.querySelector<HTMLInputElement>('#auth-password')?.type).toBe('password');
    expect(host.querySelector('#keynest-password-help')?.textContent).toContain('sign in');
    expect(document.querySelector('[role=dialog]')).not.toBeNull();
    expect(document.querySelectorAll('.cdk-focus-trap-anchor').length).toBeGreaterThanOrEqual(2);
    const closed = firstValueFrom(dialog.afterClosed());dialog.close();await closed;await harness.fixture.whenStable();
    expect(TestBed.inject(MatDialog).openDialogs.length).toBe(0);
  });
});
