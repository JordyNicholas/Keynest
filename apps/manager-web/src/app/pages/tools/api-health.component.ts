import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';

type ApiHealth = { status: string; product: string; policyId: string };

@Component({
  selector: 'kn-api-health',
  imports: [InlineErrorComponent, MatButtonModule],
  templateUrl: './api-health.component.html',
  styleUrls: ['../page-shared.css', './api-health.component.css'],
})
export class ApiHealthPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);


  readonly health = signal<ApiHealth | null>(null);
  readonly checking = signal(false);
  readonly error = signal('');
  readonly attempted = signal(false);

  constructor() {
    this.refreshHealth();
  }

  refreshHealth(): void {
    if (this.checking()) return;
    this.attempted.set(true);
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
