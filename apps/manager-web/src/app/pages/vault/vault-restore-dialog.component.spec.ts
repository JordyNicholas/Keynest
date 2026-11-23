import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { VaultRestoreDialogComponent } from './vault-restore-dialog.component';

const previewUrl = '/api/v1/vault/restores/preview', applyUrl = '/api/v1/vault/restores/apply';
const phrase = 'Synthetic separate backup passphrase', account = 'Synthetic current account credential';
function frame(length = 128): Uint8Array {
  const bytes = new Uint8Array(length);bytes.set([75, 69, 89, 78, 69, 83, 84, 66, 1, 1]);new DataView(bytes.buffer).setUint32(26, length - 30);return bytes;
}
function ticket() { return { planId: 'a'.repeat(43), expiresAt: new Date(Date.now() + 300_000).toISOString(), itemCount: 2, receivingPolicyUnchanged: true }; }

describe('Vault encrypted restore dialog', () => {
  let fixture: ComponentFixture<VaultRestoreDialogComponent>, component: VaultRestoreDialogComponent, http: HttpTestingController;
  let closing: Subject<void>, readers: FileReader[];
  let nativeRead: FileReader['readAsArrayBuffer'];
  beforeEach(() => {
    sessionStorage.clear();closing = new Subject<void>();readers = [];
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: API_BASE_URL, useValue: '/api/v1' },
      { provide: MatDialogRef, useValue: { beforeClosed: () => closing, close: jasmine.createSpy('close') } },
    ] });
    TestBed.inject(AuthService).setSession('synthetic-session', 'sample@example.test');
    nativeRead = FileReader.prototype.readAsArrayBuffer;
    spyOn(FileReader.prototype, 'readAsArrayBuffer').and.callFake(function(this: FileReader) {
      Object.defineProperty(this, 'readyState', { configurable: true, value: FileReader.LOADING });readers.push(this);
    });
    spyOn(FileReader.prototype, 'abort');
    fixture = TestBed.createComponent(VaultRestoreDialogComponent);component = fixture.componentInstance;http = TestBed.inject(HttpTestingController);fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy();http.verify();TestBed.inject(AuthService).clearSession(); });
  function field(id: string): HTMLInputElement { return fixture.nativeElement.querySelector('#restore-' + id); }
  function choose(file = new File([frame() as BlobPart], 'synthetic.keynest')) {
    const transfer = new DataTransfer();transfer.items.add(file);field('file').files = transfer.files;
    field('file').dispatchEvent(new Event('change'));fixture.detectChanges();field('passphrase').value = phrase;
  }
  function finishRead(bytes = frame()) {
    const reader = readers.at(-1)!;Object.defineProperty(reader, 'readyState', { configurable: true, value: FileReader.DONE });
    Object.defineProperty(reader, 'result', { configurable: true, value: bytes.buffer });reader.onload?.(new ProgressEvent('load') as ProgressEvent<FileReader>);return bytes;
  }
  function review() {
    choose();component.review(new Event('submit'));finishRead();const request = http.expectOne(previewUrl);request.flush(ticket());fixture.detectChanges();return request;
  }
  function apply() {
    field('account-password').value = account;component.apply(new Event('submit'));finishRead();return http.expectOne(applyUrl);
  }

  it('checks framing before upload, previews without account credentials and clears the owned read buffer', () => {
    const bytes = frame(24_576 + 128), encoded = btoa(String.fromCharCode(...bytes));
    choose(new File([bytes as BlobPart], 'synthetic.keynest'));component.review(new Event('submit'));http.expectNone(() => true);
    finishRead(bytes);expect(bytes.every(byte => byte === 0)).toBeTrue();
    const request = http.expectOne(previewUrl);expect(request.request.headers.get('Authorization')).toBe('Bearer synthetic-session');
    expect(request.request.body).toEqual({ file: encoded, backupPassphrase: phrase });request.flush(ticket());fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ready to add 2 item(s)');expect(fixture.nativeElement.textContent).toContain('No items have been written');
    expect(field('passphrase').value).toBe(phrase);http.expectNone(applyUrl);
  });

  it('reads a real browser File after clearing the picker and uploads its exact canonical bytes', async () => {
    let finished!: Promise<void>, reader!: FileReader;
    (FileReader.prototype.readAsArrayBuffer as jasmine.Spy).and.callFake(function(this: FileReader, file: Blob) {
      reader = this;finished = new Promise(resolve => this.addEventListener('loadend', () => resolve(), { once: true }));nativeRead.call(this, file);
    });
    const bytes = frame(24_576 + 128);choose(new File([bytes as BlobPart], 'synthetic.keynest'));expect(field('file').value).toBe('');
    component.review(new Event('submit'));await finished;
    const request = http.expectOne(previewUrl);expect(request.request.body.file).toBe(btoa(String.fromCharCode(...bytes)));
    expect(new Uint8Array(reader.result as ArrayBuffer).every(byte => byte === 0)).toBeTrue();request.flush(ticket());
  });

  it('applies once with fresh credentials and the exact reviewed file, clears inputs immediately and never persists payloads', () => {
    review();const storage = [spyOn(localStorage, 'setItem'), spyOn(sessionStorage, 'setItem')];
    field('account-password').value = account;component.apply(new Event('submit'));
    expect(field('account-password').value).toBe('');expect(field('passphrase').value).toBe('');
    component.apply(new Event('submit'));expect(readers.length).toBe(2);finishRead();
    const request = http.expectOne(applyUrl);
    expect(request.request.headers.get('Authorization')).toBe('Bearer synthetic-session');
    expect(request.request.body).toEqual({ file: btoa(String.fromCharCode(...frame())), backupPassphrase: phrase, accountPassword: account, planId: 'a'.repeat(43) });
    request.flush({ restoredCount: 2, receivingPolicyUnchanged: true });
    expect(TestBed.inject(MatDialogRef).close).toHaveBeenCalledWith({ restoredCount: 2, receivingPolicyUnchanged: true });
    expect(component.preview()).toBeNull();expect(component.filename()).toBe('');expect(field('file').value).toBe('');
    component.apply(new Event('submit'));http.expectNone(applyUrl);storage.forEach(spy => expect(spy).not.toHaveBeenCalled());
  });

  it('rejects wrong framing and oversized files locally without uploading them', () => {
    choose();component.review(new Event('submit'));const bytes = frame();bytes[0] = 0;finishRead(bytes);
    expect(component.error()).toContain('not a supported Keynest');http.expectNone(() => true);expect(component.filename()).toBe('');expect(field('passphrase').value).toBe('');
    const large = new File(['synthetic'], 'too-large.keynest');Object.defineProperty(large, 'size', { value: 8 * 1024 * 1024 + 80 });
    choose(large);component.review(new Event('submit'));expect(readers.length).toBe(1);http.expectNone(() => true);
  });

  it('invalidates edits and clears all file/credential/plan state on expiry', fakeAsync(() => {
    review();field('account-password').value = account;field('passphrase').value = 'Different synthetic backup passphrase';field('passphrase').dispatchEvent(new Event('input'));
    expect(component.preview()).toBeNull();expect(field('account-password').value).toBe('');component.apply(new Event('submit'));http.expectNone(applyUrl);
    review();field('account-password').value = account;tick(300_000);
    expect(component.preview()).toBeNull();expect(component.filename()).toBe('');expect(field('passphrase').value).toBe('');expect(field('account-password').value).toBe('');
    expect(component.status()).toContain('expired');component.apply(new Event('submit'));http.expectNone(applyUrl);
  }));

  it('aborts pending reads and cancels previews with immediate cleanup before closing', () => {
    choose();component.review(new Event('submit'));closing.next();expect(FileReader.prototype.abort).toHaveBeenCalled();expect(readers.at(-1)!.onload).toBeNull();
    expect(field('passphrase').value).toBe('');expect(component.filename()).toBe('');http.expectNone(() => true);
    choose();component.review(new Event('submit'));finishRead();const request = http.expectOne(previewUrl);closing.next();
    expect(request.cancelled).toBeTrue();expect(component.preview()).toBeNull();expect(field('passphrase').value).toBe('');
  });

  it('requires explicit new review after a fixed reauthentication refusal and never retries apply', () => {
    review();apply().flush({ code: 'RESTORE_REAUTH_FAILED' }, { status: 403, statusText: 'Forbidden' });
    expect(component.blocked()).toBeFalse();expect(component.error()).toContain('Account password verification failed');expect(component.preview()).toBeNull();
    component.apply(new Event('submit'));http.expectNone(applyUrl);expect(component.filename()).toBe('');
  });

  it('does not interrupt an already-sent apply when the idle review deadline passes', fakeAsync(() => {
    review();const request = apply();tick(300_001);expect(request.cancelled).toBeFalse();expect(component.applying()).toBeTrue();
    request.flush({ restoredCount: 2, receivingPolicyUnchanged: true });expect(TestBed.inject(MatDialogRef).close).toHaveBeenCalled();
  }));

  it('treats a generic transaction failure as uncertain rather than assuming the commit was rolled back', () => {
    review();apply().flush({ code: 'RESTORE_FAILED' }, { status: 503, statusText: 'Unavailable' });
    expect(component.blocked()).toBeTrue();component.review(new Event('submit'));http.expectNone(previewUrl);
  });

  it('does not report success for an unrecognized successful apply response', () => {
    review();apply().flush({ restoredCount: 1, receivingPolicyUnchanged: true });
    expect(component.blocked()).toBeTrue();expect(TestBed.inject(MatDialogRef).close).not.toHaveBeenCalled();
  });

  it('keeps unknown apply outcomes blocked even after the transient error is dismissed', () => {
    review();apply().error(new ProgressEvent('error'));
    expect(component.blocked()).toBeTrue();expect(component.preview()).toBeNull();expect(component.filename()).toBe('');
    component.error.set('');fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('may have finished');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();component.review(new Event('submit'));component.apply(new Event('submit'));http.expectNone(() => true);
  });

  it('clears on page hide, treating an already-sent apply as uncertain rather than a rollback', () => {
    review();const request = apply();window.dispatchEvent(new Event('pagehide'));
    expect(request.cancelled).toBeTrue();expect(component.blocked()).toBeTrue();expect(component.filename()).toBe('');expect(field('passphrase').value).toBe('');
  });

  it('treats a late input event that interrupts a sent apply as uncertain', () => {
    review();const request = apply();field('passphrase').dispatchEvent(new Event('input'));
    expect(request.cancelled).toBeTrue();expect(component.blocked()).toBeTrue();expect(component.preview()).toBeNull();expect(component.filename()).toBe('');
  });

  it('discards late responses after a session change and clears expired authentication', () => {
    review();const request = apply();TestBed.inject(AuthService).setSession('other-session', 'other@example.test');
    request.flush({ restoredCount: 2, receivingPolicyUnchanged: true });expect(TestBed.inject(MatDialogRef).close).not.toHaveBeenCalled();
    fixture.detectChanges();expect(TestBed.inject(MatDialogRef).close).toHaveBeenCalledWith();expect(component.filename()).toBe('');
  });

  it('clears the session and credentials on an unauthenticated response', () => {
    choose();component.review(new Event('submit'));finishRead();http.expectOne(previewUrl).flush({ code: 'RESTORE_SESSION_REQUIRED' }, { status: 401, statusText: 'Unauthorized' });
    expect(TestBed.inject(AuthService).signedIn()).toBeFalse();expect(TestBed.inject(MatDialogRef).close).toHaveBeenCalled();expect(field('passphrase').value).toBe('');
  });

  it('refuses malformed or expired preview responses without enabling apply', () => {
    choose();component.review(new Event('submit'));finishRead();http.expectOne(previewUrl).flush({ ...ticket(), expiresAt: new Date(Date.now() - 1).toISOString() });
    expect(component.preview()).toBeNull();expect(component.filename()).toBe('');component.apply(new Event('submit'));http.expectNone(applyUrl);
  });
});
