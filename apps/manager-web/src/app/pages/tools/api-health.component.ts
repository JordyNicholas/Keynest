import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';
import { JsonPipe } from '@angular/common';

@Component({
  selector: 'kn-api-health-page',
  imports: [InlineErrorComponent, MatButtonModule, JsonPipe],
  templateUrl: './api-health.component.html',
  styleUrl: '../page-shared.css',
})
export class ApiHealthPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  readonly health = signal<unknown>(null);
  readonly checking = signal(false);
  readonly error = signal('');

  constructor() {
    this.refreshHealth();
  }

  refreshHealth(): void {
    if (this.checking()) return;
    this.health.set(null);
    this.error.set('');
    this.checking.set(true);
    this.http.get(`${this.apiBaseUrl}/tools/health`)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.checking.set(false)))
      .subscribe({
        next: (body) => this.health.set(body),
        error: () => this.error.set('Could not reach the configured Manager API.'),
      });
  }
}
