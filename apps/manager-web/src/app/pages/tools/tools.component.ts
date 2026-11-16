import { InlineErrorComponent } from '../inline-error.component';
import { passwordViolationMessages } from '../password-violation-messages';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { JsonPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { API_BASE_URL } from '../../injectionTokens';

@Component({
  selector: 'kn-tools-page',
  imports: [InlineErrorComponent, JsonPipe, MatButtonModule],
  templateUrl: './tools.component.html',
  styleUrls: ['../page-shared.css', './tools.component.css'],
})
export class ToolsPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);

  private readonly destroyRef = inject(DestroyRef);

  readonly validating = signal(false);
  readonly validationError = signal('');
  readonly validation = signal<{ valid: boolean; messages: string[]; policyId: string } | null>(null);
  readonly health = signal<unknown>(null);
  readonly generated = signal('');
  readonly error = signal('');

  constructor() {
    this.refreshHealth();
  }

  refreshHealth(): void {
    this.http.get(`${this.apiBaseUrl}/tools/health`).subscribe({
      next: (body) => {
        this.health.set(body);
        this.error.set('');
      },
      error: () => this.error.set('API unreachable — start manager-api on :8080'),
    });
  }

  generate(): void {
    // Clear first so @if destroys the output and kn-leave can run; then enter on success.
    this.generated.set('');
    this.http.post<{ password: string }>(`${this.apiBaseUrl}/tools/generate`, {}).subscribe({
      next: (body) => {
        this.generated.set(body.password);
        this.error.set('');
      },
      error: () => this.error.set('Generate failed'),
    });
  }

  validateSample(event: Event, input: HTMLInputElement): void {
    event.preventDefault();
    if (this.validating() || !input.value) return;

    const password = input.value;
    input.value = '';
    this.validation.set(null);
    this.validationError.set('');
    this.validating.set(true);
    this.http.post<{ valid: boolean; violations: string[]; policyId: string }>(
      `${this.apiBaseUrl}/tools/validate?operation=update`, { password },
    ).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.validating.set(false)))
      .subscribe({
        next: ({ valid, policyId, violations }) => this.validation.set({
          valid, policyId, messages: passwordViolationMessages(violations),
        }),
        error: () => this.validationError.set('Could not validate the sample password. Try again.'),
      });
  }

  clearGenerated(): void {
    this.generated.set('');
  }
}
