import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { VaultPageComponent } from './vault.component';

describe('VaultPageComponent reveal lifecycle', () => {
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
    http.expectOne('/api/v1/vault/items').flush([]);
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
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
