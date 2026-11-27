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

type Replacement = { userId: string; email: string; token: string; expiresAt: string };

@Component({
  selector: 'kn-change-password-dialog',
  imports: [MatButtonModule, MatDialogModule, MatSnackBarModule, InlineErrorComponent],
  templateUrl: './change-password-dialog.component.html',
  styleUrls: ['../page-shared.css', './sign-out-devices-dialog.component.css'],
})
export class ChangePasswordDialogComponent {
  readonly dialog = inject<MatDialogRef<ChangePasswordDialogComponent>>(MatDialogRef);
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_BASE_URL);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  private readonly destroy = inject(DestroyRef);
  private readonly session = this.auth.token();
  private readonly current = viewChild<ElementRef<HTMLInputElement>>('current');
  private readonly next = viewChild<ElementRef<HTMLInputElement>>('next');
  private readonly confirmation = viewChild<ElementRef<HTMLInputElement>>('confirmation');
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
    const currentPassword = this.current()?.nativeElement.value ?? '';
    const newPassword = this.next()?.nativeElement.value ?? '';
    const confirmation = this.confirmation()?.nativeElement.value ?? '';
    this.clearPasswords();this.error.set('');
    if (!this.session || this.auth.token() !== this.session) return;
    if (!currentPassword || currentPassword.length > 32768 || newPassword.length > 32768
      || Array.from(newPassword).length < 15 || newPassword !== confirmation) {
      this.error.set('Enter your current password and matching new passwords of at least 15 characters.');return;
    }
    this.busy.set(true);this.dialog.disableClose = true;
    this.request = this.http.post<Replacement>(`${this.api}/auth/password`, { currentPassword, newPassword },
      { headers: this.auth.authHeaders() }).pipe(timeout(20000), takeUntilDestroyed(this.destroy)).subscribe({
        next: result => this.accept(result),
        error: response => {
          if (this.auth.token() !== this.session) return;
          if (response.status === 401) { this.leave('Your session ended. Sign in again.');return; }
          if ([400, 403, 413, 429].includes(response.status)
            || (response.status === 503 && response.error?.code === 'PASSWORD_OPERATIONS_BUSY')) {
            this.busy.set(false);this.dialog.disableClose = false;
            this.error.set(response.status === 403 ? 'Current password verification failed. Try again.'
              : response.status === 429 || response.status === 503 ? 'Password operations are busy. Wait before trying again.'
              : 'Enter your current password and matching, nonblank new passwords of at least 15 characters.');
            return;
          }
          this.uncertain();
        },
      });
  }

  @HostListener('document:visibilitychange')
  onVisibilityChange(): void {
    if (document.hidden) {
      if (this.busy()) this.uncertain();
      else this.clearPasswords();
    }
  }

  private accept(result: Replacement): void {
    if (this.auth.token() !== this.session) return;
    if (!result || typeof result.token !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(result.token)
      || result.token === this.session || typeof result.email !== 'string' || !result.email || result.email.length > 320
      || typeof result.userId !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(result.userId)
      || typeof result.expiresAt !== 'string' || !(Date.parse(result.expiresAt) > Date.now())) { this.uncertain();return; }
    this.busy.set(false);this.dialog.disableClose = false;
    this.request?.unsubscribe();this.request = undefined;
    try { this.auth.setSession(result.token, result.email); }
    catch {
      // setSession may update signals before browser storage fails; clear only this action's session.
      if (this.auth.token() === result.token) this.clearLocalSession();
      this.uncertain();return;
    }
    this.snack.open('Password changed. Other sessions have been signed out.', 'Dismiss', { duration: 5000, politeness: 'polite' });
    this.dialog.close();
  }

  private clearPasswords(): void {
    for (const field of [this.current(), this.next(), this.confirmation()]) if (field) field.nativeElement.value = '';
    this.visible.set(false);
  }
  private cancel(): void {
    if (this.busy() && this.auth.token() === this.session) this.uncertain(false);
    this.request?.unsubscribe();this.request = undefined;this.clearPasswords();this.busy.set(false);
  }
  private clearLocalSession(): void {
    try { this.auth.clearSession(); } catch { /* Signals clear first; storage may be unavailable. */ }
  }
  private uncertain(close = true): void {
    this.leave('Password change may have completed. Sign in again; try the new password first. It will not be retried automatically.', close);
  }
  private leave(message: string, close = true): void {
    this.busy.set(false);this.dialog.disableClose = false;this.clearPasswords();
    this.request?.unsubscribe();this.request = undefined;
    if (this.auth.token() === this.session || !this.auth.token()) {
      this.clearLocalSession();void this.router.navigateByUrl('/', { replaceUrl: true });
      this.snack.open(message, 'Dismiss', { duration: 10000, politeness: 'polite' });
    }
    if (close) this.dialog.close();
  }
}
