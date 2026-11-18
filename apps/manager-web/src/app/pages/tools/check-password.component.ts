import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { characterGroups, CharacterSelection, maxPasswordLength, minPasswordLength, validPasswordLength } from './password-options';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';
import { passwordViolationMessages } from '../password-violation-messages';

import { BuiltInPolicy } from './built-in-policy';

@Component({
  selector: 'kn-check-password-page',
  imports: [FormsModule, InlineErrorComponent, MatButtonModule, RouterLink],
  templateUrl: './check-password.component.html',
  styleUrl: '../page-shared.css',
})
export class CheckPasswordPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  readonly signedIn = inject(AuthService).signedIn;
  readonly mode = signal<'built-in' | 'custom'>('built-in');
  readonly policy = signal<BuiltInPolicy | null>(null);
  readonly policyError = signal('');
  readonly minLength = minPasswordLength;
  readonly maxLength = maxPasswordLength;
  readonly minimum = signal<number | null>(16);
  readonly required: CharacterSelection = { lower: true, upper: true, digits: true, symbols: true };
  readonly characterGroups = characterGroups;
  readonly validating = signal(false);
  readonly validationError = signal('');
  readonly validation = signal<{ valid: boolean; messages: string[]; policyId: string } | null>(null);

  constructor() {
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
    this.validation.set(null);
    this.validationError.set('');
  }

  validateSample(event: Event, input: HTMLInputElement): void {
    event.preventDefault();
    if (this.validating() || !input.value) return;

    const password = input.value;
    input.value = '';
    this.validation.set(null);
    this.validationError.set('');
    if (this.mode() === 'custom') {
      this.validateCustom(password);
      return;
    }
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

  private validateCustom(password: string): void {
    const minimum = this.minimum();
    if (!validPasswordLength(minimum)) {
      this.validationError.set('Choose a minimum length between 8 and 128.');
      return;
    }

    const characters = Array.from(password);
    const messages = characterGroups
      .filter(({ id, characters: group }) =>
        this.required[id] && !characters.some((character) => group.includes(character)))
      .map(({ label }) => `Missing character type: ${label.toLowerCase()}.`);
    if (characters.length < minimum) messages.unshift(`Use at least ${minimum} characters.`);
    this.validation.set({ valid: messages.length === 0, messages, policyId: 'custom' });
  }
}
