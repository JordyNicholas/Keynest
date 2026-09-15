import { Component, inject, signal } from '@angular/core';
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
export class VaultPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  readonly items = signal<VaultItem[]>([]);
  readonly revealed = signal<Record<string, string>>({});
  readonly newLabel = signal('Example site');
  readonly newUsername = signal('demo');
  readonly newUrl = signal('https://example.com');
  readonly error = signal('');

  constructor() {
    this.refreshVault();
  }

  refreshVault(): void {
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
      .get<{ item: VaultItem; password: string }>(`${this.apiBaseUrl}/vault/items/${id}`, {
        headers: this.auth.authHeaders(),
        params: { reveal: true },
      })
      .subscribe({
        next: (body) => {
          this.revealed.update((current) => ({ ...current, [id]: body.password }));
        },
        error: () => this.error.set('Reveal failed'),
      });
  }

  remove(id: string): void {
    this.http.delete(`${this.apiBaseUrl}/vault/items/${id}`, { headers: this.auth.authHeaders() }).subscribe({
      next: () => {
        this.revealed.update((current) => {
          const next = { ...current };
          delete next[id];
          return next;
        });
        this.refreshVault();
      },
      error: () => this.error.set('Delete failed'),
    });
  }
}
