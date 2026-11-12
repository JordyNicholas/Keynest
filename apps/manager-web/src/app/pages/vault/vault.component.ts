import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { Component, DestroyRef, effect, inject, OnDestroy, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { API_BASE_URL } from '../../injectionTokens';
import { AuthService } from '../../auth/auth.service';

type VaultItem = {
  id: string;
  label: string;
  url?: string;
  username?: string;
  policyId: string;
  expiresAt?: string;
  lastRotatedAt?: string;
  notifyBeforeDays?: number;
};

@Component({
  selector: 'kn-vault-page',
  imports: [DatePipe, FormsModule],
  templateUrl: './vault.component.html',
  styleUrls: ['../page-shared.css', './vault.component.css'],
})
export class VaultPageComponent implements OnDestroy {
  private static readonly REVEAL_DURATION_MS = 15_000;

  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);
  private readonly concealTimers = new Map<string, ReturnType<typeof setTimeout>>();

  readonly items = signal<VaultItem[]>([]);
  readonly revealed = signal<Record<string, string>>({});
  readonly newLabel = signal('Example site');
  readonly newUsername = signal('demo');
  readonly newUrl = signal('https://example.com');
  readonly newExpiryDays = signal<number | null>(90);
  readonly newNotifyDays = signal<number | null>(14);
  readonly editing = signal<string | null>(null);
  readonly expiryDays = signal<number | null>(null);
  readonly notifyDays = signal<number | null>(null);
  readonly pending = signal(false);
  readonly error = signal('');
  readonly revealDurationSeconds = VaultPageComponent.REVEAL_DURATION_MS / 1000;

  constructor() {
    effect(() => {
      if (!this.auth.signedIn()) {
        this.concealAll();
        this.editing.set(null);
      }
    });
    this.refreshVault();
  }

  refreshVault(): void {
    this.concealAll();
    this.http.get<VaultItem[]>(`${this.apiBaseUrl}/vault/items`, { headers: this.auth.authHeaders() }).subscribe({
      next: (body) => {
        this.items.set(body);
        this.error.set('');
      },
      error: () => this.error.set('Vault list failed — sign in again'),
    });
  }

  createGeneratedItem(): void {
    if (!this.validDays(this.newExpiryDays(), this.newNotifyDays())) return;
    this.http
      .post<VaultItem>(
        `${this.apiBaseUrl}/vault/items`,
        {
          label: this.newLabel(),
          url: this.newUrl(),
          username: this.newUsername(),
          generate: true,
          expiresInDays: this.newExpiryDays(),
          notifyBeforeDays: this.newNotifyDays(),
        },
        { headers: this.auth.authHeaders() }
      )
      .subscribe({
        next: () => {
          this.refreshVault();
          this.error.set('');
        },
        error: (err) => this.error.set(err?.error?.message ?? 'Create failed'),
      });
  }

  openEditor(item: VaultItem): void {
    this.conceal(item.id);
    this.expiryDays.set(null);
    this.notifyDays.set(item.notifyBeforeDays ?? null);
    this.error.set('');
    this.editing.set(item.id);
  }

  generateReplacement(input: HTMLInputElement): void {
    this.pending.set(true);
    this.error.set('');
    this.http.post<{ password: string }>(`${this.apiBaseUrl}/tools/generate`, {})
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.pending.set(false)))
      .subscribe({
        next: ({ password }) => {
          if (input.isConnected && this.auth.signedIn()) input.value = password;
        },
        error: () => this.error.set('Could not generate a replacement password'),
      });
  }

  saveItem(item: VaultItem, input: HTMLInputElement): void {
    if (this.pending() || !this.validDays(this.expiryDays(), this.notifyDays())) return;
    const password = input.value || undefined;
    if (password && !password.trim()) {
      this.error.set('Enter a replacement password or leave it blank.');
      return;
    }
    const expiresInDays = this.expiryDays();
    const notifyBeforeDays = this.notifyDays();
    if (!password && expiresInDays === null && notifyBeforeDays === (item.notifyBeforeDays ?? null)) return;
    this.pending.set(true);
    this.error.set('');
    this.conceal(item.id);
    this.http.put<VaultItem>(`${this.apiBaseUrl}/vault/items/${item.id}`,
      { password, expiresInDays, notifyBeforeDays },
      { headers: this.auth.authHeaders() },
    ).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.pending.set(false)))
      .subscribe({
        next: (updated) => {
          input.value = '';
          this.items.update((items) => items.map((entry) => entry.id === updated.id ? updated : entry));
          this.editing.set(null);
        },
        error: () => this.error.set('Could not save the vault item. Check the password policy and try again.'),
      });
  }

  private validDays(expiry: number | null, notice: number | null): boolean {
    const valid = (value: number | null, min: number) =>
      value === null || (Number.isInteger(value) && value >= min && value <= 2_147_483_647);
    if (valid(expiry, 1) && valid(notice, 0)) return true;
    this.error.set('Enter whole days. Expiry must be at least 1 and notice at least 0.');
    return false;
  }

  reveal(id: string): void {
    this.http
      .post<{ item: VaultItem; password: string }>(
        `${this.apiBaseUrl}/vault/items/${id}/reveal`,
        {},
        { headers: this.auth.authHeaders() }
      )
      .subscribe({
        next: (body) => {
          this.conceal(id);
          this.revealed.update((current) => ({ ...current, [id]: body.password }));
          this.concealTimers.set(
            id,
            setTimeout(() => this.conceal(id), VaultPageComponent.REVEAL_DURATION_MS)
          );
          this.error.set('');
        },
        error: () => this.error.set('Reveal failed'),
      });
  }

  conceal(id: string): void {
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
    this.http.delete(`${this.apiBaseUrl}/vault/items/${id}`, { headers: this.auth.authHeaders() }).subscribe({
      next: () => {
        this.conceal(id);
        this.refreshVault();
      },
      error: () => this.error.set('Delete failed'),
    });
  }

  ngOnDestroy(): void {
    this.concealAll();
  }

  private concealAll(): void {
    for (const timer of this.concealTimers.values()) {
      clearTimeout(timer);
    }
    this.concealTimers.clear();
    this.revealed.set({});
  }
}
