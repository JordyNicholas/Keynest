import { computed, Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly token = signal(localStorage.getItem('keynest.token') ?? '');
  readonly email = signal('demo@keynest.dev');
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
}
