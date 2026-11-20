import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { API_BASE_URL } from '../../injectionTokens';
import { BuiltInPolicy, generatorOptionsForPolicy } from './built-in-policy';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../auth/auth.service';
import { InlineErrorComponent } from '../inline-error.component';
import { PasswordGeneratorOptionsComponent } from './password-generator-options.component';

@Component({
  selector: 'kn-generate-password-page',
  imports: [InlineErrorComponent, PasswordGeneratorOptionsComponent, MatButtonModule, RouterLink],
  templateUrl: './generate-password.component.html',
  styleUrl: '../page-shared.css',
})
export class GeneratePasswordPageComponent {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly destroyRef = inject(DestroyRef);
  readonly policy = signal<BuiltInPolicy | null>(null);
  readonly rules = computed(() => {
    const policy = this.policy();
    return policy ? generatorOptionsForPolicy(policy.rules) : null;
  });
  private readonly auth = inject(AuthService);
  readonly signedIn = this.auth.signedIn;
  readonly copyStatus = signal('');
  readonly generated = signal('');
  readonly error = signal('');

  constructor() {
    this.loadPolicy();
  }

  loadPolicy(): void {
    this.error.set('');
    this.http.get<BuiltInPolicy>(`${this.apiBaseUrl}/tools/policy`, {
      headers: this.auth.signedIn() ? this.auth.authHeaders() : undefined,
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (policy) => this.policy.set(policy),
        error: () => this.error.set('Could not load the password rules. Check the API connection and retry.'),
      });
  }

  generate(event: Event, options: PasswordGeneratorOptionsComponent): void {
    event.preventDefault();
    this.clearGenerated();
    this.error.set('');
    try {
      this.generated.set(options.generate());
    } catch (failure) {
      this.error.set(failure instanceof Error ? failure.message : 'Could not generate a password.');
    }
  }

  async copyGenerated(): Promise<void> {
    const password = this.generated();
    if (!password) return;
    try {
      await navigator.clipboard.writeText(password);
      if (this.generated() === password) this.copyStatus.set('Password copied.');
    } catch {
      if (this.generated() === password) this.copyStatus.set('Copy unavailable. Select and copy the password manually.');
    }
  }

  clearGenerated(): void {
    this.generated.set('');
    this.copyStatus.set('');
  }
}
