import { Component, DestroyRef, ElementRef, HostListener, effect, inject, signal, viewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, timeout } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';

@Component({
  selector: 'kn-sign-out-devices-dialog',
  imports: [MatButtonModule, MatDialogModule, MatSnackBarModule, InlineErrorComponent],
  templateUrl: './sign-out-devices-dialog.component.html',
  styleUrls: ['../page-shared.css', './sign-out-devices-dialog.component.css'],
})
export class SignOutDevicesDialogComponent {
  readonly dialog = inject<MatDialogRef<SignOutDevicesDialogComponent>>(MatDialogRef);
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_BASE_URL);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  private readonly destroy = inject(DestroyRef);
  private readonly session = this.auth.token();
  private readonly password = viewChild<ElementRef<HTMLInputElement>>('password');
  private request?: Subscription;
  readonly busy = signal(false);
  readonly visible = signal(false);
  readonly error = signal('');

  constructor() {
    this.dialog.beforeClosed().pipe(takeUntilDestroyed()).subscribe(() => this.cancel());
    this.destroy.onDestroy(() => this.cancel());
    effect(() => { if (this.auth.token() !== this.session) { this.cancel();this.dialog.close(); } });
  }

  submit(event: Event): void {
    event.preventDefault();
    if (this.busy()) return;
    const password = this.password()?.nativeElement.value ?? '';
    this.clearPassword();this.error.set('');
    if (!this.session || this.auth.token() !== this.session) return;
    if (!password || password.length > 32768) { this.error.set('Enter your current account password.');return; }
    this.busy.set(true);this.dialog.disableClose = true;
    this.request = this.http.post<{ status: string }>(`${this.api}/auth/logout-all`, { accountPassword: password },
      { headers: this.auth.authHeaders() }).pipe(timeout(15000), takeUntilDestroyed(this.destroy)).subscribe({
        next: result => this.finish(result?.status === 'ok' ? 'success' : 'uncertain'),
        error: response => {
          if (this.auth.token() !== this.session) return;
          if (response.status === 401) { this.finish('expired');return; }
          if ([400, 403, 413, 429].includes(response.status)
            || (response.status === 503 && response.error?.code === 'PASSWORD_OPERATIONS_BUSY')) {
            this.busy.set(false);this.dialog.disableClose = false;
            this.error.set(response.status === 403 ? 'Account password verification failed. Try again.'
              : response.status === 429 || response.status === 503 ? 'Password operations are busy. Wait before trying again.'
              : 'Enter your current account password and try again.');
            return;
          }
          this.finish('uncertain');
        },
      });
  }

  @HostListener('document:visibilitychange')
  onVisibilityChange(): void {
    if (document.hidden) {
      if (this.busy()) this.finish('uncertain');
      else this.clearPassword();
    }
  }

  private clearPassword(): void {
    const field = this.password();
    if (field) field.nativeElement.value = '';
    this.visible.set(false);
  }

  private cancel(): void {
    if (this.busy() && this.auth.token() === this.session) this.finish('uncertain', false);
    this.request?.unsubscribe();this.request = undefined;this.clearPassword();this.busy.set(false);
  }

  private finish(result: 'success' | 'uncertain' | 'expired', close = true): void {
    this.busy.set(false);this.dialog.disableClose = false;this.clearPassword();
    this.request?.unsubscribe();this.request = undefined;
    if (this.auth.token() === this.session) {
      this.auth.clearSession();
      void this.router.navigateByUrl('/', { replaceUrl: true });
      this.snack.open(result === 'success' ? 'Signed out on all devices.'
        : result === 'expired' ? 'Your session ended. Sign in again.'
        : 'Sign-out may have completed. Sign in again; it will not be retried automatically.',
      'Dismiss', { duration: result === 'uncertain' ? 10000 : 5000, politeness: 'polite' });
    }
    if (close) this.dialog.close();
  }
}
