import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { VaultPageComponent } from './vault.component';

describe('VaultPageComponent', () => {
  let fixture: ComponentFixture<VaultPageComponent>;
  let component: VaultPageComponent;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    sessionStorage.setItem('keynest.token', 'session-token');
    TestBed.configureTestingModule({
      imports: [VaultPageComponent],
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
    fixture = TestBed.createComponent(VaultPageComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    const request = http.expectOne('/api/v1/vault/items');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe('Bearer session-token');
    request.flush([]);
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    sessionStorage.clear();
    localStorage.clear();
  });

  it('renders metadata returned by an authenticated vault list request', () => {
    component.refreshVault();
    const request = http.expectOne('/api/v1/vault/items');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe('Bearer session-token');
    request.flush([{
      id: 'item-1', label: 'Example account', username: 'demo-user',
      url: 'https://example.com', policyId: 'keynest.default.v1',
    }]);
    fixture.detectChanges();

    const row: HTMLElement = fixture.nativeElement.querySelector('.vault-list li');
    expect(row.textContent).toContain('Example account');
    expect(row.textContent).toContain('demo-user');
    expect(row.textContent).toContain('https://example.com');
    expect(row.querySelector('output.secret')).toBeNull();
  });

  it('renders an empty state when the vault list is empty', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.vault-list .empty').textContent)
      .toContain('No vault items yet.');
  });

  it('shows the sign-in error after a denied list request and clears it on recovery', () => {
    component.refreshVault();
    http.expectOne('/api/v1/vault/items').flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent)
      .toContain('Vault list failed — sign in again');

    component.refreshVault();
    http.expectOne('/api/v1/vault/items').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('uses POST and automatically conceals the secret after 15 seconds', fakeAsync(() => {
    component.reveal('item-1');

    const request = http.expectOne('/api/v1/vault/items/item-1/reveal');
    expect(request.request.method).toBe('POST');
    request.flush({ item: { id: 'item-1', label: 'Example', policyId: 'keynest.default.v1' }, password: 'secret' });

    expect(component.revealed()['item-1']).toBe('secret');
    tick(14_999);
    expect(component.revealed()['item-1']).toBe('secret');
    tick(1);
    expect(component.revealed()['item-1']).toBeUndefined();
  }));

  it('replaces the timer and supports immediate manual concealment', fakeAsync(() => {
    component.reveal('item-1');
    http.expectOne('/api/v1/vault/items/item-1/reveal').flush({
      item: { id: 'item-1', label: 'Example', policyId: 'keynest.default.v1' },
      password: 'first',
    });
    tick(10_000);

    component.reveal('item-1');
    http.expectOne('/api/v1/vault/items/item-1/reveal').flush({
      item: { id: 'item-1', label: 'Example', policyId: 'keynest.default.v1' },
      password: 'second',
    });
    tick(5_000);
    expect(component.revealed()['item-1']).toBe('second');

    component.conceal('item-1');
    expect(component.revealed()['item-1']).toBeUndefined();
    tick(15_000);
  }));
});
