import { InlineErrorComponent } from '../inline-error.component';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { API_BASE_URL } from '../../injectionTokens';
import { AuthService } from '../../auth/auth.service';
import { PolicyExplanationComponent } from '../policy-explanation/policy-explanation.component';
import { PasswordPolicySettingsComponent } from './password-policy-settings.component';

type AuthResponse = { userId: string; email: string; token: string; expiresAt: string };

@Component({
  selector: 'kn-account-page',
  imports: [InlineErrorComponent, FormsModule, MatButtonModule, RouterLink, PolicyExplanationComponent, PasswordPolicySettingsComponent],
  templateUrl: './account.component.html',
  styleUrls: ['../page-shared.css', './account.component.css'],
})
export class AccountPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);

  readonly minimumMasterPasswordLength = 15;
  readonly formEmail = signal('');
  readonly policyOpen = signal(false);
  readonly formPassword = signal('');
  readonly error = signal('');
  readonly aiSetupRequested = inject(ActivatedRoute).snapshot.queryParamMap.get('ai') === 'policy-explanation';
  readonly aiSetupOpen = signal(this.aiSetupRequested);

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
    this.error.set('');
    if (!this.validEmail()) return;
    const password = this.formPassword();
    if ([...password].length < this.minimumMasterPasswordLength) {
      this.error.set(`Your master password must contain at least ${this.minimumMasterPasswordLength} characters to create an account.`);
      return;
    }
    this.http
      .post<AuthResponse>(`${this.apiBaseUrl}/auth/register`, {
        email: this.formEmail(),
        password,
      })
      .subscribe({
        next: (body) => this.onAuth(body),
        error: ({ status }) => this.error.set(status === 409 ? 'An account with this email already exists. Try signing in.'
          : status === 400 ? 'Check the email address and master password requirements.'
          : this.connectionError(status)),
      });
  }

  login(): void {
    this.error.set('');
    this.http
      .post<AuthResponse>(`${this.apiBaseUrl}/auth/login`, {
        email: this.formEmail(),
        password: this.formPassword(),
      })
      .subscribe({
        next: (body) => this.onAuth(body),
        error: ({ status }) => this.error.set(status === 401 ? 'The email address or master password is incorrect.'
          : status === 400 ? 'Enter your email address and master password.'
          : this.connectionError(status)),
      });
  }

  logout(): void {
    this.http.post(`${this.apiBaseUrl}/auth/logout`, {}, { headers: this.auth.authHeaders() }).subscribe({
      next: () => this.clearAuth(),
      error: () => this.clearAuth(),
    });
  }

  private validEmail(): boolean {
    const input = document.createElement('input');
    input.type = 'email';
    input.required = true;
    input.value = this.formEmail().trim();
    if (input.checkValidity()) return true;
    this.error.set('Enter a valid email address, such as name@example.com.');
    return false;
  }

  private connectionError(status: number): string {
    if (status === 429) return 'Too many attempts. Wait a minute and try again.';
    if (status === 503) return 'Password operations are busy. Wait a moment and try again.';
    return 'Could not connect to Manager. Check API health and try again.';
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
