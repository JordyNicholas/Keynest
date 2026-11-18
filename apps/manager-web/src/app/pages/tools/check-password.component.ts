import { HttpClient } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';
import { BuiltInPolicy } from './built-in-policy';
import { characterGroups, CharacterGroup, CharacterSelection, maxPasswordLength, minPasswordLength, validPasswordLength } from './password-options';
import { checkCustomRules, confirmBuiltInPolicy, PolicyCheck, previewBuiltInPolicy } from './password-policy-checks';

@Component({
  selector: 'kn-check-password-page',
  imports: [FormsModule, InlineErrorComponent, MatButtonModule, RouterLink],
  templateUrl: './check-password.component.html',
  styleUrls: ['../page-shared.css', './check-password.component.css'],
})
export class CheckPasswordPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly assessment = signal<PolicyCheck[] | null>(null);

  readonly signedIn = inject(AuthService).signedIn;
  readonly mode = signal<'built-in' | 'custom'>('built-in');
  readonly policy = signal<BuiltInPolicy | null>(null);
  readonly policyError = signal('');
  readonly minLength = minPasswordLength;
  readonly maxLength = maxPasswordLength;
  readonly minimum = signal<number | null>(16);
  readonly required = signal<CharacterSelection>({ lower: true, upper: true, digits: true, symbols: true });
  readonly characterGroups = characterGroups;
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
    this.http.get<BuiltInPolicy>(`${this.apiBaseUrl}/tools/policy`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (policy) => this.policy.set(policy),
        error: () => this.policyError.set('Could not load the built-in rules. You can still use custom rules.'),
      });
  }

  selectMode(mode: 'built-in' | 'custom', input: HTMLInputElement): void {
    this.mode.set(mode);
    input.value = '';
    this.updatePreview(input);
  }

  changeMinimum(minimum: number | null, input: HTMLInputElement): void {
    this.minimum.set(minimum);
    this.updatePreview(input);
  }

  changeCategory(category: CharacterGroup, checked: boolean, input: HTMLInputElement): void {
    this.required.update((selection) => ({ ...selection, [category]: checked }));
    this.updatePreview(input);
  }

  updatePreview(input: HTMLInputElement): void {
    this.submitted.set(false);
    this.validationError.set('');
    this.assessment.set(input.value ? this.evaluate(input.value) : null);
  }

  validateSample(event: Event, input: HTMLInputElement): void {
    event.preventDefault();
    const policy = this.policy();
    if (this.mode() !== 'built-in' || this.validating() || !policy || !input.value) return;

    const password = input.value;
    input.value = '';
    this.validationError.set('');
    this.submitted.set(true);
    const checks = previewBuiltInPolicy(password, policy.rules);
    this.assessment.set(checks);
    this.validating.set(true);
    this.http.post<{ valid: boolean; violations: string[]; policyId: string }>(
      `${this.apiBaseUrl}/tools/validate?operation=update`, { password, policyId: policy.id },
    ).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.validating.set(false)))
      .subscribe({
        next: ({ valid, violations, policyId }) => {
          if (policyId === policy.id) {
            this.assessment.set(confirmBuiltInPolicy(checks, valid, violations));
          } else {
            this.validationError.set('The Manager API checked a different policy. Reload this page and try again.');
          }
        },
        error: () => this.validationError.set('Could not validate the sample password. Try again.'),
      });
  }

  private evaluate(sample: string): PolicyCheck[] | null {
    if (this.mode() === 'custom') {
      const minimum = this.minimum();
      return validPasswordLength(minimum) ? checkCustomRules(sample, minimum, this.required()) : null;
    }
    const rules = this.policy()?.rules;
    return rules ? previewBuiltInPolicy(sample, rules) : null;
  }
}
