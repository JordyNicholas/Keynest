import { Injectable, computed, signal } from '@angular/core';
import { HttpHeaders } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly storageKey = 'keynest.token';
  readonly token = signal(this.restoreToken());
  /** Signed-in display email (not the login form field). */
  readonly email = signal(this.token() ? 'session' : '');
  readonly signedIn = computed(() => this.token().length > 0);

  setSession(token: string, email: string): void {
    this.token.set(token);
    this.email.set(email);
    sessionStorage.setItem(this.storageKey, token);
    localStorage.removeItem(this.storageKey);
  }

  clearSession(): void {
    this.token.set('');
    this.email.set('');
    sessionStorage.removeItem(this.storageKey);
    localStorage.removeItem(this.storageKey);
  }

  authHeaders(): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${this.token()}` });
  }

  private restoreToken(): string {
    const token = sessionStorage.getItem(this.storageKey) ?? '';
    // Raw-token database sessions from earlier versions are invalid after #71.
    localStorage.removeItem(this.storageKey);
    return token;
  }
}
