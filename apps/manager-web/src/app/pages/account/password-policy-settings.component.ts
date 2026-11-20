import { Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { API_BASE_URL } from '../../injectionTokens';
import { AuthService } from '../../auth/auth.service';
import { BuiltInPolicy } from '../tools/built-in-policy';
import { InlineErrorComponent } from '../inline-error.component';

@Component({
  selector: 'kn-password-policy-settings',
  imports: [FormsModule, MatButtonModule, InlineErrorComponent],
  styleUrl: '../page-shared.css',
  template: `
    <kn-inline-error [message]="error()" />
    @if (rules; as rules) {
      <form ngNativeValidate (ngSubmit)="save()">
        <p>These rules apply to generation, checking, new Vault passwords and rotation.</p>
        <label>Password policy
          <select name="mode" [(ngModel)]="mode">
            <option value="default">Keynest default</option>
            <option value="custom">Custom account rules</option>
          </select>
        </label>
        @if (mode === 'custom') {
          <label>Minimum length
            <input type="number" name="minLength" min="8" [max]="rules.maxLength" required [(ngModel)]="rules.minLength" />
          </label>
          <label>Maximum length
            <input type="number" name="maxLength" [min]="rules.minLength" max="128" required [(ngModel)]="rules.maxLength" />
          </label>
          <fieldset><legend>Required characters</legend>
            @for (option of options; track option.key) {
              <label class="checkbox"><input type="checkbox" [name]="option.key" [(ngModel)]="rules[option.key]" />{{ option.label }}</label>
            }
          </fieldset>
          <label>Minimum different character types
            <input type="number" name="categories" min="1" max="4" required [(ngModel)]="rules.minCategories" />
          </label>
          <p>Types are lowercase letters, uppercase letters, numbers and symbols. Required types also count.</p>
          <label class="checkbox"><input type="checkbox" name="ambiguous" [(ngModel)]="rules.excludeAmbiguous" />Exclude 0, O, I, l and 1 because they can look alike</label>
          <label class="checkbox"><input type="checkbox" name="whitespace" [(ngModel)]="rules.forbidWhitespace" />Exclude spaces and other whitespace</label>
          <label class="checkbox"><input type="checkbox" name="username" [(ngModel)]="rules.forbidUsernameSubstring" />Exclude the saved username or email name (three or more characters)</label>
          <label>Blocked strings, separated by commas
            <input type="text" name="blocked" maxlength="1300" [(ngModel)]="blocked" />
          </label>
          <p>Matching ignores letter case. Leave empty to allow all strings.</p>
          <label class="checkbox"><input type="checkbox" name="repetitions" [(ngModel)]="repetitions" />Reject four or more identical characters in a row</label>
        }
        <button mat-flat-button type="submit" [disabled]="saving()">Save password policy</button>
        <p role="status">{{ status() }}</p>
      </form>
    }
  `,
})
export class PasswordPolicySettingsComponent {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly url = `${inject(API_BASE_URL)}/account/password-policy`;
  readonly error = signal('');
  readonly status = signal('');
  readonly saving = signal(false);
  readonly options = [
    { key: 'requireLower', label: 'Lowercase letters' }, { key: 'requireUpper', label: 'Uppercase letters' },
    { key: 'requireDigit', label: 'Numbers' }, { key: 'requireSymbol', label: 'Symbols' },
  ] as const;
  rules: BuiltInPolicy['rules'] | null = null;
  mode = 'default';
  blocked = '';
  repetitions = false;

  constructor() {
    this.http.get<BuiltInPolicy>(this.url, { headers: this.auth.authHeaders() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (policy) => this.accept(policy),
        error: () => this.error.set('Could not load your password policy. Close this section and try again.'),
      });
  }

  save(): void {
    if (!this.rules || this.saving()) return;
    this.error.set('');
    this.status.set('');
    this.saving.set(true);
    const rules = { ...this.rules, bannedSubstrings: this.blocked.split(',').map(word => word.trim()).filter(Boolean),
      bannedRegexes: this.repetitions ? ['(.)\\1{3,}'] : [] };
    this.http.put<BuiltInPolicy>(this.url, { mode: this.mode, rules }, { headers: this.auth.authHeaders() })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(false)))
      .subscribe({
        next: (policy) => { this.accept(policy); this.status.set('Password policy saved. Reload any open password forms.'); },
        error: ({ status }) => this.error.set(status === 401 ? 'Your session expired. Sign in again.'
          : 'Could not save the policy. Use lengths from 8 to 128 and at most 20 blocked strings of 64 characters each.'),
      });
  }

  private accept(policy: BuiltInPolicy): void {
    this.rules = { ...policy.rules };
    this.mode = policy.id === 'keynest.default.v1' ? 'default' : 'custom';
    this.blocked = policy.rules.bannedSubstrings.join(', ');
    this.repetitions = Boolean(policy.rules.bannedRegexes?.length);
  }
}
