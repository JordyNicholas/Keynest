import { DatePipe } from '@angular/common';
import { Component, DestroyRef, effect, ElementRef, inject, OnDestroy, signal, viewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { VaultExportDialogComponent } from './vault-export-dialog.component';
import { MatExpansionModule } from '@angular/material/expansion';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, Subscription } from 'rxjs';
import { API_BASE_URL } from '../../injectionTokens';
import { AuthService } from '../../auth/auth.service';
import { InlineErrorComponent } from '../inline-error.component';
import { httpErrorMessage } from '../http-error-message';
import { confirmVaultChange } from '../confirm-vault-change';
import { IconComponent } from '../../ui/icon.component';
import { PaginationComponent } from '../../ui/pagination.component';
import { VaultItem } from './vault-item';
import { VaultItemDetailsComponent } from './vault-item-details.component';
import { ExpiryCounts, ExpiryFilter, expiryFilters, expiryStatus, SearchField, VaultOrder, VaultPage } from './vault-metadata';

@Component({
  selector: 'kn-vault-page',
  imports: [InlineErrorComponent, DatePipe, RouterLink, FormsModule, MatButtonModule, MatExpansionModule, VaultItemDetailsComponent, IconComponent, PaginationComponent],
  templateUrl: './vault.component.html',
  styleUrls: ['../page-shared.css', './vault.component.css', './expiry-status.css'],
})
export class VaultPageComponent implements OnDestroy {
  private readonly dialogs = inject(MatDialog);
  private exportDialog?: MatDialogRef<VaultExportDialogComponent>;
  readonly exportIds = signal<ReadonlySet<string>>(new Set());
  readonly selectionError = signal('');
  private readonly api = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private pageRequest?: Subscription;
  private deleteRequest?: Subscription;
  private pageVersion = 0;
  private focusResults = false;
  private readonly resultsHeading = viewChild<ElementRef<HTMLElement>>('resultsHeading');
  private debounce?: ReturnType<typeof setTimeout>;
  private openRequestedItem = false;
  private readonly revealRequests = new Map<string, symbol>();
  private readonly revealSubscriptions = new Map<string, Subscription>();
  private readonly concealTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private readonly countdownTimers = new Map<string, ReturnType<typeof setInterval>>();

  readonly filters = expiryFilters;
  readonly query = signal('');
  readonly field = signal<SearchField>('all');
  readonly expiry = signal<ExpiryFilter>('all');
  readonly order = signal<VaultOrder>('label');
  readonly size = signal(10);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalMatches = signal(0);
  readonly counts = signal<ExpiryCounts | null>(null);
  readonly asOf = signal<string | null>(null);
  readonly selectedItem = signal<string | null>(null);
  readonly expandedId = signal<string | null>(null);
  readonly items = signal<VaultItem[]>([]);
  readonly vaultLoaded = signal(false);
  readonly loading = signal(false);
  readonly loadFailure = signal('');
  readonly pending = signal(false);
  readonly error = signal('');
  readonly revealed = signal<Partial<Record<string, string>>>({});
  readonly revealingId = signal<string | null>(null);
  readonly remainingSeconds = signal<Partial<Record<string, number>>>({});
  readonly revealDurationSeconds = 15;

  constructor() {
    let session = this.auth.token();
    effect(() => {
      const token = this.auth.token();
      if (session !== token) {
        session = token;this.exportIds.set(new Set());this.selectionError.set('');this.exportDialog?.close();this.cancelPage();this.deleteRequest?.unsubscribe();this.clearPage();this.query.set('');this.selectedItem.set(null);
        this.expiry.set('all');this.page.set(0);this.loading.set(false);
        this.loadFailure.set(token ? 'Your session changed. Refresh to load this account’s Vault.' : 'Your session ended. Sign in to load your Vault.');
      }
    });
    let initialized = false;
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const filter = this.filters.find(filter => filter.id === params.get('expiry'))?.id ?? 'all';
      const item = params.get('item');
      if (initialized && filter === this.expiry() && item === this.selectedItem()) return;
      initialized = true;
      this.expiry.set(filter);this.selectedItem.set(item);this.query.set('');this.page.set(0);
      if (item && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(item)) {
        this.cancelPage();this.clearPage();this.loading.set(false);this.loadFailure.set('This selected-item link is invalid. Clear the selection to browse your Vault.');return;
      }
      this.openRequestedItem = !!item;this.refreshVault();
    });
  }

  selectForExport(id: string, checked: boolean): void {
    if (!this.auth.signedIn() || !this.items().some(item => item.id === id)) return;
    const ids = new Set(this.exportIds());this.selectionError.set('');
    if (checked && ids.size >= 500 && !ids.has(id)) { this.selectionError.set('Select at most 500 items per export.');return; }
    if (checked) ids.add(id);else ids.delete(id);
    this.exportIds.set(ids);
  }

  clearExportSelection(): void { this.exportIds.set(new Set());this.selectionError.set(''); }

  openExport(): void {
    if (!this.auth.signedIn() || this.exportDialog) return;
    this.concealAll();
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const dialog = this.dialogs.open(VaultExportDialogComponent, {
      data: { ids: [...this.exportIds()] }, width: '40rem', maxWidth: 'calc(100vw - 2rem)', maxHeight: 'calc(100dvh - 2rem)',
      ariaLabelledBy: 'export-title', autoFocus: 'first-header', restoreFocus: true,
      enterAnimationDuration: reduced ? 0 : 180, exitAnimationDuration: reduced ? 0 : 160,
    });
    this.exportDialog = dialog;
    dialog.afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => { if (this.exportDialog === dialog) this.exportDialog = undefined; });
  }

  expiryStatus(expiresAt: VaultItem['expiresAt']) {
    return expiryStatus(expiresAt, this.asOf() ? Date.parse(this.asOf()!) : Date.now());
  }

  displayHost(url: string | undefined): string {
    if (!url) return '';
    try { return new URL(url).hostname || url; } catch { return url; }
  }

  search(value: string): void {
    this.query.set(value.slice(0, 200));this.page.set(0);this.clearSelectedItem();
    this.cancelPage();this.clearPage();this.loading.set(true);
    this.debounce = setTimeout(() => this.refreshVault(), 250);
  }

  clearSearch(input: HTMLElement): void {
    this.query.set('');this.page.set(0);this.clearSelectedItem();this.refreshVault();input.focus();
  }

  setField(value: string): void {
    this.field.set(['label', 'url', 'username'].includes(value) ? value as SearchField : 'all');this.page.set(0);this.refreshVault();
  }

  setOrder(value: string): void { this.order.set(value === 'expiry' ? 'expiry' : 'label');this.page.set(0);this.refreshVault(); }
  setSize(value: number): void { this.size.set([10, 25, 30, 50].includes(value) ? value : 10);this.page.set(0);this.refreshVault(); }
  goToPage(value: number): void {
    if (this.loading() || this.pending() || !Number.isInteger(value) || this.totalPages() < 1) return;
    const target = Math.min(Math.max(0, value), Math.min(100_000, this.totalPages() - 1));
    if (target === this.page()) return;
    this.focusResults = true;this.page.set(target);this.refreshVault();
  }

  setExpiry(value: ExpiryFilter): void {
    this.expiry.set(value);this.page.set(0);this.clearSelectedItem();this.syncRoute();this.refreshVault();
  }

  clearFilters(): void {
    this.query.set('');this.field.set('all');this.expiry.set('all');this.selectedItem.set(null);this.page.set(0);
    this.syncRoute();this.refreshVault();
  }

  refreshVault(): void {
    this.cancelPage();this.clearPage();this.loadFailure.set('');
    const token = this.auth.token();
    if (!token) { this.loading.set(false);this.loadFailure.set('Sign in to load your Vault.');return; }
    const version = this.pageVersion;
    const expand = this.openRequestedItem;this.openRequestedItem = false;
    this.loading.set(true);
    this.pageRequest = this.http.post<VaultPage>(`${this.api}/vault/items/search`, {
      page: this.page(), size: this.size(), query: this.query(), field: this.field(), expiry: this.expiry(), sort: this.order(), itemId: this.selectedItem(),
    }, { headers: this.auth.authHeaders() }).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
      if (version === this.pageVersion) this.loading.set(false);
    })).subscribe({
      next: body => {
        if (version !== this.pageVersion || this.auth.token() !== token) return;
        this.items.set(body.items);this.counts.set(body.counts);this.asOf.set(body.asOf);this.totalMatches.set(body.totalMatches);
        this.page.set(body.page);this.totalPages.set(body.totalPages);this.vaultLoaded.set(true);this.error.set('');
        if (this.focusResults) { this.focusResults = false;this.resultsHeading()?.nativeElement.focus(); }
        if (expand && body.items.some(item => item.id === this.selectedItem())) this.expandedId.set(this.selectedItem());
      },
      error: ({ status }) => {
        if (version !== this.pageVersion || this.auth.token() !== token) return;
        this.focusResults = false;
        this.loadFailure.set(httpErrorMessage(status, 'load Vault'));this.error.set(this.loadFailure());
      },
    });
  }

  closeItem(id: string): void {
    this.conceal(id);if (this.expandedId() === id) this.expandedId.set(null);
  }

  reveal(id: string): void {
    const token = this.auth.token();
    if (!token || this.expandedId() !== id || !this.items().some(item => item.id === id) || this.revealRequests.has(id)) return;
    const request = Symbol();this.revealRequests.set(id, request);this.revealingId.set(id);
    const subscription = this.http.post<{ password: string }>(`${this.api}/vault/items/${id}/reveal`, {}, { headers: this.auth.authHeaders() })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        if (this.revealRequests.get(id) === request) {
          this.revealRequests.delete(id);this.revealSubscriptions.delete(id);
          if (this.revealingId() === id) this.revealingId.set(null);
        }
      })).subscribe({
        next: body => {
          if (this.revealRequests.get(id) !== request || this.auth.token() !== token || this.expandedId() !== id) return;
          this.conceal(id);this.revealed.update(current => ({ ...current, [id]: body.password }));
          const deadline = Date.now() + this.revealDurationSeconds * 1000;
          this.remainingSeconds.update(current => ({ ...current, [id]: this.revealDurationSeconds }));
          this.countdownTimers.set(id, setInterval(() => {
            const remaining = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
            if (!remaining) this.conceal(id);else this.remainingSeconds.update(current => ({ ...current, [id]: remaining }));
          }, 1000));
          this.concealTimers.set(id, setTimeout(() => this.conceal(id), this.revealDurationSeconds * 1000));this.error.set('');
        },
        error: ({ status }) => {
          if (this.revealRequests.get(id) === request && this.auth.token() === token) this.error.set(httpErrorMessage(status, 'reveal this password'));
        },
      });
    this.revealSubscriptions.set(id, subscription);
  }

  conceal(id: string): void {
    this.revealRequests.delete(id);this.revealSubscriptions.get(id)?.unsubscribe();this.revealSubscriptions.delete(id);
    if (this.revealingId() === id) this.revealingId.set(null);
    clearInterval(this.countdownTimers.get(id));this.countdownTimers.delete(id);
    clearTimeout(this.concealTimers.get(id));this.concealTimers.delete(id);
    this.remainingSeconds.update(current => { const next = { ...current };delete next[id];return next; });
    this.revealed.update(current => { const next = { ...current };delete next[id];return next; });
  }

  remove(id: string): void {
    if (this.pending() || !confirmVaultChange('delete')) return;
    const token = this.auth.token();if (!token) return;
    this.pending.set(true);this.error.set('');this.conceal(id);
    this.deleteRequest = this.http.delete(`${this.api}/vault/items/${id}`, { headers: this.auth.authHeaders() })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.pending.set(false))).subscribe({
        next: () => { if (this.auth.token() === token) this.refreshVault(); },
        error: ({ status }) => { if (this.auth.token() === token) this.error.set(httpErrorMessage(status, 'delete this item')); },
      });
  }

  ngOnDestroy(): void { this.exportDialog?.close();this.exportIds.set(new Set());this.cancelPage();this.deleteRequest?.unsubscribe();this.concealAll(); }

  private syncRoute(): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { expiry: this.expiry() === 'all' ? null : this.expiry(), item: this.selectedItem() }, queryParamsHandling: 'merge', replaceUrl: true });
  }
  private clearSelectedItem(): void { if (this.selectedItem()) { this.selectedItem.set(null);this.syncRoute(); } }
  private cancelPage(): void { clearTimeout(this.debounce);++this.pageVersion;this.pageRequest?.unsubscribe(); }
  private clearPage(): void {
    this.concealAll();this.expandedId.set(null);this.items.set([]);this.counts.set(null);this.asOf.set(null);
    this.totalMatches.set(0);this.totalPages.set(0);this.vaultLoaded.set(false);this.loadFailure.set('');this.error.set('');
  }
  private concealAll(): void {
    for (const id of new Set([...this.revealRequests.keys(), ...this.concealTimers.keys(), ...Object.keys(this.revealed())])) this.conceal(id);
    this.remainingSeconds.set({});this.revealed.set({});
  }
}
