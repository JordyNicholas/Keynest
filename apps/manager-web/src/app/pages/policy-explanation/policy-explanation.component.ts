import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';

@Component({
  templateUrl: './policy-explanation.component.html',
  styleUrl: '../page-shared.css',
})
export class PolicyExplanationComponent {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;

  readonly providerId = 'openai';
  readonly policyId = 'keynest.default.v1';
  readonly pending = signal(false);
  readonly explanation = signal('');
  readonly error = signal('');

  explain(event: Event, keyInput: HTMLInputElement): void {
    event.preventDefault();
    if (this.pending() || !keyInput.reportValidity()) return;

    const headers = this.auth.authHeaders().set('X-Keynest-AI-Key', keyInput.value);
    keyInput.value = '';
    this.explanation.set('');
    this.error.set('');
    this.pending.set(true);

    this.request = this.http.post<{ explanation: string }>(
      `${this.apiBaseUrl}/ai/policy/explain`,
      { providerId: this.providerId, policyId: this.policyId },
      { headers },
    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => {
        this.explanation.set(result.explanation);
        this.pending.set(false);
      },
      error: () => {
        this.error.set('Could not get a policy explanation. Check that AI explanations are enabled and that your provider key is valid.');
        this.pending.set(false);
      },
    });
  }

  cancel(): void {
    this.request?.unsubscribe();
    this.pending.set(false);
  }
}
