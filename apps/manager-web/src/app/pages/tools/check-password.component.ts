import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';
import { passwordViolationMessages } from '../password-violation-messages';

@Component({
  selector: 'kn-check-password-page',
  imports: [InlineErrorComponent, MatButtonModule],
  templateUrl: './check-password.component.html',
  styleUrl: '../page-shared.css',
})
export class CheckPasswordPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  readonly validating = signal(false);
  readonly validationError = signal('');
  readonly validation = signal<{ valid: boolean; messages: string[]; policyId: string } | null>(null);

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
}
