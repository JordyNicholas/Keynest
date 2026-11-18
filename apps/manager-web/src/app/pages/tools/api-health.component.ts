import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';
import { Location } from '@angular/common';
import { Router } from '@angular/router';

type ApiHealth = { status: string; product: string; policyId: string };

@Component({
  selector: 'kn-api-health-page',
  imports: [InlineErrorComponent, MatButtonModule],
  templateUrl: './api-health.component.html',
  styleUrl: '../page-shared.css',
})
export class ApiHealthPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  private readonly router = inject(Router);
  private readonly location = inject(Location);

  readonly health = signal<ApiHealth | null>(null);
  readonly checking = signal(false);
  readonly error = signal('');

  constructor() {
    this.refreshHealth();
  }

  back(): void {
    if (this.router.lastSuccessfulNavigation()?.previousNavigation) {
      this.location.back();
    } else {
      void this.router.navigateByUrl('/account');
    }
  }

  refreshHealth(): void {
    if (this.checking()) return;
    this.health.set(null);
    this.error.set('');
    this.checking.set(true);
    this.http.get<ApiHealth>(`${this.apiBaseUrl}/tools/health`)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.checking.set(false)))
      .subscribe({
        next: (body) => this.health.set(body),
        error: () => this.error.set('Could not reach the configured Manager API.'),
      });
  }
}
