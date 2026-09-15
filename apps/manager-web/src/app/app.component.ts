import { Component, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { JsonPipe } from '@angular/common';
import { API_BASE_URL } from './injectionTokens';
import { MatButtonModule } from '@angular/material/button';

type AuthResponse = { userId: string; email: string; token: string; expiresAt: string };
type VaultItem = {
  id: string;
  label: string;
  url?: string;
  username?: string;
  policyId: string;
  expiresAt?: string;
};

@Component({
  selector: 'app-root',
  imports: [FormsModule, JsonPipe, MatButtonModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);

  readonly token = signal(localStorage.getItem('keynest.token') ?? '');
  readonly email = signal('demo@keynest.dev');
  readonly password = signal('DemoPassphrase!42');
  readonly authEmail = signal('');
  readonly items = signal<VaultItem[]>([]);
  readonly revealed = signal<Record<string, string>>({});
  readonly newLabel = signal('Example site');
  readonly newUsername = signal('demo');
  readonly newUrl = signal('https://example.com');
  readonly health = signal<unknown>(null);
  readonly generated = signal('');
  readonly error = signal('');
  readonly signedIn = computed(() => this.token().length > 0);

  constructor() {
    this.refreshHealth();
    if (this.signedIn()) {
      this.authEmail.set('session');
      this.refreshVault();
    }
  }

  private authHeaders(): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${this.token()}` });
  }

  refreshHealth(): void {
    this.http.get(`${this.apiBaseUrl}/tools/health`).subscribe({
      next: (body) => {
        this.health.set(body);
        this.error.set('');
      },
      error: () => this.error.set('API unreachable — start manager-api on :8080'),
    });
  }

  register(): void {
    this.http
      .post<AuthResponse>(`${this.apiBaseUrl}/auth/register`, {
        email: this.email(),
        password: this.password(),
      })
      .subscribe({
        next: (body) => this.onAuth(body),
        error: (err) => this.error.set(err?.error?.message ?? 'Register failed'),
      });
  }

  login(): void {
    this.http
      .post<AuthResponse>(`${this.apiBaseUrl}/auth/login`, {
        email: this.email(),
        password: this.password(),
      })
      .subscribe({
        next: (body) => this.onAuth(body),
        error: (err) => this.error.set(err?.error?.message ?? 'Login failed'),
      });
  }

  logout(): void {
    this.http.post(`${this.apiBaseUrl}/auth/logout`, {}, { headers: this.authHeaders() }).subscribe({
      next: () => this.clearAuth(),
      error: () => this.clearAuth(),
    });
  }

  refreshVault(): void {
    this.http.get<VaultItem[]>(`${this.apiBaseUrl}/vault/items`, { headers: this.authHeaders() }).subscribe({
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
        { headers: this.authHeaders() }
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
        headers: this.authHeaders(),
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
    this.http.delete(`${this.apiBaseUrl}/vault/items/${id}`, { headers: this.authHeaders() }).subscribe({
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

  generate(): void {
    // Clear first so @if destroys the output and kn-leave can run; then enter on success.
    this.generated.set('');
    this.http.post<{ password: string }>(`${this.apiBaseUrl}/tools/generate`, {}).subscribe({
      next: (body) => {
        this.generated.set(body.password);
        this.error.set('');
      },
      error: () => this.error.set('Generate failed'),
    });
  }

  clearGenerated(): void {
    this.generated.set('');
  }

  private onAuth(body: AuthResponse): void {
    this.token.set(body.token);
    this.authEmail.set(body.email);
    localStorage.setItem('keynest.token', body.token);
    this.error.set('');
    this.refreshVault();
  }

  private clearAuth(): void {
    this.token.set('');
    this.authEmail.set('');
    this.items.set([]);
    this.revealed.set({});
    this.generated.set('');
    localStorage.removeItem('keynest.token');
  }
}
