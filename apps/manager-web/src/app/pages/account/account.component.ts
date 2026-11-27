import { InlineErrorComponent } from '../inline-error.component';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { SignOutDevicesDialogComponent } from './sign-out-devices-dialog.component';
import { MatButtonModule } from '@angular/material/button';
import { API_BASE_URL } from '../../injectionTokens';
import { AuthService } from '../../auth/auth.service';
import { PolicyExplanationComponent } from '../policy-explanation/policy-explanation.component';
import { PasswordPolicySettingsComponent } from './password-policy-settings.component';

import { ApiHealthPageComponent } from '../tools/api-health.component';

import { logoutSession } from '../../auth/logout-session';

@Component({
  selector: 'kn-account-page',
  imports: [InlineErrorComponent, MatButtonModule, MatDialogModule, PolicyExplanationComponent, PasswordPolicySettingsComponent, ApiHealthPageComponent],
  templateUrl: './account.component.html',
  styleUrls: ['../page-shared.css', './account.component.css'],
})
export class AccountPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly dialogs = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);

  private readonly route = inject(ActivatedRoute);
  readonly policyOpen = signal(false);
  readonly healthOpen = signal(false);
  readonly error = signal('');
  readonly loggingOut = signal(false);
  readonly aiSetupRequested = this.route.snapshot.queryParamMap.get('ai') === 'policy-explanation';
  readonly aiSetupOpen = signal(this.aiSetupRequested);

  constructor() {
    this.route.fragment.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(fragment => {
      if (fragment === 'diagnostics-heading') this.healthOpen.set(true);
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
            void this.router.navigateByUrl('/', { replaceUrl: true });
          } else {
            this.error.set('Could not verify account details.');
          }
        },
      });
  }

  signOutDevices(): void {
    if (!this.auth.signedIn() || this.loggingOut() || this.dialogs.getDialogById('sign-out-devices')) return;
    this.dialogs.open(SignOutDevicesDialogComponent, {
      id: 'sign-out-devices', width: 'min(36rem, 94vw)', maxWidth: '94vw',
      ariaLabelledBy: 'sign-out-devices-title', autoFocus: 'first-tabbable', restoreFocus: true,
    });
  }

  logout(): void {
    if (this.loggingOut()) return;
    this.loggingOut.set(true);
    logoutSession(this.http, this.apiBaseUrl, this.auth).subscribe(() => {
      if (this.destroyRef.destroyed) return;
      this.loggingOut.set(false);
      if (!this.auth.signedIn()) {
        this.aiSetupOpen.set(false);this.error.set('');void this.router.navigateByUrl('/');
      }
    });
  }
}
