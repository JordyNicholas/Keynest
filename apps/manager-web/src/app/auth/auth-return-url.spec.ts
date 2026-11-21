import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { authReturnUrl } from './auth-return-url';

describe('Authentication return destination', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([])] }));
  it('preserves local workspace deep links and harmless filters', () => {
    const router = TestBed.inject(Router);
    for (const path of ['/vault/new', '/vault/rotate/sample', '/vault?expiry=soon', '/overview']) expect(authReturnUrl(router, path)).toBe(path);
  });
  it('rejects remote, malformed, unrelated and secondary-outlet destinations', () => {
    const router = TestBed.inject(Router);
    for (const path of [null, 'https://example.test', '//example.test', '/\\example.test', '/account', '/tools', '/vault(aux:example)', '/vault/(aux:example)', '/%2fexample.test', '/vault\n', '/vault/' + 'a'.repeat(2048)]) expect(authReturnUrl(router, path)).toBe('/vault');
  });
});
