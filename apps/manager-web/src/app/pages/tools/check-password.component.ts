import { httpErrorMessage } from '../http-error-message';
import { HttpClient } from '@angular/common/http';
import { Component, computed, DestroyRef, ElementRef, inject, input, OnDestroy, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';
import { BuiltInPolicy } from './built-in-policy';
import { confirmBuiltInPolicy, PolicyCheck, previewBuiltInPolicy } from './password-policy-checks';

@Component({
  selector: 'kn-check-password-page',
  imports: [InlineErrorComponent, MatButtonModule, RouterLink],
  templateUrl: './check-password.component.html',
  styleUrls: ['../page-shared.css', './check-password.component.css'],
})
export class CheckPasswordPageComponent implements OnDestroy {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly assessment = signal<PolicyCheck[] | null>(null);

  private readonly auth = inject(AuthService);
  readonly signedIn = this.auth.signedIn;
  private readonly navigationInfo = inject(Router).currentNavigation()?.extras.info as { policyExplanation?: unknown } | undefined;
  readonly explanation = input(typeof this.navigationInfo?.policyExplanation === 'string' ? this.navigationInfo.policyExplanation : '');
  private readonly sample = viewChild<ElementRef<HTMLInputElement>>('sample');
  readonly policy = signal<BuiltInPolicy | null>(null);
  readonly policyError = signal('');
  readonly validating = signal(false);
  readonly validationError = signal('');
  readonly submitted = signal(false);

  readonly rules = computed(() => this.assessment() ?? this.evaluate(''));
  readonly meter = computed(() => {
    if (this.validating()) return { tone: 'partial', label: 'Checking policy…' };
    const checks = this.assessment();
    if (!checks) return { tone: 'neutral', label: 'Enter a sample password' };
    if (checks[0].met === false) return { tone: 'weak', label: 'Outside the allowed length' };
    if (checks.some(({ met }) => met === false)) return { tone: 'partial', label: 'Some rules unmet' };
    if (checks.some(({ met }) => met === null)) {
      return { tone: 'partial', label: this.submitted() ? 'Final check incomplete' : 'Ready for final check' };
    }
    return { tone: 'strong', label: 'Meets policy' };
  });

  constructor() {
    this.loadPolicy();
  }

  loadPolicy(): void {
    this.policyError.set('');
    this.http.get<BuiltInPolicy>(`${this.apiBaseUrl}/tools/policy`, {
      headers: this.auth.authHeaders(),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (policy) => this.policy.set(policy),
        error: ({ status }) => this.policyError.set(httpErrorMessage(status, 'load the selected password rules')),
      });
  }

  updatePreview(input: HTMLInputElement): void {
    this.submitted.set(false);
    this.validationError.set('');
    this.assessment.set(input.value ? this.evaluate(input.value) : null);
  }

  validateSample(event: Event, input: HTMLInputElement): void {
    event.preventDefault();
    const policy = this.policy();
    if (this.validating() || !policy || !input.value) return;

    const password = input.value;
    input.value = '';
    this.validationError.set('');
    this.submitted.set(true);
    const checks = previewBuiltInPolicy(password, policy.rules);
    this.assessment.set(checks);
    this.validating.set(true);
    this.http.post<{ valid: boolean; violations: string[]; policyId: string }>(
      `${this.apiBaseUrl}/tools/validate?operation=update`, { password, policyId: policy.id },
      { headers: this.auth.authHeaders() },
    ).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.validating.set(false)))
      .subscribe({
        next: ({ valid, violations, policyId }) => {
          if (policyId === policy.id) {
            this.assessment.set(confirmBuiltInPolicy(checks, valid, violations));
          } else {
            this.validationError.set('The Manager API checked a different policy. Reload this page and try again.');
          }
        },
        error: ({ status }) => this.validationError.set(httpErrorMessage(status, 'check this sample password')),
      });
  }

  ngOnDestroy(): void {
    const sample = this.sample();
    if (sample) sample.nativeElement.value = '';
    this.assessment.set(null);
  }

  private evaluate(sample: string): PolicyCheck[] | null {
    const rules = this.policy()?.rules;
    return rules ? previewBuiltInPolicy(sample, rules) : null;
  }
}
