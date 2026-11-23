import { Component, DestroyRef, effect, inject, OnDestroy, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { API_BASE_URL } from '../../injectionTokens';
import { AuthService } from '../../auth/auth.service';
import { httpErrorMessage } from '../http-error-message';
import { expiryStatus, VaultOverview } from '../vault/vault-metadata';
import { OverviewMetricsComponent } from './overview-metrics.component';
import { ExpiryDistributionComponent } from './expiry-distribution.component';

@Component({
  selector: 'kn-overview-page',
  imports: [DatePipe, MatButtonModule, RouterLink, OverviewMetricsComponent, ExpiryDistributionComponent],
  templateUrl: './overview.component.html',
  styleUrls: ['./overview.component.css', '../vault/expiry-status.css'],
})
export class OverviewPageComponent implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_BASE_URL);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);
  private request?: Subscription;
  private version = 0;
  readonly overview = signal<VaultOverview | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');

  constructor() {
    let session = this.auth.token();
    effect(() => {
      const token = this.auth.token();
      if (session !== token) {
        session = token;this.cancel();this.overview.set(null);this.loading.set(false);
        this.error.set(token ? 'Your session changed. Refresh to load this account’s overview.' : 'Your session ended. Sign in again.');
      }
    });
    this.refresh();
  }

  refresh(): void {
    this.cancel();this.overview.set(null);this.error.set('');
    const token = this.auth.token();
    if (!token) { this.loading.set(false);this.error.set('Sign in to view your account overview.');return; }
    const version = this.version;this.loading.set(true);
    this.request = this.http.get<VaultOverview>(`${this.api}/vault/overview`, { headers: this.auth.authHeaders() })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: body => {
          if (version !== this.version || this.auth.token() !== token) return;
          this.loading.set(false);this.overview.set(body);
        },
        error: ({ status }) => {
          if (version !== this.version || this.auth.token() !== token) return;
          this.loading.set(false);this.error.set(httpErrorMessage(status, 'load your account overview'));
          if (status === 401) this.auth.clearSession();
        },
      });
  }

  expiryLabel(expiresAt: string | undefined): { tone: string; label: string } {
    return expiryStatus(expiresAt, Date.parse(this.overview()!.asOf));
  }

  requiredTypes(data: VaultOverview): string {
    return [data.policy.rules.requireLower && 'Lowercase letters', data.policy.rules.requireUpper && 'uppercase letters',
      data.policy.rules.requireDigit && 'numbers', data.policy.rules.requireSymbol && 'symbols'].filter(Boolean).join(', ') || 'No individual character type is mandatory';
  }

  requiredTypeCount(data: VaultOverview): number {
    return [data.policy.rules.requireLower, data.policy.rules.requireUpper, data.policy.rules.requireDigit, data.policy.rules.requireSymbol].filter(Boolean).length;
  }

  ngOnDestroy(): void { this.cancel();this.overview.set(null); }
  private cancel(): void { ++this.version;this.request?.unsubscribe(); }
}
