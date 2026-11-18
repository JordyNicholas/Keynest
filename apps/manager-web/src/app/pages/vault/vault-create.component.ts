import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';
import { passwordViolationMessages } from '../password-violation-messages';

@Component({
  selector: 'kn-vault-create-page',
  imports: [FormsModule, InlineErrorComponent, MatButtonModule, RouterLink],
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

  readonly label = signal('');
  readonly username = signal('');
  readonly url = signal('');
  readonly expiryDays = signal<number | null>(90);
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

    const generate = this.mode() === 'generate';
    const password = generate ? undefined : passwordInput.value;
    if (!generate && !password?.trim()) {
      this.error.set('Enter an existing password or choose Generate on save.');
      return;
    }
    passwordInput.value = '';
    this.saving.set(true);
    this.http.post(`${this.apiBaseUrl}/vault/items`, {
      label,
      username: this.username(),
      url: this.url(),
      expiresInDays: this.expiryDays(),
      notifyBeforeDays: this.noticeDays(),
      generate,
      password,
    }, { headers: this.auth.authHeaders() })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          if (this.auth.token() === this.session) void this.router.navigateByUrl('/vault');
        },
        error: ({ error }) => {
          if (this.auth.token() !== this.session) return;
          if (error?.code === 'VAULT_PASSWORD_POLICY') {
            this.details.set(passwordViolationMessages(error.violations));
            this.error.set('The password does not meet the vault policy.');
          } else {
            this.error.set('Could not create the vault item. Try again.');
          }
        },
      });
  }
}
