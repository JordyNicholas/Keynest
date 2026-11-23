import { Component, DestroyRef, effect, inject, model, output, signal, untracked } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { Subscription } from 'rxjs';
import { API_BASE_URL } from '../injectionTokens';
import { InlineErrorComponent } from '../pages/inline-error.component';
import { AuthService } from './auth.service';

export type AuthMode = 'signin' | 'signup';
type AuthResponse = { userId: string; email: string; token: string; expiresAt: string };

@Component({
  selector: 'kn-auth-form',
  imports: [FormsModule, MatButtonModule, InlineErrorComponent],
  templateUrl: './auth-form.component.html',
  styleUrls: ['../pages/page-shared.css', './auth-form.component.css'],
})
export class AuthFormComponent {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_BASE_URL);
  private readonly auth = inject(AuthService);
  private request?: Subscription;
  private attempt = 0;
  private requestToken = '';
  readonly mode = model<AuthMode>('signin');
  readonly authenticated = output<void>();
  readonly minimumMasterPasswordLength = 15;
  readonly email = signal('');
  readonly password = signal('');
  readonly pending = signal(false);
  readonly error = signal('');

  constructor() {
    effect(() => {
      this.mode();
      untracked(() => this.resetAttempt());
    });
    effect(() => {
      const token = this.auth.token();
      if (this.pending() && token !== this.requestToken) untracked(() => this.cancel());
    });
    inject(DestroyRef).onDestroy(() => this.cancel());
  }

  submit(): void {
    if (this.pending()) return;
    this.error.set('');
    const emailInput = document.createElement('input');
    emailInput.type = 'email';emailInput.required = true;emailInput.value = this.email().trim();
    if (!emailInput.checkValidity()) {
      this.error.set('Enter a valid email address, such as name@example.com.');return;
    }
    if (this.mode() === 'signup' && [...this.password()].length < this.minimumMasterPasswordLength) {
      this.error.set(`Your Keynest password must contain at least ${this.minimumMasterPasswordLength} characters to create an account.`);return;
    }
    if (!this.password()) {
      this.error.set('Enter your Keynest password.');return;
    }
    const mode = this.mode();
    const attempt = ++this.attempt;
    this.requestToken = this.auth.token();
    this.pending.set(true);
    this.request = this.http.post<AuthResponse>(`${this.api}/auth/${mode === 'signup' ? 'register' : 'login'}`, {
      email: this.email().trim(), password: this.password(),
    }).subscribe({
      next: (body) => {
        if (attempt !== this.attempt || this.auth.token() !== this.requestToken) return;
        this.pending.set(false);this.password.set('');this.error.set('');
        this.auth.setSession(body.token, body.email);
        this.authenticated.emit();
      },
      error: ({ status }) => {
        if (attempt !== this.attempt || this.auth.token() !== this.requestToken) return;
        this.pending.set(false);
        this.error.set(status === 401 ? 'The email address or Keynest password is incorrect.'
          : status === 409 && mode === 'signup' ? 'An account with this email already exists. Try signing in.'
          : status === 400 ? 'Check the email address and Keynest password requirements.'
          : status === 429 ? 'Too many attempts. Wait a minute and try again.'
          : status === 503 ? 'Password operations are busy. Wait a moment and try again.'
          : 'Could not connect to Manager. Check API health and try again.');
      },
    });
  }

  switchMode(): void {
    this.resetAttempt();
    this.mode.set(this.mode() === 'signin' ? 'signup' : 'signin');
  }

  cancel(): void {
    this.resetAttempt();this.email.set('');
  }

  private resetAttempt(): void {
    ++this.attempt;this.request?.unsubscribe();this.request = undefined;
    this.pending.set(false);this.password.set('');this.error.set('');
  }
}
