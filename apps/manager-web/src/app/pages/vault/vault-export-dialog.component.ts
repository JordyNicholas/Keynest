import { Component, DestroyRef, ElementRef, HostListener, effect, inject, signal, viewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { Subscription } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';

type Preview = { destination: 'keynest'; encrypted: true; version: 1; itemCount: number; snapshot: string; asOf: string };

@Component({
  selector: 'kn-vault-export-dialog',
  imports: [MatDialogModule, MatButtonModule, InlineErrorComponent],
  templateUrl: './vault-export-dialog.component.html',
  styleUrls: ['../page-shared.css', './vault-export-dialog.component.css'],
})
export class VaultExportDialogComponent {
  readonly dialog = inject<MatDialogRef<VaultExportDialogComponent>>(MatDialogRef);
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_BASE_URL);
  private readonly destroyRef = inject(DestroyRef);
  private readonly session = this.auth.token();
  readonly ids = Object.freeze([...inject<{ ids: string[] }>(MAT_DIALOG_DATA).ids]);
  readonly scope = signal<'all' | 'selected'>(this.ids.length ? 'selected' : 'all');
  readonly preview = signal<Preview | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly status = signal('');
  private request?: Subscription;
  private version = 0;
  private objectUrl?: string;
  private revokeTimer?: ReturnType<typeof setTimeout>;
  private readonly account = viewChild<ElementRef<HTMLInputElement>>('account');
  private readonly phrase = viewChild<ElementRef<HTMLInputElement>>('phrase');
  private readonly confirmation = viewChild<ElementRef<HTMLInputElement>>('confirmation');

  constructor() {
    this.dialog.beforeClosed().pipe(takeUntilDestroyed()).subscribe(() => this.cancel());
    this.destroyRef.onDestroy(() => { this.cancel();this.revoke(); });
    effect(() => { if (this.auth.token() !== this.session) { this.cancel();this.dialog.close(); } });
  }

  changeScope(value: string): void {
    if (value !== 'all' && value !== 'selected') return;
    this.cancel();this.scope.set(value);this.error.set('');this.status.set('');
  }

  review(): void {
    this.cancel();this.error.set('');this.status.set('');
    if (!this.session || this.auth.token() !== this.session || (this.scope() === 'selected' && !this.ids.length)) return;
    const version = this.version;this.busy.set(true);
    this.request = this.http.post<Preview>(`${this.api}/vault/exports/preview`, this.selection(), { headers: this.auth.authHeaders() })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: preview => {
          if (!this.current(version)) return;
          this.busy.set(false);
          if (preview.destination !== 'keynest' || preview.encrypted !== true || preview.version !== 1
            || !Number.isInteger(preview.itemCount) || preview.itemCount < 1 || preview.itemCount > 500
            || !/^[0-9a-f]{64}$/.test(preview.snapshot)) { this.error.set('Export review is unavailable. Try again.');return; }
          this.preview.set(preview);
        },
        error: response => { if (this.current(version)) this.fail(response.status); },
      });
  }

  download(event: Event): void {
    event.preventDefault();
    const preview = this.preview();
    const accountPassword = this.account()?.nativeElement.value ?? '';
    const backupPassphrase = this.phrase()?.nativeElement.value ?? '';
    const confirmation = this.confirmation()?.nativeElement.value ?? '';
    this.clearCredentials();this.error.set('');this.status.set('');
    if (!preview || this.busy() || this.auth.token() !== this.session) return;
    if (!accountPassword || accountPassword.length > 32768 || backupPassphrase.length > 1024
      || Array.from(backupPassphrase).length < 15 || !backupPassphrase.trim() || backupPassphrase !== confirmation) {
      this.error.set('Enter your account password and matching export passphrases of at least 15 characters.');return;
    }
    const version = this.version;this.busy.set(true);
    this.request = this.http.post(`${this.api}/vault/exports/download`, {
      ...this.selection(), snapshot: preview.snapshot, accountPassword, backupPassphrase,
    }, { headers: this.auth.authHeaders(), responseType: 'blob' }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: file => {
        if (!this.current(version)) return;
        this.busy.set(false);this.preview.set(null);
        if (file.type !== 'application/octet-stream' || !file.size || file.size > 8 * 1024 * 1024 + 128) {
          this.error.set('The encrypted file was unavailable. Review again before retrying.');return;
        }
        try {
          this.revoke();this.objectUrl = URL.createObjectURL(file);
          const link = document.createElement('a');link.href = this.objectUrl;link.download = 'keynest-vault.keynest';
          document.body.append(link);
          try { link.click(); } finally { link.remove(); }
          this.revokeTimer = setTimeout(() => this.revoke(), 1000);
          this.status.set('Encrypted file download started. Check your browser downloads and keep the passphrase separately.');
        } catch { this.revoke();this.error.set('Your browser could not start the download. Review again to retry.'); }
      },
      error: response => { if (this.current(version)) this.fail(response.status); },
    });
  }

  cancel(): void {
    ++this.version;this.request?.unsubscribe();this.request = undefined;
    this.busy.set(false);this.preview.set(null);this.clearCredentials();
  }

  @HostListener('document:visibilitychange')
  onVisibilityChange(): void { if (document.hidden) { this.cancel();this.status.set('Review again to continue after leaving this tab.'); } }

  private selection() { return { destination: 'keynest', scope: this.scope(), ...(this.scope() === 'selected' ? { itemIds: [...this.ids] } : {}) }; }
  private current(version: number): boolean { return version === this.version && this.auth.token() === this.session && !this.destroyRef.destroyed; }
  private clearCredentials(): void {
    for (const field of [this.account(), this.phrase(), this.confirmation()]) if (field) field.nativeElement.value = '';
  }
  private revoke(): void { clearTimeout(this.revokeTimer);if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);this.objectUrl = undefined; }
  private fail(status: number): void {
    this.cancel();
    if (status === 401) { this.auth.clearSession();this.dialog.close();return; }
    this.error.set(status === 409 ? 'Your Vault or policy changed. Review the selection again.'
      : status === 403 ? 'Account password verification failed. Review and try again.'
      : status === 404 ? 'Some selected items are unavailable. Close and update the selection.'
      : status === 413 ? 'Export supports at most 500 items. Choose a smaller selection.'
      : status === 422 ? 'There are no items in this export.'
      : status === 429 || status === 503 ? 'Export is busy or unavailable. Wait and review again.'
      : 'Export could not finish. Review again before retrying.');
  }
}
