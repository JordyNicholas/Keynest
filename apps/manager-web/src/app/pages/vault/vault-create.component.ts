import { ExpiryDatePickerComponent } from '../../ui/expiry-date-picker.component';
import { expiryInstant, localDateAfterDays } from '../../ui/expiry-date';
import { httpErrorMessage } from '../http-error-message';
import { HttpClient } from '@angular/common/http';
import { Component, computed, DestroyRef, effect, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';
import { passwordViolationMessages } from '../password-violation-messages';
import { BuiltInPolicy, generatorOptionsForPolicy } from '../tools/built-in-policy';
import { PasswordGeneratorOptionsComponent } from '../tools/password-generator-options.component';

@Component({
  selector: 'kn-vault-create-page',
  imports: [ExpiryDatePickerComponent, FormsModule, InlineErrorComponent, MatButtonModule, PasswordGeneratorOptionsComponent, RouterLink],
  templateUrl: './vault-create.component.html',
  styleUrls: ['../page-shared.css', './vault-create.component.css'],
})
export class VaultCreatePageComponent {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly session = this.auth.token();
  private readonly options = viewChild(PasswordGeneratorOptionsComponent);
  private readonly policy = signal<BuiltInPolicy | null>(null);

  readonly loadingPolicy = signal(false);
  readonly policyRules = computed(() => {
    const rules = this.policy()?.rules;
    return rules ? generatorOptionsForPolicy(rules) : null;
  });

  readonly label = signal('');
  readonly username = signal('');
  readonly url = signal('');
  readonly expiryDate = signal<Date | null>(localDateAfterDays(90));
  private readonly expiryPicker = viewChild(ExpiryDatePickerComponent);
  readonly noticeDays = signal<number | null>(14);
  readonly mode = signal<'existing' | 'generate'>('existing');
  readonly saving = signal(false);
  readonly error = signal('');
  readonly details = signal<string[]>([]);

  constructor() {
    effect(() => {
      if (this.auth.token() !== this.session) {
        void this.router.navigateByUrl('/account');
      }
    });
  }

  selectMode(mode: 'existing' | 'generate', passwordInput: HTMLInputElement): void {
    this.mode.set(mode);
    passwordInput.value = '';
    this.error.set('');
    this.details.set([]);
    if (mode === 'generate') this.loadPolicy();
  }

  loadPolicy(): void {
    if (this.policy() || this.loadingPolicy()) return;
    this.loadingPolicy.set(true);
    this.http.get<BuiltInPolicy>(`${this.apiBaseUrl}/tools/policy`, { headers: this.auth.authHeaders() })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loadingPolicy.set(false)))
      .subscribe({ next: (policy) => this.policy.set(policy), error: () => this.policy.set(null) });
  }

  save(event: Event, passwordInput: HTMLInputElement): void {
    event.preventDefault();
    if (this.saving() || !this.session || this.auth.token() !== this.session) return;
    this.error.set('');
    this.details.set([]);
    const label = this.label().trim();
    if (!label) {
      this.error.set('Enter a label for the vault item.');
      return;
    }

    if (this.expiryPicker()?.valid() === false) { this.error.set('Choose a valid expiry date.');return; }

    let password = passwordInput.value;
    if (this.mode() === 'generate') {
      const options = this.options();
      if (!options) {
        this.error.set('Load the Vault policy before generating a password.');
        return;
      }
      try {
        password = options.generate();
      } catch (failure) {
        this.error.set(failure instanceof Error ? failure.message : 'Could not generate a password.');
        return;
      }
    }
    if (!password.trim()) {
      this.error.set('Enter an existing password or choose Generate on save.');
      return;
    }
    passwordInput.value = '';
    this.saving.set(true);
    this.http.post(`${this.apiBaseUrl}/vault/items`, {
      label,
      username: this.username(),
      url: this.url(),
      expiresAt: expiryInstant(this.expiryDate()),
      notifyBeforeDays: this.noticeDays(),
      generate: false,
      policyId: this.policy()?.id,
      password,
    }, { headers: this.auth.authHeaders() })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          if (this.auth.token() === this.session) void this.router.navigateByUrl('/vault');
        },
        error: ({ error, status }) => {
          if (this.auth.token() !== this.session) return;
          if (status === 409 && error?.code === 'VAULT_ITEM_DUPLICATE') {
            this.error.set('This login is already saved in your Vault.');
          } else if (error?.code === 'VAULT_PASSWORD_POLICY') {
            this.details.set(passwordViolationMessages(error.violations));
            this.error.set('The password does not meet the vault policy.');
          } else {
            this.error.set(httpErrorMessage(status, 'create this Vault item'));
          }
        },
      });
  }
}
