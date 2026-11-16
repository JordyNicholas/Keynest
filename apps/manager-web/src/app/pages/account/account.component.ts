import { InlineErrorComponent } from '../inline-error.component';
import { PasswordVisibilityDirective } from '../password-visibility.directive';
import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { API_BASE_URL } from '../../injectionTokens';
import { AuthService } from '../../auth/auth.service';

type AuthResponse = { userId: string; email: string; token: string; expiresAt: string };

@Component({
  selector: 'kn-account-page',
  imports: [InlineErrorComponent, PasswordVisibilityDirective, FormsModule, MatButtonModule],
  templateUrl: './account.component.html',
  styleUrls: ['../page-shared.css', './account.component.css'],
})
export class AccountPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);

  readonly formEmail = signal('');
  readonly formPassword = signal('');
  readonly error = signal('');

  register(): void {
    this.http
      .post<AuthResponse>(`${this.apiBaseUrl}/auth/register`, {
        email: this.formEmail(),
        password: this.formPassword(),
      })
      .subscribe({
        next: (body) => this.onAuth(body),
        error: (err) => this.error.set(err?.error?.message ?? 'Register failed'),
      });
  }

  login(): void {
    this.http
      .post<AuthResponse>(`${this.apiBaseUrl}/auth/login`, {
        email: this.formEmail(),
        password: this.formPassword(),
      })
      .subscribe({
        next: (body) => this.onAuth(body),
        error: (err) => this.error.set(err?.error?.message ?? 'Login failed'),
      });
  }

  logout(): void {
    this.http.post(`${this.apiBaseUrl}/auth/logout`, {}, { headers: this.auth.authHeaders() }).subscribe({
      next: () => this.clearAuth(),
      error: () => this.clearAuth(),
    });
  }

  private onAuth(body: AuthResponse): void {
    this.formPassword.set('');
    this.auth.setSession(body.token, body.email);
    this.error.set('');
  }

  private clearAuth(): void {
    this.auth.clearSession();
    this.error.set('');
  }
}
