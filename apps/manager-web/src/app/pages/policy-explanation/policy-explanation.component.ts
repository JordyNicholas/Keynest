import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { Router } from '@angular/router';
import { InlineErrorComponent } from '../inline-error.component';
import { finalize, Subscription } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';

@Component({
  selector: 'kn-policy-explanation',
  imports: [InlineErrorComponent, MatButtonModule],
  templateUrl: './policy-explanation.component.html',
  styleUrls: ['../page-shared.css', './policy-explanation.component.css'],
})
export class PolicyExplanationComponent {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;

  readonly providerId = 'openai';
  readonly policyId = 'keynest.default.v1';
  readonly pending = signal(false);
  readonly error = signal('');

  explain(event: Event, keyInput: HTMLInputElement): void {
    event.preventDefault();
    const session = this.auth.token();
    if (this.pending() || !session || !keyInput.reportValidity()) return;
    if (!/^[\x21-\x7e]{1,4096}$/.test(keyInput.value)) {
      keyInput.value = '';
      this.error.set('Enter an OpenAI API key without spaces.');
      return;
    }

    const headers = this.auth.authHeaders().set('X-Keynest-AI-Key', keyInput.value);
    keyInput.value = '';
    this.error.set('');
    this.pending.set(true);

    this.request = this.http.post<{ explanation: string }>(
      `${this.apiBaseUrl}/ai/policy/explain`,
      { providerId: this.providerId, policyId: this.policyId },
      { headers },
    ).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.pending.set(false)),
    ).subscribe({
      next: ({ explanation }) => {
        if (this.auth.token() !== session) return;
        void this.router.navigate(['/tools'], {
          queryParams: { tool: 'check' },
          fragment: 'why-these-rules',
          info: { policyExplanation: explanation },
        });
      },
      error: ({ error }) => {
        if (this.auth.token() !== session) return;
        switch (error?.error) {
          case 'AI_CREDENTIAL_REQUIRED':
          case 'AI_CREDENTIAL_INVALID':
          case 'AI_PROVIDER_AUTH_FAILED':
            this.error.set('OpenAI did not accept this key. Enter a valid key with access to the configured model.');
            break;
          case 'AI_DISABLED':
            this.error.set('AI explanations are disabled for this instance. Contact your Keynest administrator.');
            break;
          default:
            this.error.set('Could not get a policy explanation. Try again later.');
        }
      },
    });
  }

  cancel(): void {
    this.request?.unsubscribe();
  }
}
