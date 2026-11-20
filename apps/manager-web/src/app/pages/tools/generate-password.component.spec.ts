import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { GeneratePasswordPageComponent } from './generate-password.component';

describe('Account policy generation', () => {
  it('uses the authenticated selected rules without sending the generated candidate', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      { provide: API_BASE_URL, useValue: '/api/v1' }] });
    const auth = TestBed.inject(AuthService);
    auth.setSession('test-session', 'synthetic@example.test');
    const fixture = TestBed.createComponent(GeneratePasswordPageComponent);
    const http = TestBed.inject(HttpTestingController);
    const read = http.expectOne('/api/v1/tools/policy');
    expect(read.request.headers.get('Authorization')).toBe('Bearer test-session');
    read.flush({ id: 'keynest.account.synthetic.v1', rules: {
      minLength: 8, maxLength: 8, minCategories: 1, requireLower: false, requireUpper: false,
      requireDigit: false, requireSymbol: false, excludeAmbiguous: false, forbidWhitespace: true,
      forbidUsernameSubstring: false, bannedSubstrings: [], bannedRegexes: [],
    } });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    expect(fixture.componentInstance.generated().length).toBe(8);
    http.verify();
    fixture.destroy();
    auth.clearSession();
  });
});
