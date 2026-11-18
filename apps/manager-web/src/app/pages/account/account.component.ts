import { InlineErrorComponent } from '../inline-error.component';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { API_BASE_URL } from '../../injectionTokens';
import { AuthService } from '../../auth/auth.service';
import { PolicyExplanationComponent } from '../policy-explanation/policy-explanation.component';

type AuthResponse = { userId: string; email: string; token: string; expiresAt: string };

@Component({
  selector: 'kn-account-page',
  imports: [InlineErrorComponent, FormsModule, MatButtonModule, RouterLink, PolicyExplanationComponent],
  templateUrl: './account.component.html',
  styleUrls: ['../page-shared.css', './account.component.css'],
})
export class AccountPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);

  readonly formEmail = signal('');
  readonly formPassword = signal('');
  readonly error = signal('');
  readonly aiSetupOpen = signal(false);

  constructor() {
    const token = this.auth.token();
    if (!token) return;
    this.http.post<{ email: string }>(`${this.apiBaseUrl}/auth/me`, {}, { headers: this.auth.authHeaders() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ email }) => {
          if (this.auth.token() === token) this.auth.email.set(email);
        },
        error: (response) => {
          if (this.auth.token() !== token) return;
          if (response.status === 401) {
            this.auth.clearSession();
            this.error.set('Your session expired. Sign in again.');
          } else {
            this.error.set('Could not verify account details.');
          }
        },
      });
  }

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
    void this.router.navigateByUrl('/vault');
  }

  private clearAuth(): void {
    this.aiSetupOpen.set(false);
    this.auth.clearSession();
    this.error.set('');
  }
}
