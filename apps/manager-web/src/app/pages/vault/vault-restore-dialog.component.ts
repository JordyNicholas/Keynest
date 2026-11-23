import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, HostListener, effect, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { Subscription } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';

type Match = { targetId: string; label: string; createdAt: string; updatedAt: string; changedFields: string[] };
type Entry = { sourceIndex: number; label: string; url: string | null; username: string | null; matchKind: string; repeatedOf: number | null; matches: Match[] };
type Decision = { sourceIndex: number; action: 'add' | 'ignore' | 'update'; targetId: string | null };
type Preview = { planId: string; expiresAt: string; itemCount: number; newItemCount: number; updatedItemCount: number; skippedCount: number; entries: Entry[]; receivingPolicyUnchanged: true };
export type RestoreResult = { restoredCount: number; updatedCount: number; skippedCount: number; receivingPolicyUnchanged: true };
const FIELD_LABELS: Record<string, string> = { label: 'Name', url: 'Website', username: 'Username', password: 'Password (hidden)', policyId: 'Historical policy', expiresAt: 'Expiry date', lastRotatedAt: 'Rotation date', notifyBeforeDays: 'Reminder timing' };
// ADR-0012 v1 framing plus the existing 8 MiB native payload bound.
const MAX_FILE_BYTES = 8 * 1024 * 1024 + 79;
const UNCERTAIN = 'The restore may have finished. Close this dialog and refresh your Vault before starting another restore. This request will not be retried automatically.';

@Component({
  selector: 'kn-vault-restore-dialog',
  imports: [DatePipe, MatDialogModule, MatButtonModule, MatExpansionModule, InlineErrorComponent],
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
  private readonly confirmation = viewChild<ElementRef<HTMLInputElement>>('confirmation');
  private file?: File;
  private reader?: FileReader;
  private request?: Subscription;
  private expiry?: ReturnType<typeof setTimeout>;
  private version = 0;
  readonly filename = signal('');
  readonly preview = signal<Preview | null>(null);
  readonly decisions = signal<Decision[]>([]);
  readonly selectionDirty = signal(false);
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
    this.stop();this.preview.set(null);this.decisions.set([]);this.selectionDirty.set(false);this.clearAccount();this.error.set('');this.status.set('');
  }

  review(event: Event): void {
    event.preventDefault();if (this.busy() || this.blocked() || !this.current(this.version)) return;
    if (this.selectionDirty() && !this.selectionValid()) { this.error.set('Choose a changed existing entry for every update, using each target only once.');return; }
    const requested = this.preview() ? this.decisions().map(decision => ({ ...decision })) : undefined;
    this.invalidateReview();
    const backupPassphrase = this.phrase()?.nativeElement.value ?? '';
    if (!this.file || !this.validPhrase(backupPassphrase)) { this.error.set('Choose your encrypted file and enter its passphrase of at least 15 characters.');return; }
    const version = this.version;this.busy.set(true);
    this.read(version, file => {
      this.request = this.http.post<Preview>(`${this.api}/vault/restores/preview`, { file, backupPassphrase, ...(requested ? { decisions: requested } : {}) }, { headers: this.auth.authHeaders() })
        .pipe(takeUntilDestroyed(this.destroy)).subscribe({
          next: preview => {
            if (!this.current(version)) return;
            const remaining = Date.parse(preview?.expiresAt) - Date.now();
            if (!preview || !this.validEntries(preview) || !/^[A-Za-z0-9_-]{43}$/.test(preview.planId) || !Number.isInteger(preview.itemCount)
              || preview.itemCount < 1 || preview.itemCount > 500 || !Number.isInteger(preview.newItemCount) || !Number.isInteger(preview.skippedCount)
              || !Number.isInteger(preview.updatedItemCount) || preview.updatedItemCount < 0 || preview.newItemCount < 0 || preview.skippedCount < 0 || preview.newItemCount + preview.updatedItemCount + preview.skippedCount !== preview.itemCount || preview.receivingPolicyUnchanged !== true
              || !Number.isFinite(remaining) || remaining <= 0 || remaining > 310_000) { this.refuse('Restore review is unavailable. Choose your file and review again.');return; }
            const entries = preview.entries.map(entry => ({ sourceIndex: entry.sourceIndex, label: entry.label, url: entry.url, username: entry.username, matchKind: entry.matchKind, repeatedOf: entry.repeatedOf,
              matches: entry.matches.map(match => ({ targetId: match.targetId, label: match.label, createdAt: match.createdAt, updatedAt: match.updatedAt, changedFields: [...match.changedFields] })) }));
            this.busy.set(false);this.preview.set({ planId: preview.planId, expiresAt: preview.expiresAt, itemCount: preview.itemCount, newItemCount: preview.newItemCount, updatedItemCount: preview.updatedItemCount, skippedCount: preview.skippedCount, entries, receivingPolicyUnchanged: true });
            this.decisions.set(requested ?? entries.map(entry => ({ sourceIndex: entry.sourceIndex, action: entry.matchKind === 'new' ? 'add' : 'ignore', targetId: null })));
            if (!this.selectionValid() || this.decisions().filter(choice => choice.action === 'add').length !== preview.newItemCount
              || this.decisions().filter(choice => choice.action === 'update').length !== preview.updatedItemCount || this.decisions().filter(choice => choice.action === 'ignore').length !== preview.skippedCount) { this.refuse('The matching entries changed. Choose your file and review again.');return; }
            this.expiry = setTimeout(() => { this.reset();this.status.set('This review expired. Choose your file and review again.'); }, Math.min(300_000, remaining));
          },
          error: response => { if (this.current(version)) this.fail(response, false); },
        });
    });
  }

  apply(event: Event): void {
    event.preventDefault();const preview = this.preview();
    if (this.busy() || this.blocked() || !preview || this.selectionDirty() || !preview.planId || preview.newItemCount + preview.updatedItemCount === 0 || !this.current(this.version)) return;
    if (Date.parse(preview.expiresAt) <= Date.now()) { this.refuse('This review expired. Choose your file and review again.');return; }
    const accountPassword = this.account()?.nativeElement.value ?? '', backupPassphrase = this.phrase()?.nativeElement.value ?? '', confirmUpdates = this.confirmation()?.nativeElement.checked === true;
    this.clearCredentials();this.error.set('');this.status.set('');
    if (!accountPassword || accountPassword.length > 32768 || !this.validPhrase(backupPassphrase) || (preview.updatedItemCount > 0 && !confirmUpdates)) {
      this.refuse('Enter the required credentials and confirm any replacements, then review the file again.');return;
    }
    clearTimeout(this.expiry);const version = this.version;this.busy.set(true);
    this.read(version, file => {
      if (Date.parse(preview.expiresAt) <= Date.now()) { this.refuse('This review expired. Choose your file and review again.');return; }
      this.applying.set(true);
      this.request = this.http.post<RestoreResult>(`${this.api}/vault/restores/apply`, { file, backupPassphrase, accountPassword, planId: preview.planId, ...(confirmUpdates ? { confirmUpdates: true } : {}) }, { headers: this.auth.authHeaders() })
        .pipe(takeUntilDestroyed(this.destroy)).subscribe({
          next: result => {
            if (!this.current(version)) return;
            if (!result || result.restoredCount !== preview.newItemCount || result.updatedCount !== preview.updatedItemCount || result.skippedCount !== preview.skippedCount || result.receivingPolicyUnchanged !== true) { this.uncertain();return; }
            this.reset();this.dialog.close({ restoredCount: result.restoredCount, updatedCount: result.updatedCount, skippedCount: result.skippedCount, receivingPolicyUnchanged: true });
          },
          error: response => { if (this.current(version)) this.fail(response, true); },
        });
    });
  }

  chooseAction(index: number, action: string): void {
    if (this.applying()) { this.uncertain();return; }
    const entry = this.preview()?.entries[index];if (this.busy() || !entry || !['add', 'ignore', 'update'].includes(action)) return;
    const targets = entry.matches.filter(match => match.changedFields.length > 0);
    if (action === 'update' && !targets.length) return;
    this.decisions.update(choices => choices.map(choice => choice.sourceIndex === index ? { sourceIndex: index, action: action as Decision['action'], targetId: action === 'update' && targets.length === 1 ? targets[0].targetId : null } : choice));this.dirtySelection();
  }
  chooseTarget(index: number, targetId: string): void {
    if (this.applying()) { this.uncertain();return; }
    const entry = this.preview()?.entries[index];if (this.busy() || !entry?.matches.some(match => match.targetId === targetId && match.changedFields.length > 0)) return;
    this.decisions.update(choices => choices.map(choice => choice.sourceIndex === index ? { sourceIndex: index, action: 'update', targetId } : choice));this.dirtySelection();
  }
  selectionValid(): boolean {
    const preview = this.preview();if (!preview || this.decisions().length !== preview.entries.length) return false;
    const targets = new Set<string>();
    return this.decisions().every(choice => {
      if (choice.action !== 'update') return choice.targetId === null;
      if (!choice.targetId || targets.has(choice.targetId) || !preview.entries[choice.sourceIndex]?.matches.some(match => match.targetId === choice.targetId && match.changedFields.length > 0)) return false;
      targets.add(choice.targetId);return true;
    });
  }
  selectedMatch(index: number): Match | undefined { return this.preview()?.entries[index]?.matches.find(match => match.targetId === this.decisions()[index]?.targetId); }
  canUpdate(entry: Entry): boolean { return entry.matches.some(match => match.changedFields.length > 0); }
  fields(match: Match): string { return match.changedFields.map(field => FIELD_LABELS[field]).join(', '); }
  kind(entry: Entry): string { return entry.matchKind === 'identical' ? 'Already up to date' : entry.matchKind === 'conflict' ? 'Changed match' : entry.matchKind === 'repeated' ? 'Repeated in this file' : 'New entry'; }
  allUpToDate(): boolean { return this.preview()?.entries.every(entry => entry.matchKind === 'identical') === true; }
  private dirtySelection(): void {
    ++this.version;this.clearAccount();this.selectionDirty.set(true);this.error.set('');this.status.set('');
    this.preview.update(preview => preview ? { ...preview, planId: '' } : null);
  }
  private validEntries(preview: Preview): boolean {
    if (!Array.isArray(preview.entries) || preview.entries.length !== preview.itemCount) return false;let matches = 0;
    return preview.entries.every((entry, index) => entry && entry.sourceIndex === index && typeof entry.label === 'string' && entry.label.length <= 200
      && (entry.url === null || typeof entry.url === 'string' && entry.url.length <= 2000) && (entry.username === null || typeof entry.username === 'string' && entry.username.length <= 320)
      && ['new', 'identical', 'conflict', 'repeated'].includes(entry.matchKind) && (entry.repeatedOf === null || Number.isInteger(entry.repeatedOf) && entry.repeatedOf >= 0 && entry.repeatedOf < index)
      && Array.isArray(entry.matches) && (matches += entry.matches.length) <= 5000 && entry.matches.every(match => match && /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(match.targetId)
        && typeof match.label === 'string' && match.label.length <= 200 && Number.isFinite(Date.parse(match.createdAt)) && Number.isFinite(Date.parse(match.updatedAt))
        && Array.isArray(match.changedFields) && match.changedFields.length <= 8 && match.changedFields.every(field => Object.hasOwn(FIELD_LABELS, field))));
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
  private clearAccount(): void { const field = this.account();if (field) field.nativeElement.value = '';const confirmation = this.confirmation();if (confirmation) confirmation.nativeElement.checked = false; }
  private clearCredentials(): void { this.clearAccount();const field = this.phrase();if (field) field.nativeElement.value = ''; }
  private stop(): void {
    ++this.version;clearTimeout(this.expiry);this.request?.unsubscribe();this.request = undefined;
    const reader = this.reader;this.reader = undefined;
    if (reader) { reader.onload = reader.onerror = null;if (reader.readyState === FileReader.LOADING) reader.abort();if (reader.result instanceof ArrayBuffer) new Uint8Array(reader.result).fill(0); }
    this.busy.set(false);this.applying.set(false);
  }
  private reset(): void {
    this.stop();this.preview.set(null);this.decisions.set([]);this.selectionDirty.set(false);this.file = undefined;this.filename.set('');this.clearCredentials();
    const input = this.picker();if (input) input.nativeElement.value = '';
  }
  private refuse(message: string): void { this.reset();this.error.set(message); }
  private uncertain(): void { this.reset();this.blocked.set(true);this.error.set(UNCERTAIN); }
  private fail(response: HttpErrorResponse, applying: boolean): void {
    if (response.status === 401) { this.reset();this.auth.clearSession();this.dialog.close();return; }
    const codes: Record<string, number> = { RESTORE_INVALID_REQUEST: 400, RESTORE_SELECTION_INVALID: 400, RESTORE_INVALID_FILE: 400, RESTORE_REAUTH_FAILED: 403,
      RESTORE_PLAN_INVALID: 409, RESTORE_CHANGED: 409, RESTORE_REQUEST_TOO_LARGE: 413, RESTORE_LIMIT_EXCEEDED: 413, RESTORE_COMPARISON_LIMIT: 413, RESTORE_EMPTY: 422, RESTORE_BUSY: 503 };
    const code = response.error?.code;
    if (applying && (typeof code !== 'string' || !Object.hasOwn(codes, code) || codes[code] !== response.status)) { this.uncertain();return; }
    this.refuse(code === 'RESTORE_SELECTION_INVALID' ? 'The selected actions or confirmation are no longer valid. Choose your file and review again.'
      : response.status === 403 ? 'Account password verification failed. Choose your file and review again.'
      : response.status === 409 ? 'The file or receiving Vault changed, or the review expired or was used. Choose your file and review again.'
      : code === 'RESTORE_COMPARISON_LIMIT' ? 'Too many similar entries to check safely. Restore a smaller selection.'
      : response.status === 413 ? 'This file exceeds the restore limits. Use a smaller backup.'
      : response.status === 422 ? 'There are no items in this backup.'
      : response.status === 429 || response.status === 503 ? 'Restore is busy or unavailable. Wait before reviewing again.'
      : 'The file or passphrase could not be verified. Choose your file and review again.');
  }
  @HostListener('document:visibilitychange') onVisibilityChange(): void { if (document.hidden) this.leave(); }
  @HostListener('window:pagehide') onPageHide(): void { this.leave(); }
  private leave(): void { if (this.applying()) this.uncertain();else { this.reset();this.status.set('Choose your file and review again after leaving this tab.'); } }
}
