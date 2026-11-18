import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OverlayContainer } from '@angular/cdk/overlay';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { VaultPageComponent } from './vault.component';

describe('VaultPageComponent', () => {
  let fixture: ComponentFixture<VaultPageComponent>;
  let component: VaultPageComponent;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    sessionStorage.setItem('keynest.token', 'session-token');
    TestBed.configureTestingModule({
      imports: [VaultPageComponent],
      providers: [
        AuthService,
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
    fixture = TestBed.createComponent(VaultPageComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    const request = http.expectOne('/api/v1/vault/items');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe('Bearer session-token');
    request.flush([]);
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    sessionStorage.clear();
    localStorage.clear();
  });

  it('renders metadata returned by an authenticated vault list request', () => {
    component.refreshVault();
    const request = http.expectOne('/api/v1/vault/items');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe('Bearer session-token');
    request.flush([{
      id: 'item-1', label: 'Example account', username: 'demo-user',
      url: 'https://example.com', policyId: 'keynest.default.v1',
    }]);
    fixture.detectChanges();

    const row: HTMLElement = fixture.nativeElement.querySelector('[aria-labelledby=vault-heading] .vault-list li');
    expect(row.textContent).toContain('Example account');
    expect(row.textContent).toContain('demo-user');
    expect(row.textContent).toContain('https://example.com');
    expect(row.querySelector('output.secret')).toBeNull();
    const host: HTMLElement = fixture.nativeElement;
    host.style.inlineSize = '288px';
    const reveal = row.querySelector<HTMLButtonElement>('button')!;
    const more = row.querySelector<HTMLButtonElement>('button[aria-haspopup=menu]')!;
    expect(reveal.textContent).toContain('Reveal for 15s');
    expect(reveal.getBoundingClientRect().top).toBe(more.getBoundingClientRect().top);
    expect(more.getBoundingClientRect().right).toBeLessThanOrEqual(row.getBoundingClientRect().right);
    expect(row.querySelector('a')).toBeNull();

    more.click();
    fixture.detectChanges();
    const menu = TestBed.inject(OverlayContainer).getContainerElement().querySelector('[role=menu]')!;
    expect(menu.getAttribute('aria-label')).toBe('Actions for Example account');
    const links = Array.from(menu.querySelectorAll('a'), (link) => link.getAttribute('href'));
    expect(links).toEqual(['/vault/edit/item-1', '/vault/rotate/item-1']);
    const deleteAction = menu.querySelector('button')!;
    expect(deleteAction.textContent).toContain('Delete');
    expect(getComputedStyle(deleteAction).backgroundColor).toBe('rgba(0, 0, 0, 0)');
    expect(getComputedStyle(deleteAction).color).not.toBe(getComputedStyle(menu.querySelector('a')!).color);
  });

  it('labels expiry on saved cards at both thresholds and for overdue and undated passwords', () => {
    const now = Date.parse('2026-11-12T12:00:00Z');
    spyOn(Date, 'now').and.returnValue(now);
    const dayMs = 86_400_000;
    const samples = [30, 30 - 0.1, 14, 14 - 0.1, 0.5, 0, null];
    component.items.set(samples.map((days, index) => ({
      id: `expiry-${index}`,
      label: 'Example login',
      policyId: 'keynest.default.v1',
      expiresAt: days === null ? undefined : new Date(now + days * dayMs).toISOString(),
    })));
    fixture.detectChanges();

    const badges = fixture.nativeElement.querySelectorAll('.saved-items .expiry-status') as NodeListOf<HTMLElement>;
    expect(Array.from(badges, (badge) => [badge.textContent?.trim(), badge.dataset['tone']])).toEqual([
      ['30 days left', 'healthy'],
      ['29 days left', 'warning'],
      ['14 days left', 'warning'],
      ['13 days left', 'urgent'],
      ['Less than a day left', 'urgent'],
      ['Expired', 'urgent'],
      ['No expiry', 'neutral'],
    ]);
  });

  it('lists overdue and upcoming items in expiry order with rotation links', () => {
    spyOn(Date, 'now').and.returnValue(Date.parse('2026-11-12T12:00:00Z'));
    const item = { label: 'Example', policyId: 'keynest.default.v1', notifyBeforeDays: 7 };
    component.items.set([
      { ...item, id: 'soon', expiresAt: '2026-11-18T12:00:00Z' },
      { ...item, id: 'later', expiresAt: '2026-11-20T12:00:00Z' },
      { ...item, id: 'overdue', expiresAt: '2026-11-11T12:00:00Z', notifyBeforeDays: 0 },
      { ...item, id: 'no-expiry' },
    ]);
    fixture.detectChanges();

    const links = Array.from(
      fixture.nativeElement.querySelectorAll('[aria-labelledby="upcoming-expiry-heading"] a') as NodeListOf<HTMLAnchorElement>,
    );
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/vault/rotate/overdue',
      '/vault/rotate/soon',
    ]);
  });

  it('deletes only after confirmation and ignores a second request while deleting', () => {
    const confirm = spyOn(window, 'confirm').and.returnValue(false);
    const item = { id: 'item-1', label: 'Example', policyId: 'keynest.default.v1' };
    component.items.set([item]);

    component.remove(item.id);
    http.expectNone('/api/v1/vault/items/item-1');
    expect(component.items()).toEqual([item]);

    confirm.and.returnValue(true);
    component.remove(item.id);
    component.remove(item.id);
    const request = http.expectOne('/api/v1/vault/items/item-1');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.headers.get('Authorization')).toBe('Bearer session-token');
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(component.items()).toEqual([]);
    expect(component.pending()).toBeFalse();
  });

  it('discards a vault list response after sign-out', () => {
    component.refreshVault();
    const request = http.expectOne('/api/v1/vault/items');
    TestBed.inject(AuthService).clearSession();
    request.flush([{ id: 'old', label: 'Old account', policyId: 'keynest.default.v1' }]);
    expect(component.items()).toEqual([]);
    expect(component.vaultLoaded()).toBeFalse();
  });

  it('renders an empty state when the vault list is empty', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[aria-labelledby=vault-heading] .vault-list .empty').textContent)
      .toContain('No vault items yet.');
  });

  it('shows the sign-in error after a denied list request and clears it on recovery', () => {
    component.refreshVault();
    http.expectOne('/api/v1/vault/items').flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent)
      .toContain('Vault list failed — sign in again');

    component.refreshVault();
    http.expectOne('/api/v1/vault/items').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('uses POST and automatically conceals the secret after 15 seconds', fakeAsync(() => {
    component.reveal('item-1');

    const request = http.expectOne('/api/v1/vault/items/item-1/reveal');
    expect(request.request.method).toBe('POST');
    request.flush({ item: { id: 'item-1', label: 'Example', policyId: 'keynest.default.v1' }, password: 'secret' });

    expect(component.revealed()['item-1']).toBe('secret');
    tick(14_999);
    expect(component.revealed()['item-1']).toBe('secret');
    tick(1);
    expect(component.revealed()['item-1']).toBeUndefined();
  }));

  it('ignores a reveal response after conceal or a session change', () => {
    const endpoint = '/api/v1/vault/items/item-1/reveal';
    const response = { item: { id: 'item-1' }, password: 'sample' };
    component.reveal('item-1');
    const concealed = http.expectOne(endpoint);
    component.conceal('item-1');
    concealed.flush(response);
    expect(component.revealed()['item-1']).toBeUndefined();

    component.reveal('item-1');
    const oldSession = http.expectOne(endpoint);
    TestBed.inject(AuthService).setSession('new-session', 'test@example.com');
    oldSession.flush(response);
    expect(component.revealed()['item-1']).toBeUndefined();
  });

  it('replaces the timer and supports immediate manual concealment', fakeAsync(() => {
    component.reveal('item-1');
    http.expectOne('/api/v1/vault/items/item-1/reveal').flush({
      item: { id: 'item-1', label: 'Example', policyId: 'keynest.default.v1' },
      password: 'first',
    });
    tick(10_000);

    component.reveal('item-1');
    http.expectOne('/api/v1/vault/items/item-1/reveal').flush({
      item: { id: 'item-1', label: 'Example', policyId: 'keynest.default.v1' },
      password: 'second',
    });
    tick(5_000);
    expect(component.revealed()['item-1']).toBe('second');

    component.conceal('item-1');
    expect(component.revealed()['item-1']).toBeUndefined();
    tick(15_000);
  }));
});
