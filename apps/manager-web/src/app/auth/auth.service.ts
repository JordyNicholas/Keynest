import { Injectable, computed, signal } from '@angular/core';
import { HttpHeaders } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly token = signal(localStorage.getItem('keynest.token') ?? '');
  /** Signed-in display email (not the login form field). */
  readonly email = signal(this.token() ? 'session' : '');
  readonly signedIn = computed(() => this.token().length > 0);

  setSession(token: string, email: string): void {
    this.token.set(token);
    this.email.set(email);
    localStorage.setItem('keynest.token', token);
  }

  clearSession(): void {
    this.token.set('');
    this.email.set('');
    localStorage.removeItem('keynest.token');
  }

  authHeaders(): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${this.token()}` });
  }
}
