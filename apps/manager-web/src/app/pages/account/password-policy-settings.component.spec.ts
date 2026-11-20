import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { PasswordPolicySettingsComponent } from './password-policy-settings.component';

describe('Account password policy settings', () => {
  it('loads and saves authenticated rules and can restore the default selection', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: API_BASE_URL, useValue: '/api/v1' }] });
    const auth = TestBed.inject(AuthService);
    auth.setSession('test-session', 'synthetic@example.test');
    const fixture = TestBed.createComponent(PasswordPolicySettingsComponent);
    const component = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);
    const rules = { minLength: 16, maxLength: 128, minCategories: 3, requireLower: true,
      requireUpper: true, requireDigit: true, requireSymbol: true, excludeAmbiguous: true,
      forbidWhitespace: true, forbidUsernameSubstring: true, bannedSubstrings: ['password'],
      bannedRegexes: ['(.)\\1{3,}'] };
    const read = http.expectOne('/api/v1/account/password-policy');
    expect(read.request.headers.get('Authorization')).toBe('Bearer test-session');
    read.flush({ id: 'keynest.default.v1', rules });
    component.mode = 'custom';
    component.rules!.minLength = 8;
    component.rules!.excludeAmbiguous = false;
    component.blocked = 'word, another';
    component.save();
    const save = http.expectOne('/api/v1/account/password-policy');
    expect(save.request.method).toBe('PUT');
    expect(save.request.body.mode).toBe('custom');
    expect(save.request.body.rules.bannedSubstrings).toEqual(['word', 'another']);
    expect(save.request.body.rules.minLength).toBe(8);
    expect(save.request.body.rules.excludeAmbiguous).toBeFalse();
    save.flush({ id: 'keynest.account.synthetic.v1', rules: save.request.body.rules });
    expect(component.mode).toBe('custom');
    component.mode = 'default';
    component.save();
    const reset = http.expectOne('/api/v1/account/password-policy');
    expect(reset.request.body.mode).toBe('default');
    reset.flush({ id: 'keynest.default.v1', rules });
    expect(component.rules!.excludeAmbiguous).toBeTrue();
    http.verify();
    fixture.destroy();
    auth.clearSession();
  });
});
