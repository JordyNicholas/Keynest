import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { VaultExportDialogComponent } from './vault-export-dialog.component';

const id = '00000000-0000-0000-0000-000000000001';
const preview = { destination: 'keynest', encrypted: true, version: 1, itemCount: 1, snapshot: 'a'.repeat(64), asOf: '2026-11-22T00:00:00Z' };

describe('Vault encrypted export dialog', () => {
  let closing: Subject<void>;
  beforeEach(() => {
    sessionStorage.clear();closing = new Subject<void>();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: API_BASE_URL, useValue: '/api/v1' }, { provide: MAT_DIALOG_DATA, useValue: { ids: [id] } },
      { provide: MatDialogRef, useValue: { beforeClosed: () => closing, close: jasmine.createSpy('close') } },
    ] });
    TestBed.inject(AuthService).setSession('synthetic-session', 'sample@example.test');
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify();TestBed.inject(AuthService).clearSession(); });

  function open() {
    const fixture = TestBed.createComponent(VaultExportDialogComponent);fixture.detectChanges();
    const component = fixture.componentInstance, http = TestBed.inject(HttpTestingController);
    const review = () => { component.review();const request = http.expectOne('/api/v1/vault/exports/preview');request.flush(preview);fixture.detectChanges();return request; };
    const fill = () => {
      const host: HTMLElement = fixture.nativeElement;
      const account = host.querySelector<HTMLInputElement>('#export-account-password')!;
      const phrase = host.querySelector<HTMLInputElement>('#export-passphrase')!;
      const confirm = host.querySelector<HTMLInputElement>('#export-passphrase-confirmation')!;
      account.value = 'Synthetic current credential';phrase.value = confirm.value = 'Synthetic separate export phrase';
      return { account, phrase, confirm };
    };
    return { fixture, component, http, review, fill };
  }

  it('previews exact selected identities without sending credentials or revealing secrets', () => {
    const { fixture, component, http, review } = open();http.expectNone(() => true);
    const request = review();expect(request.request.headers.get('Authorization')).toBe('Bearer synthetic-session');
    expect(request.request.body).toEqual({ destination: 'keynest', scope: 'selected', itemIds: [id] });
    expect(fixture.nativeElement.textContent).toContain('Application restore is still being built');
    expect(fixture.nativeElement.querySelector('#export-account-password')).not.toBeNull();
    component.changeScope('all');expect(component.preview()).toBeNull();
    component.review();const all = http.expectOne('/api/v1/vault/exports/preview');
    expect(all.request.body).toEqual({ destination: 'keynest', scope: 'all' });all.flush(preview);
  });

  it('clears credentials immediately and downloads only encrypted bytes under a generic filename', fakeAsync(() => {
    const { fixture, component, http, review, fill } = open();review();const fields = fill();
    const storage = [spyOn(localStorage, 'setItem'), spyOn(sessionStorage, 'setItem')];
    const create = spyOn(URL, 'createObjectURL').and.returnValue('blob:synthetic-export');
    const revoke = spyOn(URL, 'revokeObjectURL');
    const click = spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function(this: HTMLAnchorElement) {
      expect(this.download).toBe('keynest-vault.keynest');expect(this.href).toBe('blob:synthetic-export');
    });
    component.download(new Event('submit'));
    for (const field of Object.values(fields)) expect(field.value).toBe('');
    const request = http.expectOne('/api/v1/vault/exports/download');
    expect(request.request.responseType).toBe('blob');expect(request.request.headers.get('Authorization')).toBe('Bearer synthetic-session');
    expect(request.request.body).toEqual({ destination: 'keynest', scope: 'selected', itemIds: [id], snapshot: preview.snapshot,
      accountPassword: 'Synthetic current credential', backupPassphrase: 'Synthetic separate export phrase' });
    request.flush(new Blob(['synthetic encrypted bytes'], { type: 'application/octet-stream' }));
    expect(create).toHaveBeenCalled();expect(click).toHaveBeenCalled();expect(component.preview()).toBeNull();
    expect(component.status()).toContain('download started');tick(1000);expect(revoke).toHaveBeenCalledWith('blob:synthetic-export');
    storage.forEach(spy => expect(spy).not.toHaveBeenCalled());fixture.destroy();
  }));

  it('rejects mismatched passphrases without a download request', () => {
    const { component, http, review, fill } = open();review();const fields = fill();fields.confirm.value = 'Different synthetic phrase';
    component.download(new Event('submit'));http.expectNone('/api/v1/vault/exports/download');
    expect(component.error()).toContain('matching export passphrases');
    for (const field of Object.values(fields)) expect(field.value).toBe('');
  });

  it('invalidates a changed preview and requires explicit review before another download', () => {
    const { component, http, review, fill } = open();review();fill();component.download(new Event('submit'));
    http.expectOne('/api/v1/vault/exports/download').flush(new Blob(), { status: 409, statusText: 'Changed' });
    expect(component.preview()).toBeNull();expect(component.error()).toContain('changed');
    component.download(new Event('submit'));http.expectNone('/api/v1/vault/exports/download');
  });

  it('cancels download work on closing and clears credential inputs before the closing animation', () => {
    const { component, http, review, fill } = open();review();const fields = fill();component.download(new Event('submit'));
    const request = http.expectOne('/api/v1/vault/exports/download');closing.next();
    expect(request.cancelled).toBeTrue();expect(component.preview()).toBeNull();
    for (const field of Object.values(fields)) expect(field.value).toBe('');
  });

  it('cancels pending previews and closes when the account session changes', () => {
    const { fixture, component, http } = open();component.review();const request = http.expectOne('/api/v1/vault/exports/preview');
    TestBed.inject(AuthService).setSession('different-session', 'other@example.test');fixture.detectChanges();
    expect(request.cancelled).toBeTrue();expect(TestBed.inject(MatDialogRef).close).toHaveBeenCalled();
  });
});
