import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, HostListener, effect, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Subscription } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';

type Preview = { planId: string; expiresAt: string; itemCount: number; receivingPolicyUnchanged: true };
export type RestoreResult = { restoredCount: number; receivingPolicyUnchanged: true };
// ADR-0012 v1 framing plus the existing 8 MiB native payload bound.
const MAX_FILE_BYTES = 8 * 1024 * 1024 + 79;
const UNCERTAIN = 'The restore may have finished. Close this dialog and refresh your Vault before starting another restore. This request will not be retried automatically.';

@Component({
  selector: 'kn-vault-restore-dialog',
  imports: [DatePipe, MatDialogModule, MatButtonModule, InlineErrorComponent],
  templateUrl: './vault-restore-dialog.component.html',
  styleUrls: ['../page-shared.css', './vault-restore-dialog.component.css'],
})
export class VaultRestoreDialogComponent {
  readonly dialog = inject<MatDialogRef<VaultRestoreDialogComponent, RestoreResult>>(MatDialogRef);
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_BASE_URL);
  private readonly destroy = inject(DestroyRef);
  private readonly session = this.auth.token();
  private readonly picker = viewChild<ElementRef<HTMLInputElement>>('picker');
  private readonly phrase = viewChild<ElementRef<HTMLInputElement>>('phrase');
  private readonly account = viewChild<ElementRef<HTMLInputElement>>('account');
  private file?: File;
  private reader?: FileReader;
  private request?: Subscription;
  private expiry?: ReturnType<typeof setTimeout>;
  private version = 0;
  readonly filename = signal('');
  readonly preview = signal<Preview | null>(null);
  readonly busy = signal(false);
  readonly applying = signal(false);
  readonly blocked = signal(false);
  readonly error = signal('');
  readonly status = signal('');

  constructor() {
    this.dialog.beforeClosed().pipe(takeUntilDestroyed()).subscribe(() => this.reset());
    this.destroy.onDestroy(() => this.reset());
    effect(() => { if (this.auth.token() !== this.session) { this.reset();this.dialog.close(); } });
  }

  choose(event: Event): void {
    if (this.applying()) { this.uncertain();return; }
    const input = event.target as HTMLInputElement, file = input.files?.[0];
    this.reset();this.error.set('');this.status.set('');
    if (this.blocked() || !file) return;
    if (file.size <= 79 || file.size > MAX_FILE_BYTES) { this.error.set('Choose a Keynest encrypted file containing up to 8 MiB of Vault data.');return; }
    this.file = file;this.filename.set(file.name);
  }

  invalidateReview(): void {
    if (this.applying()) { this.uncertain();return; }
    this.stop();this.preview.set(null);this.clearAccount();this.error.set('');this.status.set('');
  }

  review(event: Event): void {
    event.preventDefault();if (this.busy() || this.blocked() || !this.current(this.version)) return;
    this.invalidateReview();
    const backupPassphrase = this.phrase()?.nativeElement.value ?? '';
    if (!this.file || !this.validPhrase(backupPassphrase)) { this.error.set('Choose your encrypted file and enter its passphrase of at least 15 characters.');return; }
    const version = this.version;this.busy.set(true);
    this.read(version, file => {
      this.request = this.http.post<Preview>(`${this.api}/vault/restores/preview`, { file, backupPassphrase }, { headers: this.auth.authHeaders() })
        .pipe(takeUntilDestroyed(this.destroy)).subscribe({
          next: preview => {
            if (!this.current(version)) return;
            const remaining = Date.parse(preview?.expiresAt) - Date.now();
            if (!preview || !/^[A-Za-z0-9_-]{43}$/.test(preview.planId) || !Number.isInteger(preview.itemCount)
              || preview.itemCount < 1 || preview.itemCount > 500 || preview.receivingPolicyUnchanged !== true
              || !Number.isFinite(remaining) || remaining <= 0 || remaining > 310_000) { this.refuse('Restore review is unavailable. Choose your file and review again.');return; }
            this.busy.set(false);this.preview.set({ planId: preview.planId, expiresAt: preview.expiresAt, itemCount: preview.itemCount, receivingPolicyUnchanged: true });
            this.expiry = setTimeout(() => { this.reset();this.status.set('This review expired. Choose your file and review again.'); }, Math.min(300_000, remaining));
          },
          error: response => { if (this.current(version)) this.fail(response, false); },
        });
    });
  }

  apply(event: Event): void {
    event.preventDefault();const preview = this.preview();
    if (this.busy() || this.blocked() || !preview || !this.current(this.version)) return;
    if (Date.parse(preview.expiresAt) <= Date.now()) { this.refuse('This review expired. Choose your file and review again.');return; }
    const accountPassword = this.account()?.nativeElement.value ?? '', backupPassphrase = this.phrase()?.nativeElement.value ?? '';
    this.clearCredentials();this.error.set('');this.status.set('');
    if (!accountPassword || accountPassword.length > 32768 || !this.validPhrase(backupPassphrase)) {
      this.refuse('Enter your current account password and review the file again.');return;
    }
    clearTimeout(this.expiry);const version = this.version;this.busy.set(true);
    this.read(version, file => {
      if (Date.parse(preview.expiresAt) <= Date.now()) { this.refuse('This review expired. Choose your file and review again.');return; }
      this.applying.set(true);
      this.request = this.http.post<RestoreResult>(`${this.api}/vault/restores/apply`, { file, backupPassphrase, accountPassword, planId: preview.planId }, { headers: this.auth.authHeaders() })
        .pipe(takeUntilDestroyed(this.destroy)).subscribe({
          next: result => {
            if (!this.current(version)) return;
            if (!result || result.restoredCount !== preview.itemCount || result.receivingPolicyUnchanged !== true) { this.uncertain();return; }
            this.reset();this.dialog.close({ restoredCount: result.restoredCount, receivingPolicyUnchanged: true });
          },
          error: response => { if (this.current(version)) this.fail(response, true); },
        });
    });
  }

  private read(version: number, next: (file: string) => void): void {
    if (!this.file) { this.refuse('Choose your encrypted file and review again.');return; }
    const reader = new FileReader();this.reader = reader;
    reader.onload = () => {
      const bytes = reader.result instanceof ArrayBuffer ? new Uint8Array(reader.result) : null;
      try {
        if (!this.current(version)) return;
        // Reject ordinary/plaintext files locally. Authentication/schema validation remains on the server.
        if (!bytes || bytes.length <= 79 || bytes.length > MAX_FILE_BYTES || bytes.length !== this.file?.size
          || ![75, 69, 89, 78, 69, 83, 84, 66, 1, 1].every((byte, index) => bytes[index] === byte)
          || new DataView(bytes.buffer).getUint32(26) !== bytes.length - 30) {
          this.refuse('This is not a supported Keynest encrypted file.');return;
        }
        const parts: string[] = [];
        // Chunk lengths are divisible by three, preserving canonical padded Base64 on joining.
        for (let offset = 0; offset < bytes.length; offset += 24_576) parts.push(btoa(String.fromCharCode(...bytes.subarray(offset, offset + 24_576))));
        this.reader = undefined;next(parts.join(''));
      } catch { if (this.current(version)) { if (this.applying()) this.uncertain();else this.refuse('Your browser could not read this file. Choose the file again.'); } }
      finally { bytes?.fill(0);reader.onload = reader.onerror = null; }
    };
    reader.onerror = () => { if (this.current(version)) this.refuse('Your browser could not read this file. Choose the file again.'); };
    try { reader.readAsArrayBuffer(this.file); }
    catch { this.refuse('Your browser could not read this file. Choose the file again.'); }
  }

  private validPhrase(value: string): boolean { return value.length <= 1024 && Array.from(value).length >= 15 && !!value.trim(); }
  private current(version: number): boolean { return !!this.session && version === this.version && this.auth.token() === this.session && !this.destroy.destroyed; }
  private clearAccount(): void { const field = this.account();if (field) field.nativeElement.value = ''; }
  private clearCredentials(): void { this.clearAccount();const field = this.phrase();if (field) field.nativeElement.value = ''; }
  private stop(): void {
    ++this.version;clearTimeout(this.expiry);this.request?.unsubscribe();this.request = undefined;
    const reader = this.reader;this.reader = undefined;
    if (reader) { reader.onload = reader.onerror = null;if (reader.readyState === FileReader.LOADING) reader.abort();if (reader.result instanceof ArrayBuffer) new Uint8Array(reader.result).fill(0); }
    this.busy.set(false);this.applying.set(false);
  }
  private reset(): void {
    this.stop();this.preview.set(null);this.file = undefined;this.filename.set('');this.clearCredentials();
    const input = this.picker();if (input) input.nativeElement.value = '';
  }
  private refuse(message: string): void { this.reset();this.error.set(message); }
  private uncertain(): void { this.reset();this.blocked.set(true);this.error.set(UNCERTAIN); }
  private fail(response: HttpErrorResponse, applying: boolean): void {
    if (response.status === 401) { this.reset();this.auth.clearSession();this.dialog.close();return; }
    const codes: Record<string, number> = { RESTORE_INVALID_REQUEST: 400, RESTORE_INVALID_FILE: 400, RESTORE_REAUTH_FAILED: 403,
      RESTORE_PLAN_INVALID: 409, RESTORE_CHANGED: 409, RESTORE_REQUEST_TOO_LARGE: 413, RESTORE_LIMIT_EXCEEDED: 413, RESTORE_EMPTY: 422, RESTORE_BUSY: 503 };
    const code = response.error?.code;
    if (applying && (typeof code !== 'string' || !Object.hasOwn(codes, code) || codes[code] !== response.status)) { this.uncertain();return; }
    this.refuse(response.status === 403 ? 'Account password verification failed. Choose your file and review again.'
      : response.status === 409 ? 'The review expired, changed or was already used. Choose your file and review again.'
      : response.status === 413 ? 'This file exceeds the restore limits. Use a smaller backup.'
      : response.status === 422 ? 'There are no items in this backup.'
      : response.status === 429 || response.status === 503 ? 'Restore is busy or unavailable. Wait before reviewing again.'
      : 'The file or passphrase could not be verified. Choose your file and review again.');
  }
  @HostListener('document:visibilitychange') onVisibilityChange(): void { if (document.hidden) this.leave(); }
  @HostListener('window:pagehide') onPageHide(): void { this.leave(); }
  private leave(): void { if (this.applying()) this.uncertain();else { this.reset();this.status.set('Choose your file and review again after leaving this tab.'); } }
}
