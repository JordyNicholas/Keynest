import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { VaultPageComponent } from './vault.component';
import { VaultItem } from './vault-item';
import { VaultPage } from './vault-metadata';
import { RestoreResult } from './vault-restore-dialog.component';

const sample: VaultItem = { id: '00000000-0000-0000-0000-000000000001', label: 'Example account', username: 'demo-user', url: 'https://example.test/login', policyId: 'keynest.default.v1' };
const second: VaultItem = { ...sample, id: '00000000-0000-0000-0000-000000000002', label: 'Second account' };
const endpoint = '/api/v1/vault/items/search';
function page(items: VaultItem[] = [], options: Partial<VaultPage> = {}): VaultPage {
  const total = options.totalMatches ?? items.length;
  return { asOf: '2026-11-21T12:00:00Z', items, totalMatches: total, page: 0, size: 10, totalPages: Math.ceil(total / (options.size ?? 10)),
    counts: { total, expired: 0, soon: 0, upcoming: 0, later: 0, unset: total }, ...options };
}

describe('Bounded Vault browsing', () => {
  let fixture: ComponentFixture<VaultPageComponent>;
  let component: VaultPageComponent;
  let http: HttpTestingController;
  beforeEach(() => {
    sessionStorage.clear();sessionStorage.setItem('keynest.token', 'session-token');
    TestBed.configureTestingModule({ imports: [VaultPageComponent], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api/v1' }] });
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(VaultPageComponent);component = fixture.componentInstance;http = TestBed.inject(HttpTestingController);
    const request = http.expectOne(endpoint);expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Authorization')).toBe('Bearer session-token');
    expect(request.request.body).toEqual({ page: 0, size: 10, query: '', field: 'all', expiry: 'all', sort: 'label', itemId: null });
    request.flush(page());fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy();http.verify();TestBed.inject(AuthService).clearSession(); });
  function load(items: VaultItem[] = [sample], options: Partial<VaultPage> = {}) {
    component.refreshVault();http.expectOne(endpoint).flush(page(items, options));fixture.detectChanges();
  }
  function open(index = 0) {
    fixture.nativeElement.querySelectorAll('mat-expansion-panel-header')[index].click();fixture.detectChanges();
  }
  function reveal() {
    component.reveal(sample.id);
    const request = http.expectOne(`/api/v1/vault/items/${sample.id}/reveal`);expect(request.request.method).toBe('POST');
    request.flush({ password: 'synthetic secret' });
  }

  it('retains only selected identities across pages and filters, and clears them on session change', () => {
    load([sample]);component.selectForExport(sample.id, true);
    load([second]);component.selectForExport(second.id, true);
    component.setExpiry('attention');http.expectOne(endpoint).flush(page());
    expect([...component.exportIds()]).toEqual([sample.id, second.id]);
    component.selectForExport('unavailable-id', true);expect(component.exportIds().size).toBe(2);
    TestBed.inject(AuthService).clearSession();fixture.detectChanges();expect(component.exportIds().size).toBe(0);
  });

  it('conceals before restore, prevents overlapping dialogs and refreshes only after confirmed success for the same session', () => {
    load();open();reveal();let closed = new Subject<RestoreResult | undefined>();
    const dialog = { afterClosed: () => closed, close: jasmine.createSpy('close') };
    const launch = spyOn(TestBed.inject(MatDialog), 'open').and.returnValue(dialog as never);
    component.openRestore();expect(component.revealed()).toEqual({});expect(launch).toHaveBeenCalledTimes(1);
    component.openRestore();component.openExport();expect(launch).toHaveBeenCalledTimes(1);http.expectNone(endpoint);
    closed.next(undefined);closed.complete();http.expectNone(endpoint);closed = new Subject<RestoreResult | undefined>();
    component.openRestore();closed.next({ restoredCount: 2, updatedCount: 0, skippedCount: 0, receivingPolicyUnchanged: true });http.expectOne(endpoint).flush(page([sample]));
    closed.complete();
  });

  it('closes restore on a session change and never refreshes another account from an old result', () => {
    const closed = new Subject<RestoreResult>();const dialog = { afterClosed: () => closed, close: jasmine.createSpy('close') };
    spyOn(TestBed.inject(MatDialog), 'open').and.returnValue(dialog as never);component.openRestore();
    TestBed.inject(AuthService).setSession('different-session', 'other@example.test');fixture.detectChanges();expect(dialog.close).toHaveBeenCalled();
    closed.next({ restoredCount: 2, updatedCount: 0, skippedCount: 0, receivingPolicyUnchanged: true });http.expectNone(endpoint);closed.complete();
  });

  it('selects through the badge inside the card without expanding it, with an independent keyboard focus target', () => {
    load();const host: HTMLElement = fixture.nativeElement;
    const card = host.querySelector<HTMLElement>('.export-item')!;
    const header = card.querySelector<HTMLElement>('mat-expansion-panel-header')!;
    const checkbox = card.querySelector<HTMLInputElement>('.selection-badge input')!;
    expect(header.querySelector('input, button, a')).toBeNull();
    expect(card.querySelectorAll('.badge-initial').length).toBe(1);expect(card.querySelector('.item-avatar')).toBeNull();
    expect(checkbox.getAttribute('aria-label')).toContain('Example account');
    checkbox.focus();expect(document.activeElement).toBe(checkbox);checkbox.click();fixture.detectChanges();
    expect(component.exportIds().has(sample.id)).toBeTrue();expect(header.getAttribute('aria-expanded')).toBe('false');
    expect(card.classList.contains('is-selected')).toBeTrue();expect(card.querySelector('.badge-check')?.textContent).toBe('✓');
    header.click();fixture.detectChanges();expect(header.getAttribute('aria-expanded')).toBe('true');expect(checkbox.checked).toBeTrue();
    checkbox.click();fixture.detectChanges();expect(header.getAttribute('aria-expanded')).toBe('true');expect(component.exportIds().size).toBe(0);
  });

  it('keeps collapsed metadata concise, expands explicit actions and never reveals on expansion', () => {
    load();const host: HTMLElement = fixture.nativeElement;
    const header = host.querySelector('mat-expansion-panel-header')!;
    expect(header.textContent).toContain('Example account');expect(header.textContent).toContain('example.test');
    expect(header.querySelector('button, a')).toBeNull();expect(host.querySelector('kn-vault-item-details')).toBeNull();
    open();expect(host.querySelector('.item-metadata')?.textContent).toContain('demo-user');
    expect(Array.from(host.querySelectorAll('.vault-actions a'), link => link.getAttribute('href'))).toEqual([`/vault/edit/${sample.id}`, `/vault/rotate/${sample.id}`]);
    expect(host.querySelector('output.secret')).toBeNull();http.expectNone(request => request.url.includes('/reveal'));
    expect(header.getAttribute('aria-expanded')).toBe('true');
  });

  it('debounces private search, cancels older reads and combines field/expiry scopes in the body', fakeAsync(() => {
    component.search('literal%_');tick(249);http.expectNone(endpoint);tick(1);
    const first = http.expectOne(endpoint);expect(first.request.urlWithParams).toBe(endpoint);expect(first.request.body.query).toBe('literal%_');
    component.search('new query');expect(first.cancelled).toBeTrue();tick(250);
    const latest = http.expectOne(endpoint);latest.flush(page([sample]));
    component.setField('username');const field = http.expectOne(endpoint);expect(field.request.body.field).toBe('username');field.flush(page([sample]));
    component.setExpiry('attention');const expiry = http.expectOne(endpoint);expect(expiry.request.body.expiry).toBe('attention');expect(expiry.request.body.query).toBe('new query');expiry.flush(page());
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { expiry: 'attention', item: null } }));
    expect(JSON.stringify(history.state)).not.toContain('new query');
  }));

  it('honors page sizes, sorting and returned last-page clamping', () => {
    for (const size of [10, 25, 30, 50]) { component.setSize(size);const request = http.expectOne(endpoint);expect(request.request.body.size).toBe(size);request.flush(page([sample], { size, totalMatches: 100 })); }
    component.setSize(10);http.expectOne(endpoint).flush(page([sample], { totalMatches: 21 }));
    component.goToPage(2);const request = http.expectOne(endpoint);expect(request.request.body.page).toBe(2);
    request.flush(page([sample], { page: 1, totalPages: 2, totalMatches: 11, size: 10 }));expect(component.page()).toBe(1);
    component.setOrder('expiry');const sorted = http.expectOne(endpoint);expect(sorted.request.body.sort).toBe('expiry');expect(sorted.request.body.page).toBe(0);sorted.flush(page());
  });

  it('keeps labeled semantic filters and returns focus to results after numbered page navigation', () => {
    load([sample], { totalMatches: 30, totalPages: 3 });
    const host: HTMLElement = fixture.nativeElement;
    expect(host.querySelector('.expiry-filters button[data-tone=healthy]')?.textContent).toContain('30+ days');
    expect(host.querySelectorAll('.expiry-filters button[data-tone=urgent]').length).toBe(3);
    const focus = spyOn(host.querySelector<HTMLElement>('#vault-heading')!, 'focus');
    host.querySelector<HTMLButtonElement>('kn-pagination [aria-label="Page 2"]')!.click();
    const request = http.expectOne(endpoint);expect(request.request.body.page).toBe(1);
    request.flush(page([sample], { page: 1, totalMatches: 30, totalPages: 3 }));fixture.detectChanges();
    expect(focus).toHaveBeenCalledTimes(1);
    expect(host.querySelector('kn-pagination [aria-current=page]')?.textContent?.trim()).toBe('2');
    host.querySelector<HTMLButtonElement>('kn-pagination [aria-label="Page 2"]')!.click();http.expectNone(endpoint);
  });

  it('clears search while retaining the expiry filter and restoring input focus', fakeAsync(() => {
    component.setExpiry('later');http.expectOne(endpoint).flush(page());component.search('label');tick(250);http.expectOne(endpoint).flush(page());
    fixture.detectChanges();const input = fixture.nativeElement.querySelector('#vault-search');spyOn(input, 'focus');
    component.clearSearch(input);const request = http.expectOne(endpoint);expect(request.request.body.expiry).toBe('later');expect(request.request.body.query).toBe('');request.flush(page());expect(input.focus).toHaveBeenCalled();
  }));

  it('distinguishes loading, first-use, filtered-empty and persistent failure/recovery states', () => {
    expect(fixture.nativeElement.textContent).toContain('first item');
    component.refreshVault();fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('Loading your Vault');
    http.expectOne(endpoint).flush({}, { status: 0, statusText: 'Offline' });fixture.detectChanges();
    component.error.set('');fixture.detectChanges();expect(fixture.nativeElement.querySelector('.browse-state.failure')?.textContent).toContain('Could not reach the API');
    component.refreshVault();http.expectOne(endpoint).flush(page([], { counts: { total: 4, expired: 0, soon: 0, upcoming: 0, later: 4, unset: 0 } }));fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No matching items');expect(fixture.nativeElement.querySelector('.browse-state.failure')).toBeNull();
  });

  it('keeps one panel open and cancels pending reveals when another item opens', () => {
    load([sample, second]);open();component.reveal(sample.id);const request = http.expectOne(`/api/v1/vault/items/${sample.id}/reveal`);
    open(1);expect(request.cancelled).toBeTrue();expect(component.expandedId()).toBe(second.id);
    expect(component.revealed()).toEqual({});
    expect(fixture.nativeElement.querySelectorAll('mat-expansion-panel-header[aria-expanded=true]').length).toBe(1);
  });

  it('uses POST, counts down and conceals at fifteen seconds', fakeAsync(() => {
    load();open();reveal();expect(component.remainingSeconds()[sample.id]).toBe(15);
    tick(1000);expect(component.remainingSeconds()[sample.id]).toBe(14);tick(13999);expect(component.revealed()[sample.id]).toBe('synthetic secret');
    tick(1);expect(component.revealed()[sample.id]).toBeUndefined();expect(component.remainingSeconds()[sample.id]).toBeUndefined();
  }));

  it('conceals immediately on collapse, refresh and scope changes and never reveals an absent/collapsed row', () => {
    component.reveal(sample.id);http.expectNone(request => request.url.endsWith('/reveal'));
    load();open();reveal();open();expect(component.revealed()).toEqual({});
    open();reveal();component.refreshVault();expect(component.revealed()).toEqual({});http.expectOne(endpoint).flush(page([sample]));fixture.detectChanges();
    open();reveal();component.setSize(25);expect(component.revealed()).toEqual({});http.expectOne(endpoint).flush(page());
  });

  it('ignores responses after an immediate session change and clears all account metadata', () => {
    load();open();component.reveal(sample.id);const request = http.expectOne(`/api/v1/vault/items/${sample.id}/reveal`);
    TestBed.inject(AuthService).setSession('new-session', 'other@example.test');request.flush({ password: 'late secret' });expect(component.revealed()).toEqual({});
    fixture.detectChanges();expect(component.items()).toEqual([]);expect(component.counts()).toBeNull();expect(component.query()).toBe('');
    expect(component.loadFailure()).toContain('session changed');
  });

  it('cancels obsolete page responses and discards a response after sign-out before its effect runs', () => {
    component.refreshVault();const first = http.expectOne(endpoint);component.refreshVault();expect(first.cancelled).toBeTrue();
    const second = http.expectOne(endpoint);TestBed.inject(AuthService).clearSession();second.flush(page([sample]));
    expect(component.items()).toEqual([]);expect(component.vaultLoaded()).toBeFalse();
  });

  it('confirms deletion once, then refreshes bounded counts and reconciles the page', () => {
    load();const confirm = spyOn(window, 'confirm').and.returnValue(false);component.remove(sample.id);http.expectNone(request => request.method === 'DELETE');
    confirm.and.returnValue(true);component.remove(sample.id);component.remove(sample.id);const request = http.expectOne(`/api/v1/vault/items/${sample.id}`);
    expect(request.request.method).toBe('DELETE');request.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne(endpoint).flush(page());expect(component.items()).toEqual([]);expect(component.counts()?.total).toBe(0);expect(component.pending()).toBeFalse();
  });
});
