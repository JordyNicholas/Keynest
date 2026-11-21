import { InlineErrorComponent } from '../inline-error.component';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { API_BASE_URL } from '../../injectionTokens';
import { AuthService } from '../../auth/auth.service';
import { PolicyExplanationComponent } from '../policy-explanation/policy-explanation.component';
import { PasswordPolicySettingsComponent } from './password-policy-settings.component';

import { AuthFormComponent, AuthMode } from '../../auth/auth-form.component';
import { authReturnUrl } from '../../auth/auth-return-url';

@Component({
  selector: 'kn-account-page',
  imports: [InlineErrorComponent, AuthFormComponent, MatButtonModule, RouterLink, PolicyExplanationComponent, PasswordPolicySettingsComponent],
  templateUrl: './account.component.html',
  styleUrls: ['../page-shared.css', './account.component.css'],
})
export class AccountPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);

  private readonly route = inject(ActivatedRoute);
  readonly authMode = signal<AuthMode>('signin');
  readonly policyOpen = signal(false);
  readonly error = signal('');
  readonly aiSetupRequested = this.route.snapshot.queryParamMap.get('ai') === 'policy-explanation';
  readonly aiSetupOpen = signal(this.aiSetupRequested);

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.authMode.set(params.get('auth') === 'signup' ? 'signup' : 'signin');
    });
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

  onAuth(): void {
    void this.router.navigateByUrl(authReturnUrl(this.router, this.route.snapshot.queryParamMap.get('returnTo')));
  }

  changeAuthMode(mode: AuthMode): void {
    this.authMode.set(mode);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { auth: mode }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  logout(): void {
    this.http.post(`${this.apiBaseUrl}/auth/logout`, {}, { headers: this.auth.authHeaders() }).subscribe({
      next: () => this.clearAuth(),
      error: () => this.clearAuth(),
    });
  }

  private clearAuth(): void {
    this.aiSetupOpen.set(false);
    this.auth.clearSession();
    this.error.set('');
  }
}
