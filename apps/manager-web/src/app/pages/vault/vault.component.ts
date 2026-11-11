import { Component, effect, inject, OnDestroy, signal } from '@angular/core';
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
};

@Component({
  selector: 'kn-vault-page',
  imports: [FormsModule],
  templateUrl: './vault.component.html',
  styleUrls: ['../page-shared.css', './vault.component.css'],
})
export class VaultPageComponent implements OnDestroy {
  private static readonly REVEAL_DURATION_MS = 15_000;

  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly concealTimers = new Map<string, ReturnType<typeof setTimeout>>();

  readonly items = signal<VaultItem[]>([]);
  readonly revealed = signal<Record<string, string>>({});
  readonly newLabel = signal('Example site');
  readonly newUsername = signal('demo');
  readonly newUrl = signal('https://example.com');
  readonly error = signal('');
  readonly revealDurationSeconds = VaultPageComponent.REVEAL_DURATION_MS / 1000;

  constructor() {
    effect(() => {
      if (!this.auth.signedIn()) {
        this.concealAll();
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
    this.http
      .post<VaultItem>(
        `${this.apiBaseUrl}/vault/items`,
        {
          label: this.newLabel(),
          url: this.newUrl(),
          username: this.newUsername(),
          generate: true,
          expiresInDays: 90,
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
