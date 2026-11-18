import { confirmVaultChange } from '../confirm-vault-change';
import { InlineErrorComponent } from '../inline-error.component';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { Component, computed, DestroyRef, effect, inject, OnDestroy, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { API_BASE_URL } from '../../injectionTokens';
import { AuthService } from '../../auth/auth.service';

import { VaultItem } from './vault-item';

const DAY_MS = 86_400_000;

@Component({
  selector: 'kn-vault-page',
  imports: [InlineErrorComponent, DatePipe, RouterLink, MatButtonModule],
  templateUrl: './vault.component.html',
  styleUrls: ['../page-shared.css', './vault.component.css'],
})
export class VaultPageComponent implements OnDestroy {
  private static readonly REVEAL_DURATION_MS = 15_000;

  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);
  readonly vaultLoaded = signal(false);
  private readonly revealRequests = new Map<string, symbol>();
  private readonly concealTimers = new Map<string, ReturnType<typeof setTimeout>>();

  readonly items = signal<VaultItem[]>([]);
  readonly upcomingExpiry = computed(() => {
    const now = Date.now();
    return this.items()
      .flatMap((item) => {
        const expiry = item.expiresAt ? Date.parse(item.expiresAt) : NaN;
        const notice = Math.max(0, item.notifyBeforeDays ?? 0);
        return expiry <= now + notice * DAY_MS ? [{ item, expiry }] : [];
      })
      .sort((left, right) => left.expiry - right.expiry)
      .map(({ item }) => item);
  });
  readonly revealed = signal<Record<string, string>>({});
  readonly pending = signal(false);
  readonly error = signal('');
  readonly revealDurationSeconds = VaultPageComponent.REVEAL_DURATION_MS / 1000;

  constructor() {
    let session = this.auth.token();
    effect(() => {
      const token = this.auth.token();
      if (session !== token) {
        session = token;
        this.items.set([]);
        this.concealAll();
        this.vaultLoaded.set(false);
      }
    });
    this.refreshVault();
  }

  expiryStatus(expiresAt: VaultItem['expiresAt']): { tone: string; label: string } {
    const expiry = expiresAt ? Date.parse(expiresAt) : NaN;
    if (!Number.isFinite(expiry)) return { tone: 'neutral', label: 'No expiry' };

    const remaining = expiry - Date.now();
    if (remaining <= 0) return { tone: 'urgent', label: 'Expired' };
    const days = Math.floor(remaining / DAY_MS);
    const tone = days >= 30 ? 'healthy' : days >= 14 ? 'warning' : 'urgent';
    if (days === 0) return { tone, label: 'Less than a day left' };
    return { tone, label: days === 1 ? '1 day left' : `${days} days left` };
  }

  refreshVault(): void {
    this.vaultLoaded.set(false);
    this.concealAll();
    const token = this.auth.token();
    if (!token) return;
    this.http.get<VaultItem[]>(`${this.apiBaseUrl}/vault/items`, { headers: this.auth.authHeaders() })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (body) => {
        if (this.auth.token() !== token) return;
        this.items.set(body);
        this.vaultLoaded.set(true);
        this.error.set('');
      },
      error: () => {
        if (this.auth.token() === token) this.error.set('Vault list failed — sign in again');
      },
    });
  }

  reveal(id: string): void {
    const token = this.auth.token();
    if (!token || this.revealRequests.has(id)) return;
    const request = Symbol();
    this.revealRequests.set(id, request);
    this.http
      .post<{ item: VaultItem; password: string }>(
        `${this.apiBaseUrl}/vault/items/${id}/reveal`,
        {},
        { headers: this.auth.authHeaders() }
      )
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        if (this.revealRequests.get(id) === request) this.revealRequests.delete(id);
      }))
      .subscribe({
        next: (body) => {
          if (this.revealRequests.get(id) !== request || this.auth.token() !== token) return;
          this.conceal(id);
          this.revealed.update((current) => ({ ...current, [id]: body.password }));
          this.concealTimers.set(
            id,
            setTimeout(() => this.conceal(id), VaultPageComponent.REVEAL_DURATION_MS)
          );
          this.error.set('');
        },
        error: () => {
          if (this.revealRequests.get(id) === request && this.auth.token() === token) {
            this.error.set('Reveal failed');
          }
        },
      });
  }

  conceal(id: string): void {
    this.revealRequests.delete(id);
    const timer = this.concealTimers.get(id);
    if (timer !== undefined) {
      clearTimeout(timer);
      this.concealTimers.delete(id);
    }
    this.revealed.update((current) => {
      if (!(id in current)) {
        return current;
      }
      const next = { ...current };
      delete next[id];
      return next;
    });
  }

  remove(id: string): void {
    if (this.pending() || !confirmVaultChange('delete')) return;
    this.pending.set(true);
    this.error.set('');
    this.conceal(id);
    this.http.delete(`${this.apiBaseUrl}/vault/items/${id}`, { headers: this.auth.authHeaders() })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.pending.set(false)))
      .subscribe({
        next: () => {
          this.items.update((items) => items.filter((item) => item.id !== id));
        },
        error: () => this.error.set('Delete failed'),
      });
  }

  ngOnDestroy(): void {
    this.concealAll();
  }

  private concealAll(): void {
    this.revealRequests.clear();
    for (const timer of this.concealTimers.values()) {
      clearTimeout(timer);
    }
    this.concealTimers.clear();
    this.revealed.set({});
  }
}
